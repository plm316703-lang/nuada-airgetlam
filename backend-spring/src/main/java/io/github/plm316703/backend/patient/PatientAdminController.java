package io.github.plm316703.backend.patient;

import io.github.plm316703.backend.auth.CurrentUser;
import io.github.plm316703.backend.user.Role;
import io.github.plm316703.backend.user.User;
import io.github.plm316703.backend.user.UserRepository;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 관제·연구 직원용 환자 API (웨이터).
 *
 * 보안 5겹 중 ③ 데이터 단위 권한 + ④ 가명 처리:
 *  - 관제 담당자(OPERATOR): 등록된 환자만, 익명 코드만
 *  - 연구책임자(PI)       : 등록된 환자는 실명, 미등록 환자는 코드만 (등록 버튼용)
 *  - 연구자(RESEARCHER)   : 동의 중인 환자만. 나에게 배정된 환자는 실명, 나머지는 코드
 *    (동의하지 않은 환자의 데이터는 익명이라도 연구에 쓰지 않는다 — 내 설계 결정)
 */
@RestController
@RequiredArgsConstructor
public class PatientAdminController {

    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final ResearcherAssignmentRepository assignmentRepository;
    private final ResearcherAssignmentService assignmentService;
    private final CurrentUser currentUser;

    public record PatientView(Long id, String researchCode, String displayName, AmputationSide amputationSide,
                              boolean enrolled, LocalDateTime enrolledAt, boolean consent, boolean participating,
                              boolean assignedToMe) {}

    public record ResearcherView(Long id, String name) {}

    public record AssignRequest(@NotNull Long researcherId) {}

    @GetMapping("/api/patients")
    @Transactional(readOnly = true)
    public List<PatientView> list(Authentication auth) {
        User me = currentUser.get(auth);
        // 연구자에게 배정된 환자 id 목록 (실명 공개 여부 판단)
        Set<Long> mine = me.getRole() == Role.RESEARCHER
                ? assignmentRepository.findAll().stream()
                    .filter(a -> a.getResearcher().getId().equals(me.getId()))
                    .map(a -> a.getPatient().getId())
                    .collect(Collectors.toSet())
                : Set.of();

        return patientRepository.findAll().stream()
                .filter(p -> switch (me.getRole()) {
                    case OPERATOR -> p.getEnrolledAt() != null;
                    case RESEARCHER -> p.isResearchConsent();
                    default -> true;   // PI
                })
                .map(p -> {
                    boolean assigned = mine.contains(p.getId());
                    String name = switch (me.getRole()) {
                        case PRINCIPAL_INVESTIGATOR -> p.getEnrolledAt() != null ? p.getUser().getName() : null;
                        case RESEARCHER -> assigned ? p.getUser().getName() : null;
                        default -> null;   // 관제 담당자는 실명 없음
                    };
                    return new PatientView(p.getId(), p.getResearchCode(), name, p.getAmputationSide(),
                            p.getEnrolledAt() != null, p.getEnrolledAt(), p.isResearchConsent(),
                            p.isParticipating(), assigned);
                })
                .toList();
    }

    /** 연구 등록 (연구책임자만) */
    @PostMapping("/api/patients/{id}/enroll")
    @Transactional
    public void enroll(@PathVariable Long id) {
        Patient p = patientRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 환자입니다. id=" + id));
        p.enroll();
    }

    /** 연구자 배정 (연구책임자만). 규칙은 기존 ResearcherAssignmentService.assign 그대로 */
    @PostMapping("/api/patients/{id}/assignments")
    public Long assign(@PathVariable Long id, @RequestBody AssignRequest req) {
        return assignmentService.assign(id, req.researcherId());
    }

    @GetMapping("/api/researchers")
    public List<ResearcherView> researchers() {
        return userRepository.findAllByRole(Role.RESEARCHER).stream()
                .map(u -> new ResearcherView(u.getId(), u.getName()))
                .toList();
    }
}
