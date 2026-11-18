package io.github.plm316703.backend.patient;

import io.github.plm316703.backend.arm.ArmState;
import io.github.plm316703.backend.arm.ArmSupervisor;
import io.github.plm316703.backend.auth.CurrentUser;
import io.github.plm316703.backend.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 환자 본인 / 보호자 화면 API (웨이터).
 *  - 환자: 내 참여 상태, 동의·철회 (본인 결정이 항상 우선), 내 세션이면 로봇팔 상태
 *  - 보호자: 연결된 환자의 참여·로봇팔 상태 보기 (대리 동의는 발표 이후)
 */
@RestController
@RequiredArgsConstructor
public class MeController {

    private final PatientRepository patientRepository;
    private final PatientGuardianRepository guardianRepository;
    private final ResearchConsentService consentService;
    private final ArmSupervisor arm;
    private final CurrentUser currentUser;

    /** 내 세션일 때만 채워지는 로봇팔 상태 (다른 환자의 상태는 보이지 않음) */
    public record ArmStatus(ArmState state, double angle, String stopReason) {}

    public record MyStatus(String name, String researchCode, AmputationSide amputationSide, boolean enrolled,
                           boolean consent, LocalDateTime consentedAt, LocalDateTime consentWithdrawnAt,
                           boolean participating, ArmStatus arm) {}

    public record GuardedPatient(String name, String researchCode, GuardianRelation relation,
                                 boolean participating, ArmStatus arm) {}

    // ───────── 환자 ─────────

    @GetMapping("/api/me/patient")
    @Transactional(readOnly = true)
    public MyStatus myStatus(Authentication auth) {
        Patient p = myPatient(auth);
        return new MyStatus(p.getUser().getName(), p.getResearchCode(), p.getAmputationSide(),
                p.getEnrolledAt() != null, p.isResearchConsent(), p.getConsentedAt(), p.getConsentWithdrawnAt(),
                p.isParticipating(), armIfMine(p.getId()));
    }

    @PostMapping("/api/me/patient/consent")
    public void consent(Authentication auth) {
        consentService.giveConsent(myPatient(auth).getId());
    }

    /** 철회: 화면에서 "연구자 배정 등 연구 데이터 삭제 + 로봇팔 사용 중단" 안내 후 호출 */
    @PostMapping("/api/me/patient/withdraw")
    public Map<String, Long> withdraw(Authentication auth) {
        long deleted = consentService.withdraw(myPatient(auth).getId());
        return Map.of("deletedAssignments", deleted);
    }

    // ───────── 보호자 ─────────

    @GetMapping("/api/me/guardian/patients")
    @Transactional(readOnly = true)
    public List<GuardedPatient> guardedPatients(Authentication auth) {
        User me = currentUser.get(auth);
        return guardianRepository.findAllByGuardian(me).stream()
                .map(link -> {
                    Patient p = link.getPatient();
                    return new GuardedPatient(p.getUser().getName(), p.getResearchCode(), link.getRelation(),
                            p.isParticipating(), armIfMine(p.getId()));
                })
                .toList();
    }

    private Patient myPatient(Authentication auth) {
        User me = currentUser.get(auth);
        return patientRepository.findByUser(me)
                .orElseThrow(() -> new IllegalStateException("환자 정보가 없는 계정입니다."));
    }

    private ArmStatus armIfMine(Long patientId) {
        var s = arm.snapshot(Instant.now());
        if (s.patientId() == null || !s.patientId().equals(patientId)) return null;
        return new ArmStatus(s.state(), s.angle(), s.stopReason());
    }
}
