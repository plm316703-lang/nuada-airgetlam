package io.github.plm316703.backend.user;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 시스템 관리자: 계정 관리만 (환자 의료·연구 정보는 보지 않음).
 * 실명도 내보내지 않는다 — 이메일(계정 아이디)과 역할만.
 */
@RestController
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;

    public record AccountView(Long id, String email, Role role, LocalDateTime createdAt) {}

    @GetMapping("/api/admin/users")
    public List<AccountView> users() {
        return userRepository.findAll().stream()
                .map(u -> new AccountView(u.getId(), u.getEmail(), u.getRole(), u.getCreatedAt()))
                .toList();
    }
}
