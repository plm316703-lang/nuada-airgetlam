package io.github.plm316703.backend.patient;

import io.github.plm316703.backend.arm.ArmState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FriendlyStopReasonTest {   // 순수 자바 테스트 (DB·Spring 없이 문장 변환만 확인)

    @Test
    @DisplayName("신호 품질 차단은 원인(issue)에 맞는 쉬운 문장이 되고, 기술 문구는 남지 않는다")
    void signalQualityByIssue() {
        String raw = "SIGNAL_QUALITY: quality=0.1 min=0.6 issue=ELECTRODE_CONTACT channels=[C3, Cz, C4]";

        String text = FriendlyStopReason.reason(raw);

        assertThat(text).contains("전극");
        // 환자 화면에 원문이 새어 나가지 않는 것이 이 클래스의 목적
        assertThat(text).doesNotContain("SIGNAL_QUALITY", "quality=", "C3");
    }

    @Test
    @DisplayName("연결 끊김(PACKET_LOSS)은 장비 연결 문장으로 바뀐다")
    void packetLoss() {
        assertThat(FriendlyStopReason.reason("PACKET_LOSS: 3021ms 동안 메시지 없음"))
                .isEqualTo("장비 연결이 잠시 끊겨 안전을 위해 멈췄어요.");
    }

    @Test
    @DisplayName("모르는 사유나 빈 값이 와도 원문 대신 일반 문장을 돌려준다")
    void unknownFallsBack() {
        assertThat(FriendlyStopReason.reason("SOMETHING_NEW: secret detail"))
                .isEqualTo("안전을 위해 잠시 멈췄어요.");
        assertThat(FriendlyStopReason.reason(null)).isEqualTo("안전을 위해 잠시 멈췄어요.");
    }

    @Test
    @DisplayName("정지 상태 안내문은 사람이 확인한 뒤 다시 움직인다는 것(자동 재개 없음)을 알려 준다")
    void stoppedMentionsManualResume() {
        String text = FriendlyStopReason.nowMessage(ArmState.STOPPED, "PACKET_LOSS: 3021ms 동안 메시지 없음");

        assertThat(text).contains("관제 담당자가 확인한 뒤 다시 움직여요");
    }
}
