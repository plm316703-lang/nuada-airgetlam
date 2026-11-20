package io.github.plm316703.backend.patient;

import io.github.plm316703.backend.auth.CurrentUser;
import io.github.plm316703.backend.user.Role;
import io.github.plm316703.backend.user.User;
import io.github.plm316703.backend.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
    private final PatientGuardianRepository guardianRepository;
    private final InvitationService invitationService;
    private final CurrentUser currentUser;

    /**
     * userId·invitePending·guardians는 연구책임자에게만 채운다 (다시 초대·보호자 관리용).
     * 다른 역할에는 null/빈 목록 → 관제 담당자·연구자가 보호자 실명을 보지 않게.
     */
    public record PatientView(Long id, String researchCode, String displayName, AmputationSide amputationSide,
                              boolean enrolled, LocalDateTime enrolledAt, boolean consent, boolean participating,
                              boolean assignedToMe, Long userId, boolean invitePending, List<GuardianView> guardians) {}

    public record GuardianView(Long userId, String name, GuardianRelation relation, boolean invitePending) {}

    public record ResearcherView(Long id, String name) {}

    public record AssignRequest(@NotNull Long researcherId) {}

    public record RegisterRequest(@NotBlank @Size(max = 50) String name,
                                  @NotBlank @Email @Size(max = 100) String email,
                                  @NotNull AmputationSide amputationSide) {}

    public record GuardianRequest(@NotBlank @Size(max = 50) String name,
                                  @NotBlank @Email @Size(max = 100) String email,
                                  @NotNull GuardianRelation relation,
                                  boolean identityChecked) {}

    /** token은 이 응답에만 담긴다. 화면이 링크로 만들어 "지금 한 번만 보입니다"라고 안내 (null이면 기존 보호자를 연결만 함) */
    public record InviteView(Long userId, String email, Role role, String token, LocalDateTime expiresAt) {
        static InviteView of(InvitationService.Invited i) {
            return new InviteView(i.user().getId(), i.user().getEmail(), i.user().getRole(), i.token(), i.expiresAt());
        }
    }

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
                    boolean pi = me.getRole() == Role.PRINCIPAL_INVESTIGATOR;
                    List<GuardianView> guardians = pi
                            ? guardianRepository.findAllByPatient(p).stream()
                                .map(l -> new GuardianView(l.getGuardian().getId(), l.getGuardian().getName(),
                                        l.getRelation(), invitationService.isPending(l.getGuardian())))
                                .toList()
                            : List.of();
                    return new PatientView(p.getId(), p.getResearchCode(), name, p.getAmputationSide(),
                            p.getEnrolledAt() != null, p.getEnrolledAt(), p.isResearchConsent(),
                            p.isParticipating(), assigned,
                            pi ? p.getUser().getId() : null,
                            pi && invitationService.isPending(p.getUser()),
                            guardians);
                })
                .toList();
    }

    /** 환자 등록 + 초대 링크 (연구책임자만, SecurityConfig의 /api/patients/** 규칙) */
    @PostMapping("/api/patients")
    public InviteView register(@Valid @RequestBody RegisterRequest req, Authentication auth, HttpServletRequest http) {
        return InviteView.of(invitationService.registerPatient(req.name(), req.email(), req.amputationSide(),
                currentUser.get(auth), http.getRemoteAddr()));
    }

    /** 보호자 초대 (신분 확인 체크 필수) */
    @PostMapping("/api/patients/{id}/guardians")
    public InviteView inviteGuardian(@PathVariable Long id, @Valid @RequestBody GuardianRequest req,
                                     Authentication auth, HttpServletRequest http) {
        return InviteView.of(invitationService.inviteGuardian(id, req.name(), req.email(), req.relation(),
                req.identityChecked(), currentUser.get(auth), http.getRemoteAddr()));
    }

    /** 다시 초대 (환자·보호자 계정 번호로). 이전 링크는 무효 */
    @PostMapping("/api/patients/invitations/{userId}")
    public InviteView reinvite(@PathVariable Long userId, Authentication auth, HttpServletRequest http) {
        return InviteView.of(invitationService.reinvite(userId, currentUser.get(auth), http.getRemoteAddr()));
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
