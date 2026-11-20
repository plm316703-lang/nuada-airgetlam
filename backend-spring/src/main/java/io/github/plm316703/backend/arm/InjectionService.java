package io.github.plm316703.backend.arm;

import io.github.plm316703.backend.dashboard.DashboardHub;
import io.github.plm316703.backend.dashboard.Views;
import io.github.plm316703.backend.gateway.ReplayerCommander;
import io.github.plm316703.backend.record.ControlEvent;
import io.github.plm316703.backend.record.ControlEventRepository;
import io.github.plm316703.backend.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 시연용 오류 주입 (11/20 B2) — 관제 화면 버튼 → 재생기.
 *
 * 하는 일은 두 가지뿐이다: ① 재생기에 "다음 시행부터 이 오류를 섞어라" 명령 ② 관제 동작 기록에 INJECT 마커.
 * 팔을 멈추거나 관문 결과를 바꾸는 코드는 없다 (내 설계 결정: 주입은 가짜 데이터만 바꾼다).
 * 정지·차단·RAG 안내는 주입된 데이터가 평소처럼 관문을 지나면서 일어난다.
 */
@Service
public class InjectionService {

    /** 재생기(replayer.py)의 ALL_SCENARIOS와 같은 이름. 화면 표시 이름도 함께 (순서 = 버튼 순서) */
    public static final Map<String, String> KINDS = new LinkedHashMap<>();
    static {
        KINDS.put("electrode", "전극 불량");
        KINDS.put("noise", "전원 잡음(60Hz)");
        KINDS.put("muscle", "근육 잡음");
        KINDS.put("mismatch", "판단 불일치");
        KINDS.put("stale", "오래된 명령");
        KINDS.put("disconnect", "끊김 5초");
        KINDS.put("reset", "끊김 12초");
    }

    private final ReplayerCommander commander;
    private final ControlEventRepository controlRepository;
    private final DashboardHub hub;
    private final ArmSupervisor arm;
    private final boolean enabled;

    public InjectionService(ReplayerCommander commander, ControlEventRepository controlRepository,
                            DashboardHub hub, ArmSupervisor arm,
                            @Value("${app.demo.injection-enabled}") boolean enabled) {
        this.commander = commander;
        this.controlRepository = controlRepository;
        this.hub = hub;
        this.arm = arm;
        this.enabled = enabled;
    }

    /** 시연 모드가 켜져 있는가 (꺼져 있으면 화면에 버튼을 그리지 않고, 서버도 거절) */
    public boolean enabled() { return enabled; }

    @Transactional
    public int inject(String kind, User actor) {
        // 화면에서 버튼을 숨겨도, 주소를 직접 불러 우회할 수 있으므로 서버가 다시 막는다
        if (!enabled) {
            throw new IllegalStateException("시연 모드가 꺼져 있어 오류를 주입할 수 없습니다.");
        }
        if (!KINDS.containsKey(kind)) {
            throw new IllegalArgumentException("알 수 없는 주입 종류입니다: " + kind);
        }
        int sent = commander.sendInject(kind);
        if (sent == 0) {
            throw new IllegalStateException("연결된 재생기가 없습니다. 재생기를 먼저 실행하세요.");
        }
        // 마커: 세션이 없을 때 누른 것도 남긴다 (세션 번호만 비어 있음)
        Long sessionId = arm.snapshot(Instant.now()).sessionId();
        ControlEvent saved = controlRepository.save(new ControlEvent(sessionId, ControlEvent.Type.INJECT, actor,
                kind + " (" + KINDS.get(kind) + ")"));
        hub.broadcast("control", Views.ControlView.of(saved));
        return sent;
    }
}
