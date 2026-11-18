package io.github.plm316703.backend.auth;

import io.github.plm316703.backend.user.Role;
import io.github.plm316703.backend.user.User;
import io.github.plm316703.backend.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 로그인 / 로그아웃 / 내 정보 (웨이터).
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final CurrentUser currentUser;

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}

    public record MeResponse(String email, String name, Role role, String home) {}

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        User user = userRepository.findByEmail(req.email()).orElse(null);
        // 이메일이 없는 경우와 비밀번호가 틀린 경우를 같은 메시지로 → 어떤 이메일이 가입됐는지 알 수 없게
        if (user == null || !passwordEncoder.matches(req.password(), user.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "이메일 또는 비밀번호가 올바르지 않습니다."));
        }
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

    /** 역할별 첫 화면 */
    private MeResponse toMe(User u) {
        String home = switch (u.getRole()) {
            case OPERATOR, PRINCIPAL_INVESTIGATOR, RESEARCHER -> "/dashboard.html";
            case PATIENT -> "/patient.html";
            case GUARDIAN -> "/guardian.html";
            case SYSTEM_ADMIN -> "/admin.html";
        };
        return new MeResponse(u.getEmail(), u.getName(), u.getRole(), home);
    }
}
