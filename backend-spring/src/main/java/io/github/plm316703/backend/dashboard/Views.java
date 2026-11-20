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
import java.util.Arrays;
import java.util.List;

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

    /**
     * badChannels: DB에는 "C3,Cz,C4" 글자로 저장하지만 화면에는 목록으로 보낸다.
     * 화면 JS가 쉼표로 쪼갤 필요 없이 바로 채널 지도에 칠할 수 있게 하기 위해서다.
     * 실시간 전송과 "최근 판단 50개" 불러오기가 모두 of()를 거치므로, 새로고침 직후에도 지도가 채워진다.
     */
    public record DecisionView(Long id, Long sessionId, Long seq, LocalDateTime receivedAt, String intent,
                               Double confidence, Double signalQuality, String signalIssue,
                               List<String> badChannels,
                               String pythonDecision, GateResult result, RejectReason reason, String detail,
                               Double armAngleAfter, String trueLabel, String scenario) {
        public static DecisionView of(GateDecision d) {
            return new DecisionView(d.getId(), d.getSessionId(), d.getSeq(), d.getReceivedAt(), d.getIntent(),
                    d.getConfidence(), d.getSignalQuality(), d.getSignalIssue(), splitChannels(d.getBadChannels()),
                    d.getPythonDecision(), d.getResult(), d.getReason(), d.getDetail(), d.getArmAngleAfter(),
                    d.getTrueLabel(), d.getScenario());
        }

        /**
         * 문제 채널이 없으면 null 대신 빈 목록을 보낸다.
         * 화면에서 null 검사 없이 "목록에 있으면 빨강, 없으면 초록"으로만 칠하면 되게 하려는 것이다.
         * 쉼표 사이 공백이나 빈 칸은 걸러 낸다 (200자 자르기로 마지막 이름이 잘렸을 때 등).
         */
        private static List<String> splitChannels(String joined) {
            if (joined == null || joined.isBlank()) return List.of();
            return Arrays.stream(joined.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
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
