package io.github.plm316703.backend.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordRulesTest {   // 순수 자바 테스트 (설정값은 application.yaml과 같게 12자·10자)

    private final PasswordRules rules = new PasswordRules(12, 10);

    @Test
    @DisplayName("임시 비밀번호는 12자이고, 헷갈리는 글자(0 O o 1 l I)가 없으며, 매번 다르다")
    void tempPassword() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String p = rules.generateTemp();
            assertThat(p).hasSize(12).doesNotContainPattern("[0Oo1lI]");
            seen.add(p);
        }
        assertThat(seen).hasSize(200);   // 200번 만들어 겹치지 않음 → 예측하기 어려움
    }

    @Test
    @DisplayName("10자 미만이면 거절한다")
    void tooShort() {
        assertThatThrownBy(() -> rules.validateNew("short123", "operator@nuada.test"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("10자 이상");
    }

    @Test
    @DisplayName("이메일 앞부분이 들어 있으면 대소문자와 상관없이 거절한다")
    void containsEmailLocalPart() {
        assertThatThrownBy(() -> rules.validateNew("myOPERATOR2026", "operator@nuada.test"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("이메일 앞부분");
    }

    @Test
    @DisplayName("규칙을 지킨 비밀번호는 통과한다")
    void ok() {
        rules.validateNew("silver arm of nuada", "operator@nuada.test");
    }

    @Test
    @DisplayName("임시 비밀번호는 만료 시각이 지나면 만료, 비밀번호를 바꾸면 임시 상태가 풀린다")
    void tempExpiryOnUser() {
        User u = new User("a@nuada.test", "hash", "테스트", Role.OPERATOR);
        LocalDateTime expires = LocalDateTime.of(2026, 11, 23, 12, 0);
        u.issueTempPassword("hash2", expires);

        assertThat(u.isMustChangePassword()).isTrue();
        assertThat(u.isTempPasswordExpired(expires.minusMinutes(1))).isFalse();
        assertThat(u.isTempPasswordExpired(expires.plusMinutes(1))).isTrue();

        u.changePassword("hash3");
        assertThat(u.isMustChangePassword()).isFalse();
        assertThat(u.isTempPasswordExpired(expires.plusDays(10))).isFalse();
    }
}
