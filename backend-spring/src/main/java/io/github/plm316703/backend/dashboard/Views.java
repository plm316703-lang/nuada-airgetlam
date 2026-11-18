package io.github.plm316703.backend.dashboard;

import io.github.plm316703.backend.arm.ArmState;
import io.github.plm316703.backend.arm.ArmSupervisor;
import io.github.plm316703.backend.gateway.RejectReason;
import io.github.plm316703.backend.record.ControlEvent;
import io.github.plm316703.backend.record.DeviceError;
import io.github.plm316703.backend.record.ErrorType;
import io.github.plm316703.backend.record.GateDecision;
import io.github.plm316703.backend.record.GateResult;
import io.github.plm316703.backend.session.MeasurementSession;
import io.github.plm316703.backend.session.SourceType;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * 화면으로 내보내는 데이터 모양(DTO) 모음.
 * 엔티티를 그대로 보내지 않는 이유:
 *  - 보여 줄 칸만 고를 수 있다 (실명·이메일 같은 개인정보가 섞여 나가지 않게)
 *  - 지연 로딩(LAZY) 관계를 트랜잭션 밖에서 건드려 오류 나는 것을 막는다
 */
public final class Views {

    private Views() {}

    public record ArmView(ArmState state, double angle, String stopReason, Instant stoppedAt,
                          Long sessionId, String patientCode, Integer datasetSubject,
                          Long lastMessageAgoMs, int consecutiveClean, int requiredClean) {
        public static ArmView of(ArmSupervisor.Snapshot s) {
            return new ArmView(s.state(), s.angle(), s.stopReason(), s.stoppedAt(), s.sessionId(),
                    s.patientCode(), s.datasetSubject(), s.lastMessageAgoMs(),
                    s.consecutiveClean(), s.requiredClean());
        }
    }

    public record DecisionView(Long id, Long sessionId, Long seq, LocalDateTime receivedAt, String intent,
                               Double confidence, Double signalQuality, String signalIssue,
                               String pythonDecision, GateResult result, RejectReason reason, String detail,
                               Double armAngleAfter, String trueLabel, String scenario) {
        public static DecisionView of(GateDecision d) {
            return new DecisionView(d.getId(), d.getSessionId(), d.getSeq(), d.getReceivedAt(), d.getIntent(),
                    d.getConfidence(), d.getSignalQuality(), d.getSignalIssue(), d.getPythonDecision(),
                    d.getResult(), d.getReason(), d.getDetail(), d.getArmAngleAfter(), d.getTrueLabel(),
                    d.getScenario());
        }
    }

    public record ErrorView(Long id, Long sessionId, ErrorType errorType, String detail, Double signalQuality,
                            boolean injected, LocalDateTime occurredAt, LocalDateTime lastSeenAt,
                            int occurrenceCount, LocalDateTime resolvedAt, DeviceError.RagStatus ragStatus,
                            String ragAnswer, String ragSourcesJson, Boolean ragEvidenceSufficient) {
        public static ErrorView of(DeviceError e) {
            return new ErrorView(e.getId(), e.getSessionId(), e.getErrorType(), e.getDetail(),
                    e.getSignalQuality(), e.isInjected(), e.getOccurredAt(), e.getLastSeenAt(),
                    e.getOccurrenceCount(), e.getResolvedAt(), e.getRagStatus(), e.getRagAnswer(),
                    e.getRagSources(), e.getRagEvidenceSufficient());
        }
    }

    /** actor 이름은 관제 직원(운영자·책임자)이므로 표시한다. 시스템 자동 동작이면 "시스템" */
    public record ControlView(Long id, Long sessionId, ControlEvent.Type type, String actor,
                              String detail, LocalDateTime occurredAt) {
        public static ControlView of(ControlEvent c) {
            String actor = c.getActor() == null ? "시스템"
                    : c.getActor().getName() + " (" + c.getActor().getRole() + ")";
            return new ControlView(c.getId(), c.getSessionId(), c.getType(), actor, c.getDetail(), c.getOccurredAt());
        }
    }

    /** 세션 정보. 환자는 익명 연구 코드로만 표시 (관제 담당자는 실명을 보지 않음) */
    public record SessionView(Long id, String patientCode, String deviceCode, SourceType sourceType,
                              int datasetSubject, LocalDateTime startedAt, LocalDateTime endedAt) {
        public static SessionView of(MeasurementSession s) {
            return new SessionView(s.getId(), s.getPatient().getResearchCode(), s.getDeviceCode(),
                    s.getSourceType(), s.getDatasetSubject(), s.getStartedAt(), s.getEndedAt());
        }
    }
}
