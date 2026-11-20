package io.github.plm316703.backend.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

import java.util.List;

/**
 * 보안 설정 — 보안 5겹 중 ① 로그인(JWT+BCrypt) ② 역할별 권한.
 * (③ 데이터 단위 권한, ④ 익명 코드는 서비스·화면 데이터에서 처리)
 *
 * 역할 묶음:
 *  - 관제 보기: 관제 담당자, 연구책임자, 연구자
 *  - 관제 조작(세션·재개·초기화): 관제 담당자, 연구책임자  ← 사용자 결정
 */
@Configuration
public class SecurityConfig {

    private static final String[] MONITOR_ROLES = {"OPERATOR", "PRINCIPAL_INVESTIGATOR", "RESEARCHER"};
    private static final String[] CONTROL_ROLES = {"OPERATOR", "PRINCIPAL_INVESTIGATOR"};

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();   // 비밀번호는 BCrypt 해시로만 저장
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        http
            // CSRF 토큰 대신 SameSite=Strict 쿠키로 다른 사이트 요청을 막는다 (JwtService 참고)
            .csrf(csrf -> csrf.disable())
            // 서버에 로그인 세션을 만들지 않음: 매 요청마다 쿠키의 JWT로 확인
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/login", "/api/auth/logout").permitAll()
                // 초대 링크로 가입: 아직 비밀번호가 없어 로그인할 수 없으므로 열어 둔다 (링크 비밀값이 출입증, R4)
                .requestMatchers(HttpMethod.POST, "/api/invitations/preview", "/api/invitations/accept").permitAll()
                // 판별기 연결은 로그인 대신 공유 API 키로 문지기가 확인 (ApiKeyHandshakeInterceptor)
                .requestMatchers("/ws/classifier").permitAll()
                .requestMatchers("/ws/dashboard").hasAnyRole(MONITOR_ROLES)
                .requestMatchers(HttpMethod.GET, "/api/dashboard/**").hasAnyRole(MONITOR_ROLES)
                .requestMatchers("/api/dashboard/**").hasAnyRole(CONTROL_ROLES)
                .requestMatchers(HttpMethod.GET, "/api/patients").hasAnyRole(MONITOR_ROLES)
                .requestMatchers("/api/patients/**", "/api/researchers").hasRole("PRINCIPAL_INVESTIGATOR")
                .requestMatchers("/api/me/patient/**").hasRole("PATIENT")
                .requestMatchers("/api/me/guardian/**").hasRole("GUARDIAN")
                .requestMatchers("/api/admin/**").hasRole("SYSTEM_ADMIN")
                .requestMatchers("/api/**").authenticated()
                // 화면 파일(html, css, js)은 누구나 받을 수 있음. 데이터는 위의 API가 지킨다
                .anyRequest().permitAll())
            .oauth2ResourceServer(o -> o
                .bearerTokenResolver(cookieTokenResolver())
                .jwt(jwt -> jwt
                    .decoder(jwtService.decoder())
                    .jwtAuthenticationConverter(roleConverter())))
            // 로그인 안 됨 → 401, 권한 없음 → 403 (화면 JS가 이 코드를 보고 로그인 화면으로 보냄)
            .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        return http.build();
    }

    /**
     * 토큰을 Authorization 헤더 대신 HttpOnly 쿠키에서 꺼낸다.
     * API와 관제 WebSocket 주소에서만 읽는다 → 만료된 쿠키가 있어도 로그인 화면 파일은 열린다.
     */
    private BearerTokenResolver cookieTokenResolver() {
        return (HttpServletRequest request) -> {
            String path = request.getRequestURI();
            if (!(path.startsWith("/api/") || path.equals("/ws/dashboard"))) return null;
            // 열어 둔 주소에서는 쿠키를 읽지 않는다: 만료된 쿠키가 남아 있으면 permitAll 주소도 401로 막히기 때문
            if (path.equals("/api/auth/login") || path.equals("/api/auth/logout")
                    || path.startsWith("/api/invitations/")) return null;
            Cookie[] cookies = request.getCookies();
            if (cookies == null) return null;
            for (Cookie c : cookies) {
                if (JwtService.COOKIE_NAME.equals(c.getName()) && !c.getValue().isBlank()) {
                    return c.getValue();
                }
            }
            return null;
        };
    }

    /**
     * JWT의 role 클레임("OPERATOR") → 스프링 권한("ROLE_OPERATOR").
     * 단, 비밀번호 변경이 필요한 토큰(pwc=true)에는 역할 권한을 주지 않고 "변경 대기" 권한만 준다.
     * → 역할이 필요한 모든 API(관제·환자·관리자)가 403으로 막히고, 로그인만 필요한
     *   /api/auth/me·/api/auth/password 만 쓸 수 있다. 화면이 아니라 서버가 막는 것이 핵심.
     */
    private JwtAuthenticationConverter roleConverter() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("role");
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt ->
                Boolean.TRUE.equals(jwt.getClaimAsBoolean(JwtService.PASSWORD_CHANGE_CLAIM))
                        ? List.<GrantedAuthority>of(new SimpleGrantedAuthority("PASSWORD_CHANGE_REQUIRED"))
                        : roles.convert(jwt));
        return converter;
    }
}
