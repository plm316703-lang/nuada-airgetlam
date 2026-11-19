package io.github.plm316703.backend.gateway;

import io.github.plm316703.backend.arm.ArmSupervisor;
import io.github.plm316703.backend.dashboard.DashboardHub;
import io.github.plm316703.backend.dashboard.Views;
import io.github.plm316703.backend.patient.Patient;
import io.github.plm316703.backend.patient.PatientRepository;
import io.github.plm316703.backend.record.*;
import io.github.plm316703.backend.session.MeasurementSession;
import io.github.plm316703.backend.session.SessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Optional;

/**
 * 관문(관제탑)의 본체: 판별 메시지 하나를 받아 "움직여도 되는가"를 끝까지 판단한다.
 *
 *   ① 형식(JSON) → ② 칸·범위·버전 → ③ 순서(seq) → ④ 오래된 명령(timestamp)
 *   → ⑤ 열린 세션·피험자 일치 → ⑥ 참여 중인 환자(등록+동의) → ⑦ 신호 품질 → ⑧ 판단 일치(확신도)
 *
 *  - 하나라도 걸리면 BLOCKED: 팔은 그 자리에서 정지, 차단 기록, (장비 오류면) RAG 안내 요청
 *  - 다 통과하면: ABSTAIN(정상 보류) / MOVE(실행) / HELD(정지 중이라 실행 안 함)
 *  - 자동 재개는 없다. 정지된 팔은 사람이 재개 버튼을 눌러야 다시 움직인다.
 */
@Slf4j
@Service
public class GateService {

    private final ObjectMapper objectMapper;
    private final MessageValidator validator;
    private final SequenceTracker sequenceTracker;
    private final FreshnessChecker freshnessChecker;
    private final DecisionGate decisionGate;
    private final ArmSupervisor arm;
    private final SessionRepository sessionRepository;
    private final PatientRepository patientRepository;
    private final GateDecisionRepository decisionRepository;
    private final ControlEventRepository controlRepository;
    private final DeviceErrorService errorService;
    private final DashboardHub hub;
    private final double minSignalQuality;

    public GateService(ObjectMapper objectMapper, MessageValidator validator, SequenceTracker sequenceTracker,
                       FreshnessChecker freshnessChecker, DecisionGate decisionGate, ArmSupervisor arm,
                       SessionRepository sessionRepository, PatientRepository patientRepository,
                       GateDecisionRepository decisionRepository, ControlEventRepository controlRepository,
                       DeviceErrorService errorService, DashboardHub hub,
                       @Value("${gateway.min-signal-quality}") double minSignalQuality) {
        if (!(minSignalQuality >= 0.0 && minSignalQuality <= 1.0)) {
            throw new IllegalStateException("gateway.min-signal-quality 는 0~1 이어야 함: " + minSignalQuality);
        }
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.sequenceTracker = sequenceTracker;
        this.freshnessChecker = freshnessChecker;
        this.decisionGate = decisionGate;
        this.arm = arm;
        this.sessionRepository = sessionRepository;
        this.patientRepository = patientRepository;
        this.decisionRepository = decisionRepository;
        this.controlRepository = controlRepository;
        this.errorService = errorService;
        this.hub = hub;
        this.minSignalQuality = minSignalQuality;
    }

    public double minSignalQuality() { return minSignalQuality; }

    /** WebSocket 담당자가 메시지를 받을 때마다 호출 */
    public void handle(String connectionId, String payload) {
        Instant now = Instant.now();
        arm.markReceived(now);   // 내용이 엉망이어도 "연결은 살아 있음" (워치독용)

        // ── ① 형식 ──────────────────────────────────────────
        ClassifierMessage msg;
        try {
            msg = objectMapper.readValue(payload, ClassifierMessage.class);
        } catch (JacksonException e) {
            // 페일세이프: 읽을 수 없는 메시지는 절대 MOVE로 이어지지 않게 여기서 끝냄
            block(null, connectionId, RejectReason.INVALID_FORMAT, e.getOriginalMessage(), ErrorType.INVALID_MESSAGE);
            return;
        }

        // ── ② 칸·범위·버전 ───────────────────────────────────
        Optional<MessageValidator.Rejection> r = validator.validate(msg);
        if (r.isPresent()) { block(msg, connectionId, r.get(), ErrorType.INVALID_MESSAGE); return; }

        // ── ③ 순서 (중복·옛 메시지) ───────────────────────────
        r = sequenceTracker.check(connectionId, msg.seq());
        if (r.isPresent()) { block(msg, connectionId, r.get(), ErrorType.INVALID_MESSAGE); return; }

        // ── ④ 오래된 명령 ────────────────────────────────────
        r = freshnessChecker.check(msg.timestamp(), now);
        if (r.isPresent()) {
            ErrorType type = r.get().reason() == RejectReason.STALE_COMMAND ? ErrorType.STALE_COMMAND : ErrorType.INVALID_MESSAGE;
            block(msg, connectionId, r.get(), type);
            return;
        }

        // ── ⑤ 열린 세션 + 피험자 일치 ─────────────────────────
        Optional<MeasurementSession> session = sessionRepository.findFirstByEndedAtIsNullOrderByStartedAtDesc();
        if (session.isEmpty()) {
            block(msg, connectionId, new MessageValidator.Rejection(RejectReason.NO_ACTIVE_SESSION,
                    "관제 화면에서 세션을 먼저 시작해야 함"), null);
            return;
        }
        if (session.get().getDatasetSubject() != msg.subject()) {
            block(msg, connectionId, new MessageValidator.Rejection(RejectReason.SUBJECT_MISMATCH,
                    "session=" + session.get().getDatasetSubject() + " message=" + msg.subject()), null);
            return;
        }

        // ── ⑥ 참여 중인 환자 (매번 DB에서 다시 확인: 방금 철회했을 수 있음) ─────
        Patient patient = patientRepository.findById(session.get().getPatient().getId()).orElseThrow();
        if (!patient.isParticipating()) {
            block(msg, connectionId, new MessageValidator.Rejection(RejectReason.NOT_PARTICIPATING,
                    patient.getResearchCode() + " 등록=" + (patient.getEnrolledAt() != null)
                            + " 동의=" + patient.isResearchConsent()), null);
            return;
        }

        // ── ⑦ 신호 품질 ──────────────────────────────────────
        ClassifierMessage.Signal signal = msg.signal();
        if (signal.quality() < minSignalQuality) {
            ErrorType type = signal.issue() != null ? ErrorType.valueOf(signal.issue()) : ErrorType.SIGNAL_QUALITY_LOW;
            String detail = "quality=" + signal.quality() + " min=" + minSignalQuality
                    + (signal.issue() == null ? "" : " issue=" + signal.issue())
                    + (signal.badChannels() == null || signal.badChannels().isEmpty() ? "" : " channels=" + signal.badChannels());
            block(msg, connectionId, new MessageValidator.Rejection(RejectReason.SIGNAL_QUALITY, detail), type);
            return;
        }

        // ── ⑧ 판단 일치 (확신도 기준을 관문이 다시 계산) ──────────
        r = decisionGate.check(msg);
        if (r.isPresent()) { block(msg, connectionId, r.get(), ErrorType.DECISION_MISMATCH); return; }

        // ── 모든 검사 통과 ───────────────────────────────────
        arm.markClean();
        GateResult result;
        if ("ABSTAIN".equals(msg.decision())) {
            result = GateResult.ABSTAIN;                       // 정상 보류 (확신도 부족)
        } else {
            result = arm.applyMove(msg.intent()) ? GateResult.MOVE : GateResult.HELD;
        }
        GateDecision saved = decisionRepository.save(decisionOf(connectionId, msg, result, null, null));   // ← connectionId 추가
        log.info("판정 id={} seq={} intent={} conf={} result={}", connectionId, msg.seq(), msg.intent(),
                msg.confidence(), result);
        publish(saved);
    }

    // ───────────────────── 차단 처리 ─────────────────────

    private void block(ClassifierMessage msg, String connectionId, RejectReason reason, String detail, ErrorType errorType) {
        block(msg, connectionId, new MessageValidator.Rejection(reason, detail), errorType);
    }

    /**
     * 차단: ① 팔 정지 ② 판단 기록(BLOCKED) ③ 정지 기록 ④ 장비 오류면 오류 기록 + RAG ⑤ 화면에 알림
     */
    private void block(ClassifierMessage msg, String connectionId, MessageValidator.Rejection rejection, ErrorType errorType) {
        log.warn("거부 id={} seq={} reason={} detail={}", connectionId, msg == null ? null : msg.seq(),
                rejection.reason(), rejection.detail());

        boolean stoppedNow = arm.stop(rejection.reason().name());
        Long sessionId = arm.snapshot(Instant.now()).sessionId();

        GateDecision saved = decisionRepository.save(decisionOf(connectionId, msg, GateResult.BLOCKED, rejection.reason(), rejection.detail()));   // ← connectionId 추가

        if (stoppedNow) {
            ControlEvent stop = controlRepository.save(new ControlEvent(sessionId, ControlEvent.Type.STOP, null,
                    rejection.reason() + ": " + rejection.detail()));
            hub.broadcast("control", Views.ControlView.of(stop));
        }
        // 장비 오류는 세션이 있을 때만 기록 (세션 없이 들어온 메시지는 판단 기록만)
        if (errorType != null && sessionId != null) {
            Double quality = msg == null || msg.signal() == null ? null : msg.signal().quality();
            boolean injected = msg != null && msg.scenario() != null;
            var reported = errorService.report(sessionId, errorType,
                    rejection.reason() + ": " + rejection.detail(), quality, injected);
            hub.broadcast("error", Views.ErrorView.of(reported.error()));
        }
        publish(saved);
    }

    private void publish(GateDecision saved) {
        hub.broadcast("decision", Views.DecisionView.of(saved));
        hub.broadcast("arm", Views.ArmView.of(arm.snapshot(Instant.now())));
    }

    private GateDecision decisionOf(String connectionId,                       // ← 추가: 어느 연결에서 온 메시지인지
                                    ClassifierMessage m, GateResult result, RejectReason reason, String detail) {
        var s = arm.snapshot(Instant.now());
        var b = GateDecision.builder()
                .sessionId(s.sessionId())
                .wsConnectionId(connectionId)                                  // ← 추가: 메시지가 깨져도 연결 id는 항상 알 수 있음
                .result(result)
                .reason(reason)
                .detail(detail)
                .armAngleAfter(s.angle());
        if (m != null) {
            b.seq(m.seq()).subject(m.subject()).commandTimestamp(m.timestamp()).intent(m.intent()).confidence(m.confidence())
                    .pythonDecision(m.decision()).trueLabel(m.trueLabel()).scenario(m.scenario());
            if (m.signal() != null) {
                b.signalQuality(m.signal().quality()).signalIssue(m.signal().issue());
            }
        }
        return b.build();
    }
}
