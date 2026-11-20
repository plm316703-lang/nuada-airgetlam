package io.github.plm316703.backend.auth;

import io.github.plm316703.backend.user.AccountService;
import io.github.plm316703.backend.user.Role;
import io.github.plm316703.backend.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 로그인 / 로그아웃 / 내 정보 (웨이터).
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtService jwtService;
    private final CurrentUser currentUser;
    private final AccountService accountService;
    private final LoginService loginService;

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}

    public record PasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {}

    /**
     * mustChangePassword = true면 화면은 어디로 가든 비밀번호 변경 화면으로 보낸다.
     * idleLogoutMinutes: 화면의 자동 로그아웃 타이머 길이 (서버 증표 수명과 같은 값을 내려줘 둘이 어긋나지 않게)
     */
    public record MeResponse(String email, String name, Role role, String home, boolean mustChangePassword,
                             long idleLogoutMinutes) {}

    /**
     * 로그인. 판단·잠금·기록은 LoginService가 한다.
     * IP는 getRemoteAddr()만 쓴다: X-Forwarded-For 헤더는 누구나 꾸며 보낼 수 있어서,
     * 믿을 수 있는 프록시 설정 없이 쓰면 기록이 거짓이 될 수 있다.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        LoginService.Result r = loginService.login(req.email(), req.password(), http.getRemoteAddr());
        if (!r.ok()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", r.failMessage()));
        }
        return withToken(r.user());
    }

    /**
     * 본인 비밀번호 변경 (임시 비밀번호 → 새 비밀번호, 또는 평소 변경).
     * 성공하면 "변경 대기"가 풀린 새 토큰을 다시 발급한다 → 다시 로그인하지 않아도 바로 원래 화면을 쓸 수 있음.
     */
    @PostMapping("/password")
    public ResponseEntity<MeResponse> changePassword(@Valid @RequestBody PasswordRequest req, Authentication authentication,
                                                     HttpServletRequest http) {
        User me = currentUser.get(authentication);
        User changed = accountService.changePassword(me.getId(), req.currentPassword(), req.newPassword(), http.getRemoteAddr());
        return withToken(changed);
    }

    /**
     * 증표 연장 (자동 로그아웃 R3). 화면이 "사람이 쓰고 있다"고 판단할 때만 부른다.
     * 30분 동안 아무 조작이 없으면 화면이 이걸 부르지 않으므로 증표가 만료돼 서버에서도 로그아웃된다.
     * DB의 현재 계정 상태로 새로 발급하므로, 그사이 바뀐 역할·비밀번호 상태도 반영된다.
     */
    @PostMapping("/refresh")
    public ResponseEntity<MeResponse> refresh(Authentication authentication) {
        return withToken(currentUser.get(authentication));
    }

    private ResponseEntity<MeResponse> withToken(User user) {
        String token = jwtService.issue(user);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, jwtService.loginCookie(token).toString())
                .body(toMe(user));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, jwtService.logoutCookie().toString())
                .build();
    }

    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        return toMe(currentUser.get(authentication));
    }

    /** 역할별 첫 화면. 비밀번호를 바꿔야 하면 첫 화면은 비밀번호 변경 화면 */
    private MeResponse toMe(User u) {
        String home = u.isMustChangePassword() ? "/password.html" : switch (u.getRole()) {
            case OPERATOR, PRINCIPAL_INVESTIGATOR, RESEARCHER -> "/dashboard.html";
            case PATIENT -> "/patient.html";
            case GUARDIAN -> "/guardian.html";
            case SYSTEM_ADMIN -> "/admin.html";
        };
        return new MeResponse(u.getEmail(), u.getName(), u.getRole(), home, u.isMustChangePassword(),
                jwtService.lifetimeMinutes());
    }
}
