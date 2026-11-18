package io.github.plm316703.backend.arm;

import io.github.plm316703.backend.dashboard.DashboardHub;
import io.github.plm316703.backend.dashboard.Views;
import io.github.plm316703.backend.patient.Patient;
import io.github.plm316703.backend.patient.PatientRepository;
import io.github.plm316703.backend.patient.ResearchConsentService;
import io.github.plm316703.backend.record.ControlEvent;
import io.github.plm316703.backend.record.ControlEventRepository;
import io.github.plm316703.backend.record.DeviceErrorService;
import io.github.plm316703.backend.session.MeasurementSession;
import io.github.plm316703.backend.session.SessionRepository;
import io.github.plm316703.backend.session.SourceType;
import io.github.plm316703.backend.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.List;

/**
 * 사람이 하는 관제 동작 (주방장): 세션 시작·종료, 수동 재개, 초기화.
 * 모든 동작은 control_events에 "누가 언제 무엇을" 기록하고 관제 화면에 알린다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArmControlService {

    public static final String DEFAULT_DEVICE = "EEG-PLAYBACK-01";
    public static final int DEFAULT_SUBJECT = 3;   // 저장된 모델(model_s3)의 피험자

    private final ArmSupervisor arm;
    private final SessionRepository sessionRepository;
    private final PatientRepository patientRepository;
    private final ControlEventRepository controlRepository;
    private final DeviceErrorService errorService;
    private final DashboardHub hub;

    public record ResumeResult(boolean resumed, List<String> blockers) {}

    /** 세션 시작: 참여 중인 환자만, 동시에 하나만. 팔은 정지 상태로 시작한다. */
    @Transactional
    public MeasurementSession startSession(Long patientId, User actor) {
        if (sessionRepository.findFirstByEndedAtIsNullOrderByStartedAtDesc().isPresent()) {
            throw new IllegalStateException("이미 진행 중인 세션이 있습니다. 먼저 종료하세요.");
        }
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 환자입니다. id=" + patientId));
        if (!patient.isParticipating()) {
            throw new IllegalStateException("참여 중인 환자(연구 등록 + 동의)만 세션을 시작할 수 있습니다.");
        }
        MeasurementSession session = sessionRepository.save(
                new MeasurementSession(patient, DEFAULT_DEVICE, SourceType.PLAYBACK, DEFAULT_SUBJECT, actor));
        arm.startSession(session.getId(), patient.getId(), patient.getResearchCode(), DEFAULT_SUBJECT);
        record(session.getId(), ControlEvent.Type.SESSION_START, actor,
                patient.getResearchCode() + " / " + DEFAULT_DEVICE + " / PLAYBACK subject " + DEFAULT_SUBJECT);
        broadcastArm();
        return session;
    }

    @Transactional
    public void endSession(User actor) {
        MeasurementSession session = sessionRepository.findFirstByEndedAtIsNullOrderByStartedAtDesc()
                .orElseThrow(() -> new IllegalStateException("진행 중인 세션이 없습니다."));
        session.end();
        arm.endSession();
        record(session.getId(), ControlEvent.Type.SESSION_END, actor, null);
        broadcastArm();
    }

    /**
     * 수동 재개. 화면 버튼을 믿지 않고 서버가 지금 조건을 다시 검사한다.
     * 조건: 정지 상태 + 참여 중 환자 + 데이터가 계속 들어옴 + 연속 정상 N개
     */
    @Transactional
    public ResumeResult resume(User actor) {
        var snap = arm.snapshot(Instant.now());
        if (snap.sessionId() == null) {
            throw new IllegalStateException("진행 중인 세션이 없습니다.");
        }
        boolean participating = patientRepository.findById(snap.patientId())
                .map(Patient::isParticipating).orElse(false);

        List<String> blockers;
        // 검사와 재개 사이에 다른 스레드(판별 메시지)가 끼어들지 않도록 arm 객체를 잠근 채로 처리
        synchronized (arm) {
            blockers = arm.resumeBlockers(participating, Instant.now());
            if (blockers.isEmpty()) {
                arm.resume();
            }
        }
        if (!blockers.isEmpty()) {
            record(snap.sessionId(), ControlEvent.Type.RESUME_DENIED, actor, String.join(" / ", blockers));
            return new ResumeResult(false, blockers);
        }
        var resolved = errorService.resolveOpen(snap.sessionId(), actor);
        record(snap.sessionId(), ControlEvent.Type.RESUME, actor, "해결 처리한 오류 " + resolved.size() + "건");
        resolved.forEach(e -> hub.broadcast("error", Views.ErrorView.of(e)));
        broadcastArm();
        return new ResumeResult(true, List.of());
    }

    @Transactional
    public void reset(User actor) {
        var snap = arm.snapshot(Instant.now());
        if (snap.sessionId() == null) {
            throw new IllegalStateException("진행 중인 세션이 없습니다.");
        }
        arm.reset();
        record(snap.sessionId(), ControlEvent.Type.RESET, actor, "기본 자세 " + ArmSupervisor.HOME_ANGLE + "°");
        broadcastArm();
    }

    /** 연구 동의 철회 → 그 환자가 세션 중이면 즉시 정지 (다음 메시지를 기다리지 않음) */
    @TransactionalEventListener(fallbackExecution = true)
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void onConsentWithdrawn(ResearchConsentService.ConsentWithdrawnEvent event) {
        var snap = arm.snapshot(Instant.now());
        if (snap.patientId() != null && snap.patientId().equals(event.patientId())) {
            arm.stop("NOT_PARTICIPATING: 연구 동의 철회");
            record(snap.sessionId(), ControlEvent.Type.STOP, null, "연구 동의 철회로 로봇팔 사용 중단");
            broadcastArm();
            log.warn("동의 철회로 정지 patientId={}", event.patientId());
        }
    }

    /** 서버를 다시 켰을 때 DB에 열린 세션이 있으면 정지 상태로 이어받는다 (자동 재개 없음) */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void restoreActiveSession() {
        sessionRepository.findFirstByEndedAtIsNullOrderByStartedAtDesc().ifPresent(s -> {
            arm.startSession(s.getId(), s.getPatient().getId(), s.getPatient().getResearchCode(), s.getDatasetSubject());
            log.info("열린 세션 이어받음 sessionId={} (정지 상태)", s.getId());
        });
    }

    private void record(Long sessionId, ControlEvent.Type type, User actor, String detail) {
        ControlEvent saved = controlRepository.save(new ControlEvent(sessionId, type, actor, detail));
        hub.broadcast("control", Views.ControlView.of(saved));
    }

    private void broadcastArm() {
        hub.broadcast("arm", Views.ArmView.of(arm.snapshot(Instant.now())));
    }
}
