package io.github.plm316703.backend.auth;

import io.github.plm316703.backend.user.AccountService;
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

import java.time.LocalDateTime;
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
    private final AccountService accountService;

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}

    public record PasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {}

    /** mustChangePassword = true면 화면은 어디로 가든 비밀번호 변경 화면으로 보낸다 */
    public record MeResponse(String email, String name, Role role, String home, boolean mustChangePassword) {}

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        // 관리자가 발급한 이메일은 소문자로 저장하므로, 로그인할 때도 소문자로 맞춰 찾는다
        User user = userRepository.findByEmail(req.email().trim().toLowerCase()).orElse(null);
        // 이메일이 없는 경우와 비밀번호가 틀린 경우를 같은 메시지로 → 어떤 이메일이 가입됐는지 알 수 없게
        if (user == null || !passwordEncoder.matches(req.password(), user.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "이메일 또는 비밀번호가 올바르지 않습니다."));
        }
        // 비밀번호까지 맞힌 뒤에만 알려 주므로, 이 문장으로 계정 존재 여부가 새지 않는다
        if (user.isTempPasswordExpired(LocalDateTime.now())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "임시 비밀번호가 만료되었습니다. 관리자에게 재발급을 요청하세요."));
        }
        return withToken(user);
    }

    /**
     * 본인 비밀번호 변경 (임시 비밀번호 → 새 비밀번호, 또는 평소 변경).
     * 성공하면 "변경 대기"가 풀린 새 토큰을 다시 발급한다 → 다시 로그인하지 않아도 바로 원래 화면을 쓸 수 있음.
     */
    @PostMapping("/password")
    public ResponseEntity<MeResponse> changePassword(@Valid @RequestBody PasswordRequest req, Authentication authentication) {
        User me = currentUser.get(authentication);
        User changed = accountService.changePassword(me.getId(), req.currentPassword(), req.newPassword());
        return withToken(changed);
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
        return new MeResponse(u.getEmail(), u.getName(), u.getRole(), home, u.isMustChangePassword());
    }
}
