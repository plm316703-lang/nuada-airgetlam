package io.github.plm316703.backend.arm;

import io.github.plm316703.backend.dashboard.DashboardHub;
import io.github.plm316703.backend.dashboard.Views;
import io.github.plm316703.backend.record.ControlEvent;
import io.github.plm316703.backend.record.ControlEventRepository;
import io.github.plm316703.backend.record.DeviceErrorService;
import io.github.plm316703.backend.record.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 워치독(감시견): 0.5초마다 "판별기 메시지가 끊기지 않았나"를 확인한다.
 *  - 3초 동안 없음 → 정지 + PACKET_LOSS 오류 기록(+RAG 안내)
 *  - 10초 이상 없음 → 초기화 필요 상태 (자동 복귀 없음)
 * 기준 시간은 application.yaml 의 gateway.watchdog-* 설정.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Watchdog {

    private final ArmSupervisor arm;
    private final DeviceErrorService errorService;
    private final ControlEventRepository controlRepository;
    private final DashboardHub hub;

    @Scheduled(fixedDelay = 500)
    public void check() {
        Instant now = Instant.now();
        ArmSupervisor.Tick tick = arm.tick(now);
        if (tick == ArmSupervisor.Tick.NONE) return;

        var snap = arm.snapshot(now);
        try {
            switch (tick) {
                case LOSS_WHILE_RUNNING, LOSS_WHILE_STOPPED -> {
                    log.warn("워치독: {}ms 동안 메시지 없음 → {}", snap.lastMessageAgoMs(), tick);
                    if (tick == ArmSupervisor.Tick.LOSS_WHILE_RUNNING) {
                        save(snap.sessionId(), ControlEvent.Type.STOP, "PACKET_LOSS: 워치독 정지");
                    }
                    var reported = errorService.report(snap.sessionId(), ErrorType.PACKET_LOSS,
                            "PACKET_LOSS: 마지막 메시지 후 " + snap.lastMessageAgoMs() + "ms 동안 수신 없음 (기준 "
                                    + arm.watchdogStopMs() + "ms)", null, false);
                    hub.broadcast("error", Views.ErrorView.of(reported.error()));
                }
                case RESET_REQUIRED -> {
                    log.warn("워치독: 끊김 {}ms → 초기화 필요", snap.lastMessageAgoMs());
                    save(snap.sessionId(), ControlEvent.Type.RESET_REQUIRED, snap.stopReason());
                }
                default -> { }
            }
        } catch (RuntimeException e) {
            // 기록이 실패해도 팔은 이미 메모리 상태에서 정지됨 → 안전 쪽
            log.error("워치독 기록 실패", e);
        }
        hub.broadcast("arm", Views.ArmView.of(snap));
    }

    private void save(Long sessionId, ControlEvent.Type type, String detail) {
        ControlEvent saved = controlRepository.save(new ControlEvent(sessionId, type, null, detail));
        hub.broadcast("control", Views.ControlView.of(saved));
    }
}
