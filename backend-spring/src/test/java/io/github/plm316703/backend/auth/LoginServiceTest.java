package io.github.plm316703.backend.auth;

import io.github.plm316703.backend.record.LoginHistory;
import io.github.plm316703.backend.record.LoginHistoryRepository;
import io.github.plm316703.backend.user.Role;
import io.github.plm316703.backend.user.User;
import io.github.plm316703.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 로그인 서비스(LoginService) 검사 — 실패 잠금과 로그인 기록.
 * 설정값(application.yaml): 5번 실패 → 15분 잠금
 *
 * @SpringBootTest + @Transactional: 진짜 DB로 검사하고, 끝나면 되돌린다 (기존 테스트와 같은 방식)
 */
@SpringBootTest
@Transactional
class LoginServiceTest {

    private static final String EMAIL = "lock-test@nuada.test";
    private static final String RIGHT = "right password 123";

    @Autowired LoginService loginService;
    @Autowired UserRepository userRepository;
    @Autowired LoginHistoryRepository historyRepository;
    @Autowired PasswordEncoder passwordEncoder;

    User user;

    @BeforeEach
    void setUp() {
        user = userRepository.save(new User(EMAIL, passwordEncoder.encode(RIGHT), "테스트잠금", Role.OPERATOR));
    }

    @Test
    @DisplayName("비밀번호를 5번 틀리면 잠기고, 잠긴 동안에는 맞는 비밀번호도 거절된다")
    void locksAfterFiveFailures() {
        for (int i = 0; i < 4; i++) {
            assertThat(loginService.login(EMAIL, "wrong", "127.0.0.1").failMessage())
                    .isEqualTo(LoginService.BAD_CREDENTIALS);
        }
        // 5번째 실패에서 잠김 안내로 바뀐다
        assertThat(loginService.login(EMAIL, "wrong", "127.0.0.1").failMessage()).contains("잠겼습니다");
        assertThat(user.isLocked(LocalDateTime.now())).isTrue();

        // 잠긴 동안은 비밀번호를 확인하지 않고 거절 (계속 대입해 보는 공격 방지)
        LoginService.Result r = loginService.login(EMAIL, RIGHT, "127.0.0.1");
        assertThat(r.ok()).isFalse();
        assertThat(r.failMessage()).contains("잠겼습니다");
    }

    @Test
    @DisplayName("로그인에 성공하면 실패 횟수가 0으로 돌아간다 (실패가 띄엄띄엄 쌓여 잠기지 않게)")
    void successResetsFailures() {
        for (int i = 0; i < 4; i++) loginService.login(EMAIL, "wrong", "127.0.0.1");
        assertThat(loginService.login(EMAIL, RIGHT, "127.0.0.1").ok()).isTrue();
        assertThat(user.getFailedLoginCount()).isZero();

        // 다시 4번 틀려도 아직 잠기지 않는다
        for (int i = 0; i < 4; i++) loginService.login(EMAIL, "wrong", "127.0.0.1");
        assertThat(user.isLocked(LocalDateTime.now())).isFalse();
    }

    @Test
    @DisplayName("없는 이메일로 시도한 것도 실패 기록으로 남는다 (공격 시도를 알아보려고)")
    void unknownEmailIsRecorded() {
        long before = historyRepository.count();

        LoginService.Result r = loginService.login("nobody@nuada.test", "x", "10.0.0.9");

        assertThat(r.failMessage()).isEqualTo(LoginService.BAD_CREDENTIALS);   // 가입 여부가 드러나지 않는 같은 문장
        assertThat(historyRepository.count()).isEqualTo(before + 1);
        LoginHistory last = historyRepository.findTop100ByOrderByIdDesc().get(0);
        assertThat(last.getEvent()).isEqualTo(LoginHistory.Event.LOGIN_FAILED);
        assertThat(last.getFailReason()).isEqualTo(LoginHistory.FailReason.BAD_CREDENTIALS);
        assertThat(last.getEmail()).isEqualTo("nobody@nuada.test");
        assertThat(last.getIp()).isEqualTo("10.0.0.9");
    }
}
