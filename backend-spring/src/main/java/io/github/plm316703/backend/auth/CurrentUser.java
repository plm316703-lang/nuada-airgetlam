package io.github.plm316703.backend.auth;

import io.github.plm316703.backend.user.User;
import io.github.plm316703.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * "지금 로그인한 사람"을 DB의 User로 바꿔 주는 도우미.
 * JWT의 subject(이메일)로 찾는다. 토큰 발급 뒤 계정이 지워졌으면 401과 같은 의미로 거절.
 */
@Component
@RequiredArgsConstructor
public class CurrentUser {

    private final UserRepository userRepository;

    public User get(Authentication authentication) {
        if (authentication == null) {
            throw new UnauthorizedException("로그인이 필요합니다.");
        }
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UnauthorizedException("계정을 찾을 수 없습니다. 다시 로그인하세요."));
    }

    public static class UnauthorizedException extends RuntimeException {
        public UnauthorizedException(String message) { super(message); }
    }
}
