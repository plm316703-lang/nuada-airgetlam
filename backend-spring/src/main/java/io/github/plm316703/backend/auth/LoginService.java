package io.github.plm316703.backend.auth;

import io.github.plm316703.backend.record.LoginHistory;
import io.github.plm316703.backend.record.LoginHistory.Event;
import io.github.plm316703.backend.record.LoginHistory.FailReason;
import io.github.plm316703.backend.record.LoginHistoryRepository;
import io.github.plm316703.backend.user.User;
import io.github.plm316703.backend.user.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 로그인 판단 + 실패 잠금 + 로그인 기록 (11/20 R3).
 * 컨트롤러에서 꺼내 서비스로 둔 이유: 잠금 횟수 갱신과 기록 저장을 한 트랜잭션으로 묶고, 순서를 한곳에서 지키려고.
 *
 * 검사 순서가 중요하다:
 *  ① 없는 이메일 → 실패 (기록은 남김)
 *  ② 잠긴 계정 → 비밀번호를 **확인하지 않고** 거절 (잠긴 동안 계속 대입해 보는 공격을 막음)
 *  ③ 비밀번호 틀림 → 실패 횟수 +1, 5번째면 잠금
 *  ④ 임시 비밀번호 만료 → 거절 (재발급 안내)
 *  ⑤ 성공 → 실패 횟수 초기화
 */
@Slf4j
@Service
public class LoginService {

    // ①·③은 같은 문장 → 어떤 이메일이 가입됐는지 알 수 없게
    static final String BAD_CREDENTIALS = "이메일 또는 비밀번호가 올바르지 않습니다.";
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginHistoryRepository historyRepository;
    private final int maxFailures;
    private final Duration lockFor;

    public LoginService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                        LoginHistoryRepository historyRepository,
                        @Value("${account.max-failed-logins}") int maxFailures,
                        @Value("${account.lock-minutes}") long lockMinutes) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.historyRepository = historyRepository;
        this.maxFailures = maxFailures;
        this.lockFor = Duration.ofMinutes(lockMinutes);
    }

    /** 결과: 성공이면 user, 실패면 화면에 보여 줄 문장 */
    public record Result(User user, String failMessage) {
        public boolean ok() { return user != null; }
    }

    @Transactional
    public Result login(String rawEmail, String password, String ip) {
        String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase();
        LocalDateTime now = LocalDateTime.now();
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            fail(email, FailReason.BAD_CREDENTIALS, ip);
            return new Result(null, BAD_CREDENTIALS);
        }
        if (user.isLocked(now)) {
            fail(email, FailReason.LOCKED, ip);
            return new Result(null, lockedMessage(user));
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            fail(email, FailReason.BAD_CREDENTIALS, ip);
            if (user.recordLoginFailure(now, maxFailures, lockFor)) {
                historyRepository.save(new LoginHistory(Event.ACCOUNT_LOCKED, null, email, null, ip));
                log.warn("로그인 {}회 실패로 잠금 id={}", maxFailures, user.getId());
                return new Result(null, lockedMessage(user));
            }
            return new Result(null, BAD_CREDENTIALS);
        }
        // 비밀번호까지 맞힌 뒤에만 알려 주므로, 이 문장으로 계정 존재 여부가 새지 않는다
        if (user.isTempPasswordExpired(now)) {
            fail(email, FailReason.TEMP_EXPIRED, ip);
            return new Result(null, "임시 비밀번호가 만료되었습니다. 관리자에게 재발급을 요청하세요.");
        }
        user.recordLoginSuccess();
        historyRepository.save(new LoginHistory(Event.LOGIN_SUCCESS, null, email, null, ip));
        return new Result(user, null);
    }

    /**
     * 잠김 안내. "잠겼다"고 알려 주면 그 이메일이 가입돼 있다는 것이 드러나지만,
     * 왜 로그인이 안 되는지 모르는 혼란이 더 크다고 보고 알려 준다 (현업에서도 흔한 방식, 11/20 Claude 제안).
     */
    private String lockedMessage(User user) {
        return "로그인을 " + maxFailures + "번 실패해 잠겼습니다. "
                + user.getLockedUntil().format(HHMM) + " 이후 다시 시도하거나 관리자에게 잠금 해제를 요청하세요.";
    }

    private void fail(String email, FailReason reason, String ip) {
        historyRepository.save(new LoginHistory(Event.LOGIN_FAILED, reason, email, null, ip));
    }
}
