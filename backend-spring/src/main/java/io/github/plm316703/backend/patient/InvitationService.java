package io.github.plm316703.backend.patient;

import io.github.plm316703.backend.record.LoginHistory;
import io.github.plm316703.backend.record.LoginHistoryRepository;
import io.github.plm316703.backend.user.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 환자 등록·보호자 초대·초대 수락 (11/20 R4).
 *
 * 흐름: 연구책임자가 등록·초대 → 서버가 초대 링크 비밀값을 만들어 **한 번만** 돌려줌 → 직원이 전달
 *      → 본인이 링크로 들어와 비밀번호를 정함 → 로그인.
 * 연구 동의는 여기서 받지 않는다 (Q-R4c): 동의는 로그인 후 환자 화면에서 본인이 따로 하는 명확한 행동이어야 함.
 */
@Slf4j
@Service
public class InvitationService {

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final PatientGuardianRepository guardianRepository;
    private final InvitationRepository invitationRepository;
    private final LoginHistoryRepository historyRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordRules rules;
    private final Duration inviteLifetime;
    private final SecureRandom random = new SecureRandom();

    public InvitationService(UserRepository userRepository, PatientRepository patientRepository,
                             PatientGuardianRepository guardianRepository, InvitationRepository invitationRepository,
                             LoginHistoryRepository historyRepository, PasswordEncoder passwordEncoder,
                             PasswordRules rules, @Value("${account.invite-hours}") long inviteHours) {
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
        this.guardianRepository = guardianRepository;
        this.invitationRepository = invitationRepository;
        this.historyRepository = historyRepository;
        this.passwordEncoder = passwordEncoder;
        this.rules = rules;
        this.inviteLifetime = Duration.ofHours(inviteHours);
    }

    /** 초대 결과. token이 null이면 초대 없이 끝난 경우 (이미 가입한 보호자를 연결만 함) */
    public record Invited(User user, String token, LocalDateTime expiresAt) {}

    /** 링크로 들어왔을 때 보여 줄 정보 (이 링크를 가진 사람에게만) */
    public record Preview(String name, String email, Role role, LocalDateTime expiresAt) {}

    // ───────── 연구책임자 ─────────

    /** 환자 등록 + 초대. 연구 등록(enroll)은 기존 버튼으로 따로 한다 (등록 ≠ 연구 참여) */
    @Transactional
    public Invited registerPatient(String name, String email, AmputationSide side, User actor, String ip) {
        User user = newAccount(email, name, Role.PATIENT);
        patientRepository.save(new Patient(user, side));
        return invite(user, actor, ip);
    }

    /**
     * 보호자 초대. 신분 확인(identityChecked)이 없으면 거절한다:
     * 보호자는 환자의 팔 상태·정지 기록을 보게 되므로, 아무나 연결되면 안 된다 (현업: 신분 확인 후 직원이 연결).
     * 이미 가입한 보호자 계정이면 새 초대 없이 연결만 한다.
     */
    @Transactional
    public Invited inviteGuardian(Long patientId, String name, String email, GuardianRelation relation,
                                  boolean identityChecked, User actor, String ip) {
        if (!identityChecked) {
            throw new IllegalArgumentException("보호자 신분을 확인한 뒤에만 초대할 수 있습니다.");
        }
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 환자입니다. id=" + patientId));
        User existing = userRepository.findByEmail(normalize(email)).orElse(null);
        if (existing != null) {
            if (existing.getRole() != Role.GUARDIAN) {
                throw new IllegalArgumentException("이미 다른 역할로 쓰이는 이메일입니다.");
            }
            boolean linked = guardianRepository.findAllByPatient(patient).stream()
                    .anyMatch(l -> l.getGuardian().getId().equals(existing.getId()));
            if (linked) throw new IllegalStateException("이미 이 환자와 연결된 보호자입니다.");
            guardianRepository.save(new PatientGuardian(patient, existing, relation));
            log.info("기존 보호자 연결 patientId={} guardianId={}", patientId, existing.getId());
            return new Invited(existing, null, null);
        }
        User guardian = newAccount(email, name, Role.GUARDIAN);
        guardianRepository.save(new PatientGuardian(patient, guardian, relation));
        return invite(guardian, actor, ip);
    }

    /** 다시 초대: 이전 링크는 모두 무효로 하고 새 링크 발급. 이미 가입한 계정은 대상이 아님 (비밀번호 분실은 관리자 재발급) */
    @Transactional
    public Invited reinvite(Long userId, User actor, String ip) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("계정을 찾을 수 없습니다."));
        if (user.getRole() != Role.PATIENT && user.getRole() != Role.GUARDIAN) {
            throw new IllegalArgumentException("환자·보호자 계정만 초대할 수 있습니다.");
        }
        if (invitationRepository.existsByUserAndUsedAtIsNotNull(user)) {
            throw new IllegalStateException("이미 가입을 마친 계정입니다. 비밀번호를 잊었다면 관리자가 임시 비밀번호를 재발급합니다.");
        }
        LocalDateTime now = LocalDateTime.now();
        invitationRepository.findAllByUser(user).forEach(i -> i.revoke(now));
        return invite(user, actor, ip);
    }

    /** 초대를 받았지만 아직 가입하지 않은 계정인가 (화면의 "초대 대기" 표시) */
    @Transactional(readOnly = true)
    public boolean isPending(User user) {
        return invitationRepository.existsByUser(user) && !invitationRepository.existsByUserAndUsedAtIsNotNull(user);
    }

    // ───────── 링크를 받은 본인 (로그인 전) ─────────

    @Transactional(readOnly = true)
    public Preview preview(String token) {
        Invitation inv = usable(token);
        User u = inv.getUser();
        return new Preview(u.getName(), u.getEmail(), u.getRole(), inv.getExpiresAt());
    }

    /** 비밀번호를 정하고 링크를 사용 처리. 규칙은 직원 비밀번호와 같다 (10자 이상, 이메일 앞부분 금지) */
    @Transactional
    public User accept(String token, String newPassword, String ip) {
        Invitation inv = usable(token);
        User user = inv.getUser();
        rules.validateNew(newPassword, user.getEmail());
        user.changePassword(passwordEncoder.encode(newPassword));
        inv.markUsed(LocalDateTime.now());
        historyRepository.save(new LoginHistory(LoginHistory.Event.INVITATION_ACCEPTED, null, user.getEmail(), null, ip));
        log.info("초대 수락 id={}", user.getId());
        return user;
    }

    // ───────── 내부 ─────────

    /**
     * 가입 전 계정: 아무도 모르는 무작위 비밀번호(해시)로 만들어 둔다.
     * → 초대 링크로 비밀번호를 정하기 전에는 어떤 비밀번호로도 로그인할 수 없다.
     */
    private User newAccount(String email, String name, Role role) {
        String normalized = normalize(email);
        if (userRepository.findByEmail(normalized).isPresent()) {
            throw new IllegalArgumentException("이미 있는 이메일입니다.");
        }
        String unusable = passwordEncoder.encode(newToken());
        return userRepository.save(new User(normalized, unusable, name.trim(), role));
    }

    private Invited invite(User user, User actor, String ip) {
        String token = newToken();
        LocalDateTime expiresAt = LocalDateTime.now().plus(inviteLifetime);
        invitationRepository.save(new Invitation(user, sha256(token), expiresAt, actor == null ? null : actor.getEmail()));
        historyRepository.save(new LoginHistory(LoginHistory.Event.INVITATION_CREATED, null, user.getEmail(),
                actor == null ? null : actor.getEmail(), ip));
        log.info("초대 발급 id={} role={}", user.getId(), user.getRole());
        return new Invited(user, token, expiresAt);
    }

    private Invitation usable(String token) {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("초대 링크가 올바르지 않습니다.");
        Invitation inv = invitationRepository.findByTokenHash(sha256(token))
                // 없는 링크와 쓸 수 없는 링크를 구분해 주되, 없는 링크에는 아무 정보도 주지 않는다
                .orElseThrow(() -> new IllegalArgumentException("초대 링크가 올바르지 않습니다."));
        String reason = inv.unusableReason(LocalDateTime.now());
        if (reason != null) throw new IllegalStateException(reason);
        return inv;
    }

    /** 32바이트(256비트) 난수 → URL에 그대로 넣을 수 있는 글자 (Base64 URL, 43자) */
    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 쓸 수 없습니다.", e);   // 자바 표준이라 실제로는 일어나지 않음
        }
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
