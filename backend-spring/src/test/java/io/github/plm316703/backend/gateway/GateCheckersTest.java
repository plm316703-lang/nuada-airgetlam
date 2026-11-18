package io.github.plm316703.backend.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 관문 검사 담당자 단위 테스트 (메시지 규격 v2, 오래된 명령, 판단 일치)
 */
class GateCheckersTest {

    final MessageValidator validator = new MessageValidator();
    final FreshnessChecker freshness = new FreshnessChecker(2000);
    final DecisionGate gate = new DecisionGate(0.75);

    private ClassifierMessage msg(Integer version, Double conf, String decision, ClassifierMessage.Signal signal) {
        return new ClassifierMessage(version, "classification", 1L, "2026-11-17T21:00:00.000+09:00", 3,
                "left_hand", conf, 0.75, decision, signal, null, "left_hand");
    }

    private ClassifierMessage.Signal ok() {
        return new ClassifierMessage.Signal(0.98, null, List.of());
    }

    @Test
    @DisplayName("정상 v2 메시지는 통과")
    void validPasses() {
        assertThat(validator.validate(msg(2, 0.9, "MOVE", ok()))).isEmpty();
    }

    @Test
    @DisplayName("예전 v1 메시지는 UNSUPPORTED_VERSION (신호 품질이 없는 메시지는 안전하게 거부)")
    void v1Rejected() {
        assertThat(validator.validate(msg(1, 0.9, "MOVE", ok())))
                .hasValueSatisfying(r -> assertThat(r.reason()).isEqualTo(RejectReason.UNSUPPORTED_VERSION));
    }

    @Test
    @DisplayName("signal 칸이 없거나, 품질이 범위 밖이거나, 모르는 issue면 INVALID_FIELD")
    void signalValidated() {
        assertThat(validator.validate(msg(2, 0.9, "MOVE", null)))
                .hasValueSatisfying(r -> assertThat(r.reason()).isEqualTo(RejectReason.INVALID_FIELD));
        assertThat(validator.validate(msg(2, 0.9, "MOVE", new ClassifierMessage.Signal(Double.NaN, null, null))))
                .hasValueSatisfying(r -> assertThat(r.reason()).isEqualTo(RejectReason.INVALID_FIELD));
        assertThat(validator.validate(msg(2, 0.9, "MOVE", new ClassifierMessage.Signal(0.5, "ALIEN", null))))
                .hasValueSatisfying(r -> assertThat(r.reason()).isEqualTo(RejectReason.INVALID_FIELD));
    }

    @Test
    @DisplayName("2초보다 오래된 명령은 STALE_COMMAND, 시간대 없는 timestamp는 INVALID_FIELD")
    void freshness() {
        Instant sent = Instant.parse("2026-11-17T12:00:00Z");
        String ts = "2026-11-17T21:00:00.000+09:00";      // 같은 순간 (한국 시간)
        assertThat(freshness.check(ts, sent.plusMillis(1500))).isEmpty();
        assertThat(freshness.check(ts, sent.plusMillis(2500)))
                .hasValueSatisfying(r -> assertThat(r.reason()).isEqualTo(RejectReason.STALE_COMMAND));
        assertThat(freshness.check("2026-11-17T21:00:00.000", sent))
                .hasValueSatisfying(r -> assertThat(r.reason()).isEqualTo(RejectReason.INVALID_FIELD));
        assertThat(freshness.check(ts, sent.minusSeconds(5)))          // 5초 미래 → 시계 이상
                .hasValueSatisfying(r -> assertThat(r.reason()).isEqualTo(RejectReason.INVALID_FIELD));
    }

    @Test
    @DisplayName("판별기와 관문의 판단이 다르면 DECISION_MISMATCH (둘 다 MOVE일 때만 MOVE)")
    void decisionMismatch() {
        assertThat(gate.check(msg(2, 0.9, "MOVE", ok()))).isEmpty();
        assertThat(gate.check(msg(2, 0.6, "ABSTAIN", ok()))).isEmpty();
        assertThat(gate.check(msg(2, 0.6, "MOVE", ok())))
                .hasValueSatisfying(r -> assertThat(r.reason()).isEqualTo(RejectReason.DECISION_MISMATCH));
        assertThat(gate.check(msg(2, 0.9, "ABSTAIN", ok())))
                .hasValueSatisfying(r -> assertThat(r.reason()).isEqualTo(RejectReason.DECISION_MISMATCH));
    }
}
