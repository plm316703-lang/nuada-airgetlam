package io.github.plm316703.backend.arm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 로봇팔 상태 기계 단위 테스트 (DB·Spring 없이, 순수 자바 객체만)
 * 안전 규칙: 자동 재개 없음 / 정지 중 명령 무시 / 끊김 3초 정지 / 10초 초기화 필요
 */
class ArmSupervisorTest {

    ArmSupervisor arm;
    Instant t0;

    @BeforeEach
    void setUp() {
        arm = new ArmSupervisor(3000, 10000, 3);   // 정지 3초, 초기화 10초, 연속 정상 3개
        t0 = Instant.parse("2026-11-17T12:00:00Z");
        arm.startSession(1L, 10L, "P-test0001", 3);
    }

    /** 정상 메시지 n개를 1초 간격으로 받은 것처럼 만든다 */
    private Instant receiveClean(int n, Instant from) {
        Instant t = from;
        for (int i = 0; i < n; i++) {
            t = t.plusSeconds(1);
            arm.markReceived(t);
            arm.markClean();
        }
        return t;
    }

    @Test
    @DisplayName("세션 시작 직후는 정지 상태이고, 정상 명령이 와도 움직이지 않는다 (자동 시작 없음)")
    void startsStopped() {
        assertThat(arm.snapshot(t0).state()).isEqualTo(ArmState.STOPPED);
        assertThat(arm.applyMove("left_hand")).isFalse();
        assertThat(arm.snapshot(t0).angle()).isEqualTo(ArmSupervisor.HOME_ANGLE);
    }

    @Test
    @DisplayName("연속 정상 3개 전에는 재개를 거절하고, 3개가 쌓이면 재개할 수 있다")
    void resumeNeedsCleanStreak() {
        Instant t = receiveClean(2, t0);
        assertThat(arm.resumeBlockers(true, t)).anyMatch(b -> b.contains("2/3"));

        t = receiveClean(1, t);
        assertThat(arm.resumeBlockers(true, t)).isEmpty();
        arm.resume();
        assertThat(arm.snapshot(t).state()).isEqualTo(ArmState.RUNNING);
    }

    @Test
    @DisplayName("참여 중인 환자가 아니면 재개할 수 없다 (관문 규칙 ①)")
    void resumeNeedsParticipation() {
        Instant t = receiveClean(3, t0);
        assertThat(arm.resumeBlockers(false, t)).anyMatch(b -> b.contains("참여"));
    }

    @Test
    @DisplayName("동작 중 왼손 = 굽히기(+10°), 오른손 = 펴기(-10°), 범위를 넘지 않는다")
    void moveChangesAngleWithinLimits() {
        Instant t = receiveClean(3, t0);
        arm.resume();
        arm.applyMove("left_hand");
        assertThat(arm.snapshot(t).angle()).isEqualTo(100.0);
        arm.applyMove("right_hand");
        arm.applyMove("right_hand");
        assertThat(arm.snapshot(t).angle()).isEqualTo(80.0);
        for (int i = 0; i < 30; i++) arm.applyMove("left_hand");
        assertThat(arm.snapshot(t).angle()).isEqualTo(ArmSupervisor.MAX_ANGLE);
    }

    @Test
    @DisplayName("차단되면 그 자리에서 정지하고, 연속 정상 수가 0이 된다")
    void blockStopsInPlace() {
        Instant t = receiveClean(3, t0);
        arm.resume();
        arm.applyMove("left_hand");                 // 100°
        assertThat(arm.stop("SIGNAL_QUALITY")).isTrue();
        var s = arm.snapshot(t);
        assertThat(s.state()).isEqualTo(ArmState.STOPPED);
        assertThat(s.angle()).isEqualTo(100.0);     // 기본 자세로 돌아가지 않음
        assertThat(s.consecutiveClean()).isZero();
        assertThat(arm.applyMove("left_hand")).isFalse();
        assertThat(arm.stop("다시")).isFalse();      // 이미 정지 → "지금 막 멈춤" 아님
    }

    @Test
    @DisplayName("워치독: 3초 끊기면 정지(한 번만 기록), 10초 끊기면 초기화 필요")
    void watchdog() {
        Instant t = receiveClean(3, t0);
        arm.resume();

        assertThat(arm.tick(t.plusMillis(2900))).isEqualTo(ArmSupervisor.Tick.NONE);
        assertThat(arm.tick(t.plusMillis(3100))).isEqualTo(ArmSupervisor.Tick.LOSS_WHILE_RUNNING);
        assertThat(arm.snapshot(t).state()).isEqualTo(ArmState.STOPPED);
        assertThat(arm.tick(t.plusMillis(5000))).isEqualTo(ArmSupervisor.Tick.NONE);   // 같은 끊김 반복 기록 안 함

        assertThat(arm.tick(t.plusMillis(10000))).isEqualTo(ArmSupervisor.Tick.RESET_REQUIRED);
        assertThat(arm.snapshot(t).state()).isEqualTo(ArmState.RESET_REQUIRED);

        // 데이터가 다시 들어와도 초기화 전에는 재개 불가
        Instant back = receiveClean(5, t.plusSeconds(12));
        assertThat(arm.resumeBlockers(true, back)).anyMatch(b -> b.contains("초기화"));

        arm.reset();
        assertThat(arm.snapshot(back).state()).isEqualTo(ArmState.STOPPED);
        assertThat(arm.snapshot(back).angle()).isEqualTo(ArmSupervisor.HOME_ANGLE);
        assertThat(arm.resumeBlockers(true, back)).isNotEmpty();   // 초기화 뒤 연속 정상 다시 필요
        Instant later = receiveClean(3, back);
        assertThat(arm.resumeBlockers(true, later)).isEmpty();
    }

    @Test
    @DisplayName("메시지를 한 번도 받지 않았으면 워치독이 판단하지 않는다 (재생기 시작 전)")
    void watchdogWaitsForFirstMessage() {
        assertThat(arm.tick(t0.plusSeconds(60))).isEqualTo(ArmSupervisor.Tick.NONE);
    }

    @Test
    @DisplayName("동작 중에는 초기화할 수 없다")
    void cannotResetWhileRunning() {
        receiveClean(3, t0);
        arm.resume();
        assertThatThrownBy(() -> arm.reset()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("설정값이 말이 안 되면 시작 단계에서 막는다")
    void rejectsBadConfig() {
        assertThatThrownBy(() -> new ArmSupervisor(3000, 2000, 3)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ArmSupervisor(3000, 10000, 0)).isInstanceOf(IllegalStateException.class);
    }
}
