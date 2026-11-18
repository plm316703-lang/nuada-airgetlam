package io.github.plm316703.backend.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 페일세이프 판단 담당자.
 * Python이 보낸 판단(decision)과, 관문이 자기 기준값으로 다시 계산한 판단이
 * 다르면 DECISION_MISMATCH 로 거부한다. → "둘 다 MOVE일 때만 MOVE"
 */
@Component
public class DecisionGate {

    // 관문 자신의 기준값 (메시지의 threshold 는 믿지 않음)
    private final double moveThreshold;

    /**
     * @Value: application.yaml 의 gateway.move-threshold 값을 생성자로 받음
     * 설정이 없으면 Spring이 시작 자체를 실패함 → 기준값 없이 돌아가는 일이 없음 (API 키와 같은 원칙)
     */
    public DecisionGate(@Value("${gateway.move-threshold}") double moveThreshold) {
        // 설정값이 말이 안 되면(예: 1.5, -0.1) 시작 단계에서 바로 멈춤
        if (!(moveThreshold >= 0.0 && moveThreshold <= 1.0)) {
            throw new IllegalStateException("gateway.move-threshold 는 0~1 이어야 함: " + moveThreshold);
        }
        this.moveThreshold = moveThreshold;
    }

    public double moveThreshold() {
        return moveThreshold;
    }

    public Optional<MessageValidator.Rejection> check(ClassifierMessage m) {
        // 관문이 직접 다시 계산한 판단 (12-5b를 통과했으므로 confidence는 null 아님, 0~1 보장)
        String gatewayDecision = m.confidence() >= moveThreshold ? "MOVE" : "ABSTAIN";

        // Python 판단과 다르면 거부 → 어느 쪽으로 달라도 최종적으로 움직이지 않음
        if (!gatewayDecision.equals(m.decision())) {
            return Optional.of(new MessageValidator.Rejection(
                    RejectReason.DECISION_MISMATCH,
                    "python=" + m.decision() + " gateway=" + gatewayDecision
                            + " conf=" + m.confidence() + " th=" + moveThreshold));
        }
        return Optional.empty();   // 의견 일치 → msg.decision()이 최종 판단
    }
}