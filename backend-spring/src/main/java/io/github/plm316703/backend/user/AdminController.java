package io.github.plm316703.backend.user;

import io.github.plm316703.backend.auth.CurrentUser;
import io.github.plm316703.backend.record.LoginHistory;
import io.github.plm316703.backend.record.LoginHistoryRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

/**
 * 시스템 관리자: 계정 관리만 (환자 의료·연구 정보는 보지 않음).
 * 실명도 목록으로 내보내지 않는다 — 이메일(계정 아이디)과 역할, 비밀번호·잠금 상태만.
 * 주소 권한(/api/admin/** = 관리자만)은 SecurityConfig가 지킨다.
 */
@RestController
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final AccountService accountService;
    private final LoginHistoryRepository historyRepository;
    private final CurrentUser currentUser;

    /**
     * mustChangePassword: 임시 비밀번호를 아직 바꾸지 않은 계정인지 (관리자가 "변경 대기"를 볼 수 있게)
     * lockedUntil: 잠김이 풀리는 시각 (지난 시각이면 이미 풀린 것)
     */
    public record AccountView(Long id, String email, Role role, LocalDateTime createdAt,
                              boolean mustChangePassword, LocalDateTime tempPasswordExpiresAt,
                              boolean locked, LocalDateTime lockedUntil) {
        static AccountView of(User u) {
            return new AccountView(u.getId(), u.getEmail(), u.getRole(), u.getCreatedAt(),
                    u.isMustChangePassword(), u.getTempPasswordExpiresAt(),
                    u.isLocked(LocalDateTime.now()), u.getLockedUntil());
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

    public record HistoryView(Long id, LocalDateTime occurredAt, LoginHistory.Event event,
                              LoginHistory.FailReason failReason, String email, String actorEmail, String ip) {
        static HistoryView of(LoginHistory h) {
            return new HistoryView(h.getId(), h.getOccurredAt(), h.getEvent(), h.getFailReason(),
                    h.getEmail(), h.getActorEmail(), h.getIp());
        }
    }

    // "실패만 보기"에 넣을 종류: 로그인 실패와 그로 인한 잠김
    private static final EnumSet<LoginHistory.Event> FAILURES =
            EnumSet.of(LoginHistory.Event.LOGIN_FAILED, LoginHistory.Event.ACCOUNT_LOCKED);

    @GetMapping("/api/admin/users")
    public List<AccountView> users() {
        return userRepository.findAll().stream().map(AccountView::of).toList();
    }

    @PostMapping("/api/admin/users")
    public IssuedView create(@Valid @RequestBody CreateRequest req, Authentication auth, HttpServletRequest http) {
        return IssuedView.of(accountService.create(req.email(), req.name(), req.role(),
                currentUser.get(auth), http.getRemoteAddr()));
    }

    @PostMapping("/api/admin/users/{id}/temp-password")
    public IssuedView reissue(@PathVariable Long id, Authentication auth, HttpServletRequest http) {
        return IssuedView.of(accountService.reissue(id, currentUser.get(auth), http.getRemoteAddr()));
    }

    @PostMapping("/api/admin/users/{id}/unlock")
    public AccountView unlock(@PathVariable Long id, Authentication auth, HttpServletRequest http) {
        return AccountView.of(accountService.unlock(id, currentUser.get(auth), http.getRemoteAddr()));
    }

    /** 로그인·계정 관리 기록 최근 100건 (failuresOnly=true면 실패·잠김만) */
    @GetMapping("/api/admin/login-history")
    public List<HistoryView> history(@RequestParam(defaultValue = "false") boolean failuresOnly) {
        var rows = failuresOnly
                ? historyRepository.findTop100ByEventInOrderByIdDesc(FAILURES)
                : historyRepository.findTop100ByOrderByIdDesc();
        return rows.stream().map(HistoryView::of).toList();
    }
}
