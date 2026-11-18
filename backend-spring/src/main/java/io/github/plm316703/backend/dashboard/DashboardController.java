package io.github.plm316703.backend.dashboard;

import io.github.plm316703.backend.arm.ArmControlService;
import io.github.plm316703.backend.arm.ArmSupervisor;
import io.github.plm316703.backend.auth.CurrentUser;
import io.github.plm316703.backend.gateway.DecisionGate;
import io.github.plm316703.backend.gateway.FreshnessChecker;
import io.github.plm316703.backend.gateway.GateService;
import io.github.plm316703.backend.record.ControlEventRepository;
import io.github.plm316703.backend.record.DeviceErrorRepository;
import io.github.plm316703.backend.record.GateDecisionRepository;
import io.github.plm316703.backend.session.SessionRepository;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/**
 * 관제 화면 API (웨이터).
 *  GET  /api/dashboard/state          : 첫 화면 전체 상태 (보기 역할)
 *  POST /api/dashboard/session/start  : 세션 시작 (조작 역할)
 *  POST /api/dashboard/session/end    : 세션 종료
 *  POST /api/dashboard/arm/resume     : 수동 재개 (서버가 조건 재검사)
 *  POST /api/dashboard/arm/reset      : 초기화 (기본 자세)
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final ArmSupervisor arm;
    private final ArmControlService control;
    private final SessionRepository sessionRepository;
    private final GateDecisionRepository decisionRepository;
    private final DeviceErrorRepository errorRepository;
    private final ControlEventRepository controlRepository;
    private final GateService gateService;
    private final FreshnessChecker freshnessChecker;
    private final DecisionGate decisionGate;
    private final CurrentUser currentUser;

    /** 화면에 표시하는 기준값 (설정 파일 값 그대로) */
    public record GateConfig(double moveThreshold, double minSignalQuality, long staleCommandMs,
                             long watchdogStopMs, long watchdogResetMs, int resumeRequiredClean) {}

    public record State(Views.ArmView arm, Views.SessionView session, List<Views.DecisionView> decisions,
                        List<Views.ErrorView> errors, List<Views.ControlView> controls, GateConfig config) {}

    public record StartRequest(@NotNull Long patientId) {}

    @GetMapping("/state")
    @Transactional(readOnly = true)
    public State state() {
        var session = sessionRepository.findFirstByEndedAtIsNullOrderByStartedAtDesc()
                .map(Views.SessionView::of).orElse(null);
        var config = new GateConfig(decisionGate.moveThreshold(), gateService.minSignalQuality(),
                freshnessChecker.staleMs(), arm.watchdogStopMs(), arm.watchdogResetMs(), arm.requiredClean());
        return new State(
                Views.ArmView.of(arm.snapshot(Instant.now())),
                session,
                decisionRepository.findTop50ByOrderByIdDesc().stream().map(Views.DecisionView::of).toList(),
                errorRepository.findTop20ByOrderByIdDesc().stream().map(Views.ErrorView::of).toList(),
                controlRepository.findTop20ByOrderByIdDesc().stream().map(Views.ControlView::of).toList(),
                config);
    }

    @PostMapping("/session/start")
    public Views.ArmView start(@RequestBody StartRequest req, Authentication auth) {
        control.startSession(req.patientId(), currentUser.get(auth));
        return Views.ArmView.of(arm.snapshot(Instant.now()));
    }

    @PostMapping("/session/end")
    public Views.ArmView end(Authentication auth) {
        control.endSession(currentUser.get(auth));
        return Views.ArmView.of(arm.snapshot(Instant.now()));
    }

    @PostMapping("/arm/resume")
    public ArmControlService.ResumeResult resume(Authentication auth) {
        return control.resume(currentUser.get(auth));
    }

    @PostMapping("/arm/reset")
    public Views.ArmView reset(Authentication auth) {
        control.reset(currentUser.get(auth));
        return Views.ArmView.of(arm.snapshot(Instant.now()));
    }
}
