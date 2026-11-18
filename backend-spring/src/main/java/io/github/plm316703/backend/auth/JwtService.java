package io.github.plm316703.backend.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import io.github.plm316703.backend.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/**
 * JWT(로그인 증명서) 발급·검증 담당.
 *  - 서명: HS256 + 비밀키(JWT_SECRET, 32바이트 이상). 비밀키를 모르면 위조할 수 없다.
 *  - 저장: HttpOnly 쿠키 → 자바스크립트가 토큰을 읽을 수 없어 XSS로 훔쳐 가기 어렵다.
 *          SameSite=Strict → 다른 사이트에서 보낸 요청에는 쿠키가 붙지 않아 CSRF를 막는다.
 */
@Component
public class JwtService {

    public static final String COOKIE_NAME = "NUADA_TOKEN";

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final Duration lifetime;
    private final boolean cookieSecure;

    public JwtService(@Value("${security.jwt-secret}") String secret,
                      @Value("${security.jwt-lifetime-minutes}") long lifetimeMinutes,
                      @Value("${security.cookie-secure}") boolean cookieSecure) {
        byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            // 짧은 비밀키는 무차별 대입으로 풀릴 수 있음 → 서버 시작 자체를 막는다 (API 키와 같은 원칙)
            throw new IllegalStateException("JWT_SECRET 은 32바이트(영문 32자) 이상이어야 합니다.");
        }
        SecretKey key = new SecretKeySpec(bytes, "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        this.lifetime = Duration.ofMinutes(lifetimeMinutes);
        this.cookieSecure = cookieSecure;
    }

    public JwtDecoder decoder() {
        return decoder;
    }

    /** 로그인 성공 → 토큰 발급. subject = 이메일, role = 역할 */
    public String issue(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("nuada-airgetlam")
                .subject(user.getEmail())
                .issuedAt(now)
                .expiresAt(now.plus(lifetime))
                .claim("role", user.getRole().name())
                .claim("uid", user.getId())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public ResponseCookie loginCookie(String token) {
        return baseCookie(token).maxAge(lifetime).build();
    }

    public ResponseCookie logoutCookie() {
        return baseCookie("").maxAge(0).build();   // 같은 이름으로 수명 0 → 브라우저가 지움
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(cookieSecure)      // HTTPS 배포에서는 true
                .sameSite("Strict")
                .path("/");
    }
}
