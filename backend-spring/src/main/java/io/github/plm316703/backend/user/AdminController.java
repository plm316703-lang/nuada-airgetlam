package io.github.plm316703.backend.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 시스템 관리자: 계정 관리만 (환자 의료·연구 정보는 보지 않음).
 * 실명도 목록으로 내보내지 않는다 — 이메일(계정 아이디)과 역할, 비밀번호 상태만.
 * 주소 권한(/api/admin/** = 관리자만)은 SecurityConfig가 지킨다.
 */
@RestController
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final AccountService accountService;

    /** mustChangePassword: 임시 비밀번호를 아직 바꾸지 않은 계정인지 (관리자가 "변경 대기"를 볼 수 있게) */
    public record AccountView(Long id, String email, Role role, LocalDateTime createdAt,
                              boolean mustChangePassword, LocalDateTime tempPasswordExpiresAt) {
        static AccountView of(User u) {
            return new AccountView(u.getId(), u.getEmail(), u.getRole(), u.getCreatedAt(),
                    u.isMustChangePassword(), u.getTempPasswordExpiresAt());
        }
    }

    public record CreateRequest(@NotBlank @Email @Size(max = 100) String email,
                                @NotBlank @Size(max = 50) String name,
                                @NotNull Role role) {}

    /** 임시 비밀번호는 이 응답에만 담기고 다시 조회할 방법이 없다 → 화면이 "지금 받아 적으세요"라고 안내 */
    public record IssuedView(AccountView account, String tempPassword, LocalDateTime expiresAt) {
        static IssuedView of(AccountService.Issued i) {
            return new IssuedView(AccountView.of(i.user()), i.tempPassword(), i.expiresAt());
        }
    }

    @GetMapping("/api/admin/users")
    public List<AccountView> users() {
        return userRepository.findAll().stream().map(AccountView::of).toList();
    }

    @PostMapping("/api/admin/users")
    public IssuedView create(@Valid @RequestBody CreateRequest req) {
        return IssuedView.of(accountService.create(req.email(), req.name(), req.role()));
    }

    @PostMapping("/api/admin/users/{id}/temp-password")
    public IssuedView reissue(@PathVariable Long id) {
        return IssuedView.of(accountService.reissue(id));
    }
}
