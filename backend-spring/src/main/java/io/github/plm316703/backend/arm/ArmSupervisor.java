package io.github.plm316703.backend.arm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 로봇팔 상태 관리자 (메모리 안의 상태 기계).
 *
 * 여러 스레드가 동시에 이 객체를 건드린다:
 *   - WebSocket 스레드(판별 메시지), 워치독 스레드(0.5초마다), HTTP 스레드(재개 버튼)
 * 그래서 상태를 읽고 바꾸는 메서드는 전부 synchronized → 한 번에 한 스레드만 들어옴.
 *
 * 이 클래스는 DB를 모른다. "지금 팔이 어떤 상태인가"만 책임지고, 기록은 서비스가 한다.
 */
@Component
public class ArmSupervisor {

    // 팔꿈치 각도 (도). 왼손 상상 = 굽히기(+), 오른손 상상 = 펴기(-)
    public static final double HOME_ANGLE = 90.0;
    public static final double MIN_ANGLE = 10.0;
    public static final double MAX_ANGLE = 150.0;
    public static final double STEP = 10.0;

    private final long watchdogStopMs;   // 이 시간 동안 메시지가 없으면 정지 (PACKET_LOSS)
    private final long watchdogResetMs;  // 이 시간 이상 끊기면 초기화 필요
    private final int requiredClean;     // 재개하려면 연속으로 필요한 정상 메시지 수

    private ArmState state = ArmState.IDLE;
    private double angle = HOME_ANGLE;
    private String stopReason;
    private Instant stoppedAt;

    private Long sessionId;
    private Long patientId;
    private String patientCode;          // 화면 표시용 익명 연구 코드 (실명은 담지 않음)
    private Integer datasetSubject;

    private Instant lastMessageAt;       // 마지막으로 메시지(정상이든 아니든)를 받은 시각
    private int consecutiveClean;        // 연속 정상 메시지 수 (차단되면 0으로)
    private boolean lossReported;        // 이번 끊김을 이미 기록했는가 (같은 끊김을 반복 기록하지 않으려고)

    public ArmSupervisor(@Value("${gateway.watchdog-stop-ms}") long watchdogStopMs,
                         @Value("${gateway.watchdog-reset-ms}") long watchdogResetMs,
                         @Value("${gateway.resume-required-clean}") int requiredClean) {
        if (watchdogStopMs <= 0 || watchdogResetMs <= watchdogStopMs || requiredClean < 1) {
            throw new IllegalStateException("워치독 설정이 올바르지 않음: stop=" + watchdogStopMs
                    + " reset=" + watchdogResetMs + " clean=" + requiredClean);
        }
        this.watchdogStopMs = watchdogStopMs;
        this.watchdogResetMs = watchdogResetMs;
        this.requiredClean = requiredClean;
    }

    // ───────────────────── 세션 ─────────────────────

    /** 세션 시작: 정지 상태로 시작한다 (자동 시작 없음). */
    public synchronized void startSession(Long sessionId, Long patientId, String patientCode, int datasetSubject) {
        this.sessionId = sessionId;
        this.patientId = patientId;
        this.patientCode = patientCode;
        this.datasetSubject = datasetSubject;
        this.state = ArmState.STOPPED;
        this.angle = HOME_ANGLE;
        this.stopReason = "세션 시작: 정상 데이터 확인 후 수동 재개";
        this.stoppedAt = Instant.now();
        this.lastMessageAt = null;
        this.consecutiveClean = 0;
        this.lossReported = false;
    }

    public synchronized void endSession() {
        this.state = ArmState.IDLE;
        this.sessionId = null;
        this.patientId = null;
        this.patientCode = null;
        this.datasetSubject = null;
        this.stopReason = null;
        this.stoppedAt = null;
        this.angle = HOME_ANGLE;
        this.lastMessageAt = null;
        this.consecutiveClean = 0;
        this.lossReported = false;
    }

    // ───────────────────── 메시지 ─────────────────────

    /** 메시지를 하나 받음 (내용과 상관없이 "연결은 살아 있다"는 뜻) */
    public synchronized void markReceived(Instant now) {
        this.lastMessageAt = now;
        this.lossReported = false;   // 다시 들어오기 시작했으니 다음 끊김은 새로 기록
    }

    /** 모든 검사를 통과한 정상 메시지 → 연속 정상 수 +1 */
    public synchronized void markClean() {
        this.consecutiveClean++;
    }

    /**
     * 정지. RUNNING이었으면 STOPPED로 바꾸고 true (= "지금 막 멈췄다").
     * 이미 멈춰 있으면 사유는 처음 것을 유지하고 false. 어느 경우든 연속 정상 수는 0.
     */
    public synchronized boolean stop(String reason) {
        this.consecutiveClean = 0;
        if (state == ArmState.RUNNING) {
            state = ArmState.STOPPED;
            stopReason = reason;
            stoppedAt = Instant.now();
            return true;
        }
        return false;
    }

    /** 통과한 MOVE 명령 실행. RUNNING일 때만 움직이고 true. */
    public synchronized boolean applyMove(String intent) {
        if (state != ArmState.RUNNING) {
            return false;   // 정지 중에는 정상 명령이어도 실행하지 않음 (HELD)
        }
        double delta = "left_hand".equals(intent) ? STEP : -STEP;
        angle = Math.max(MIN_ANGLE, Math.min(MAX_ANGLE, angle + delta));
        return true;
    }

    // ───────────────────── 수동 재개 / 초기화 ─────────────────────

    /**
     * 재개를 막는 조건 목록. 빈 목록이면 재개 가능.
     * 서버가 "지금 이 순간" 다시 검사한다 (화면에 보이는 상태를 믿지 않음).
     */
    public synchronized List<String> resumeBlockers(boolean participating, Instant now) {
        List<String> blockers = new ArrayList<>();
        if (state == ArmState.RESET_REQUIRED) {
            blockers.add("연결이 오래 끊겼습니다. 먼저 초기화가 필요합니다.");
            return blockers;
        }
        if (state != ArmState.STOPPED) {
            blockers.add("정지 상태가 아닙니다 (현재 " + state + ")");
            return blockers;
        }
        if (!participating) {
            blockers.add("참여 중인 환자가 아닙니다 (연구 등록·동의 확인)");
        }
        if (lastMessageAt == null || Duration.between(lastMessageAt, now).toMillis() > watchdogStopMs) {
            blockers.add("판별기 데이터가 들어오지 않고 있습니다");
        }
        if (consecutiveClean < requiredClean) {
            blockers.add("연속 정상 데이터 " + consecutiveClean + "/" + requiredClean + "개");
        }
        return blockers;
    }

    public synchronized void resume() {
        if (state != ArmState.STOPPED) {
            throw new IllegalStateException("정지 상태에서만 재개할 수 있습니다.");
        }
        state = ArmState.RUNNING;
        stopReason = null;
        stoppedAt = null;
    }

    /** 초기화: 기본 자세로 되돌리고 정지 상태로 (재개는 다시 조건 검사) */
    public synchronized void reset() {
        if (state != ArmState.RESET_REQUIRED && state != ArmState.STOPPED) {
            throw new IllegalStateException("정지 또는 초기화 필요 상태에서만 초기화할 수 있습니다.");
        }
        angle = HOME_ANGLE;
        state = ArmState.STOPPED;
        stopReason = "초기화 완료: 정상 데이터 확인 후 수동 재개";
        stoppedAt = Instant.now();
        consecutiveClean = 0;
    }

    // ───────────────────── 워치독 ─────────────────────

    public enum Tick { NONE, LOSS_WHILE_RUNNING, LOSS_WHILE_STOPPED, RESET_REQUIRED }

    /**
     * 0.5초마다 호출. 마지막 메시지 이후 시간이 기준을 넘었는지 본다.
     * 메시지를 한 번도 받지 않았으면(재생기가 아직 안 켜짐) 판단하지 않는다.
     */
    public synchronized Tick tick(Instant now) {
        if (state == ArmState.IDLE || lastMessageAt == null) {
            return Tick.NONE;
        }
        long gap = Duration.between(lastMessageAt, now).toMillis();

        if (gap >= watchdogResetMs && state != ArmState.RESET_REQUIRED) {
            state = ArmState.RESET_REQUIRED;
            stopReason = "연결 끊김 " + (watchdogResetMs / 1000) + "초 이상: 초기화 필요";
            stoppedAt = now;
            consecutiveClean = 0;
            return Tick.RESET_REQUIRED;
        }
        if (gap >= watchdogStopMs && !lossReported) {
            lossReported = true;
            consecutiveClean = 0;
            if (state == ArmState.RUNNING) {
                state = ArmState.STOPPED;
                stopReason = "PACKET_LOSS: " + gap + "ms 동안 메시지 없음";
                stoppedAt = now;
                return Tick.LOSS_WHILE_RUNNING;
            }
            return Tick.LOSS_WHILE_STOPPED;
        }
        return Tick.NONE;
    }

    // ───────────────────── 읽기 ─────────────────────

    public synchronized Snapshot snapshot(Instant now) {
        Long agoMs = lastMessageAt == null ? null : Duration.between(lastMessageAt, now).toMillis();
        return new Snapshot(state, angle, stopReason, stoppedAt, sessionId, patientId, patientCode,
                datasetSubject, agoMs, consecutiveClean, requiredClean);
    }

    /** 화면·기록으로 내보내는 "지금 이 순간" 사진. record라 바뀌지 않음 → 다른 스레드에 넘겨도 안전 */
    public record Snapshot(ArmState state, double angle, String stopReason, Instant stoppedAt,
                           Long sessionId, Long patientId, String patientCode, Integer datasetSubject,
                           Long lastMessageAgoMs, int consecutiveClean, int requiredClean) {}

    public long watchdogStopMs() { return watchdogStopMs; }
    public long watchdogResetMs() { return watchdogResetMs; }
    public int requiredClean() { return requiredClean; }
}
