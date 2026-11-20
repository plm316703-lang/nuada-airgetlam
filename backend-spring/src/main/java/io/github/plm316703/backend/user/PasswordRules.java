package io.github.plm316703.backend.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * 비밀번호 규칙 — 임시 비밀번호 만들기 + 새 비밀번호 검사.
 *
 * 규칙(11/20 사용자 결정, 미국 NIST 비밀번호 지침 참고):
 *  - "대문자·특수문자 꼭 넣기" 같은 조합 규칙 대신 **길이**를 요구한다 (조합 규칙은 "Password1!" 같은 뻔한 비밀번호를 낳음)
 *  - 새 비밀번호는 임시 비밀번호와 달라야 하고, 이메일 앞부분(예: operator)을 넣으면 안 된다 (추측하기 쉬움)
 */
@Component
public class PasswordRules {

    // 0/O/o, 1/l/I 처럼 손으로 옮겨 적을 때 헷갈리는 글자를 뺀 문자표 (관리자가 받아 적어 전달하므로)
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    // BCrypt는 72바이트까지만 비교한다 → 그보다 긴 부분은 무시돼 "길게 쓴 것"이 의미 없으므로 상한을 둔다
    private static final int MAX_LENGTH = 64;

    // 예측할 수 없는 난수가 필요하므로 일반 Random이 아니라 SecureRandom
    private final SecureRandom random = new SecureRandom();
    private final int tempLength;
    private final int minLength;

    public PasswordRules(@Value("${account.temp-password-length}") int tempLength,
                         @Value("${account.min-password-length}") int minLength) {
        if (tempLength < 10 || minLength < 8) {
            throw new IllegalStateException("비밀번호 길이 설정이 너무 짧습니다: temp=" + tempLength + " min=" + minLength);
        }
        this.tempLength = tempLength;
        this.minLength = minLength;
    }

    public String generateTemp() {
        StringBuilder sb = new StringBuilder(tempLength);
        for (int i = 0; i < tempLength; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /**
     * 새 비밀번호 검사. 문제가 있으면 IllegalArgumentException (화면에 그대로 보여 줄 문장).
     * "임시 비밀번호와 같은지"는 해시 비교가 필요해서 AccountService가 따로 검사한다.
     */
    public void validateNew(String password, String email) {
        if (password == null || password.length() < minLength) {
            throw new IllegalArgumentException("새 비밀번호는 " + minLength + "자 이상이어야 합니다.");
        }
        if (password.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("새 비밀번호는 " + MAX_LENGTH + "자 이하로 해 주세요.");
        }
        if (password.isBlank()) {
            throw new IllegalArgumentException("공백만으로 된 비밀번호는 쓸 수 없습니다.");
        }
        String local = email == null ? "" : email.split("@")[0].toLowerCase(Locale.ROOT);
        // 너무 짧은 이메일 앞부분(예: "a")까지 막으면 정상 비밀번호도 거절되므로 3자 이상일 때만 검사
        if (local.length() >= 3 && password.toLowerCase(Locale.ROOT).contains(local)) {
            throw new IllegalArgumentException("비밀번호에 이메일 앞부분(" + local + ")을 넣을 수 없습니다.");
        }
    }

    public int minLength() { return minLength; }
}
