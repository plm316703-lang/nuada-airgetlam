package io.github.plm316703.backend.user;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * 계정 발급·임시 비밀번호 재발급·비밀번호 변경 (11/20 R2).
 *
 * 현업 방식: 스스로 가입하지 않고 관리자가 발급한다.
 * 임시 비밀번호는 서버가 만들어 **이 메서드의 반환값으로 한 번만** 내보내고, DB에는 BCrypt 해시만 남긴다.
 */
@Slf4j
@Service
public class AccountService {

    /**
     * 관리자가 만들 수 있는 역할 (Q-R2a 결정).
     * 보호자는 R4 "초대"에서 환자와 함께 연결하고, 관리자 계정은 권한이 커서 화면에서 늘리지 않는다.
     */
    public static final Set<Role> ISSUABLE_ROLES = Set.of(Role.OPERATOR, Role.RESEARCHER, Role.PRINCIPAL_INVESTIGATOR);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordRules rules;
    private final Duration tempLifetime;

    public AccountService(UserRepository userRepository, PasswordEncoder passwordEncoder, PasswordRules rules,
                          @Value("${account.temp-password-hours}") long tempHours) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.rules = rules;
        this.tempLifetime = Duration.ofHours(tempHours);
    }

    /** 발급 결과: 계정 + 화면에 한 번만 보여 줄 임시 비밀번호 */
    public record Issued(User user, String tempPassword, LocalDateTime expiresAt) {}

    @Transactional
    public Issued create(String email, String name, Role role) {
        if (!ISSUABLE_ROLES.contains(role)) {
            throw new IllegalArgumentException("관리자는 관제 담당자·연구자·연구책임자 계정만 만들 수 있습니다.");
        }
        String normalized = email.trim().toLowerCase();
        if (userRepository.findByEmail(normalized).isPresent()) {
            throw new IllegalArgumentException("이미 있는 이메일입니다.");
        }
        String temp = rules.generateTemp();
        LocalDateTime expiresAt = LocalDateTime.now().plus(tempLifetime);
        String hash = passwordEncoder.encode(temp);
        User user = new User(normalized, hash, name.trim(), role);
        user.issueTempPassword(hash, expiresAt);
        userRepository.save(user);
        // 임시 비밀번호 자체는 로그에 남기지 않는다 (로그 파일을 보는 사람도 알 수 없게)
        log.info("계정 발급 id={} role={}", user.getId(), role);
        return new Issued(user, temp, expiresAt);
    }

    /**
     * 임시 비밀번호 재발급 ("비밀번호를 잊었어요").
     * 관리자 계정은 대상에서 뺀다: 관리자가 다른 관리자(또는 자기)의 비밀번호를 바꿀 수 있으면 권한을 가로챌 수 있음.
     */
    @Transactional
    public Issued reissue(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("계정을 찾을 수 없습니다."));
        if (user.getRole() == Role.SYSTEM_ADMIN) {
            throw new IllegalArgumentException("관리자 계정의 비밀번호는 이 화면에서 재발급할 수 없습니다.");
        }
        String temp = rules.generateTemp();
        LocalDateTime expiresAt = LocalDateTime.now().plus(tempLifetime);
        user.issueTempPassword(passwordEncoder.encode(temp), expiresAt);
        log.info("임시 비밀번호 재발급 id={}", user.getId());
        return new Issued(user, temp, expiresAt);
    }

    /**
     * 본인 비밀번호 변경. 지금 비밀번호를 다시 확인한다:
     * 로그인된 채 자리를 비운 PC에서 다른 사람이 비밀번호를 바꿔 계정을 빼앗지 못하게.
     */
    @Transactional
    public User changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("계정을 찾을 수 없습니다."));
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("지금 비밀번호가 올바르지 않습니다.");
        }
        rules.validateNew(newPassword, user.getEmail());
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new IllegalArgumentException("지금(임시) 비밀번호와 다른 비밀번호를 정해 주세요.");
        }
        user.changePassword(passwordEncoder.encode(newPassword));
        log.info("비밀번호 변경 id={}", user.getId());
        return user;
    }
}
