package io.github.plm316703.backend.gateway;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 재생기 명령 전달자 (11/20 B2).
 * 이미 API 키로 인증된 /ws/classifier 연결을 거꾸로도 쓴다: 재생기 → Spring(판별 메시지)에 더해
 * Spring → 재생기(오류 주입 명령). 새 포트·Docker 설정이 필요 없다 (Q-B2a 결정).
 *
 * 원칙: 이 명령은 재생기의 **가짜 데이터**만 바꾼다. 팔·관문 상태는 건드리지 않고,
 * 주입된 데이터도 평소처럼 관문 검사를 거쳐 판단된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReplayerCommander {

    private final ObjectMapper objectMapper;
    // 판별기 연결은 여러 스레드(WebSocket 수신, HTTP 버튼 요청)가 동시에 건드리므로 동시성 안전한 지도
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    void register(WebSocketSession session) { sessions.put(session.getId(), session); }

    void unregister(WebSocketSession session) { sessions.remove(session.getId()); }

    public int connectedCount() { return sessions.size(); }

    /** 연결된 모든 재생기에 주입 명령을 보내고, 실제로 보낸 개수를 돌려준다 */
    public int sendInject(String kind) {
        // 규격: docs/message-spec.md "Spring → 재생기 명령"
        String json = objectMapper.writeValueAsString(
                Map.of("version", 2, "type", "command", "command", "inject", "kind", kind));
        int sent = 0;
        for (WebSocketSession s : sessions.values()) {
            if (!s.isOpen()) continue;
            // WebSocketSession.sendMessage는 여러 스레드가 동시에 부르면 안 된다 → 연결마다 잠그고 보냄
            synchronized (s) {
                try {
                    s.sendMessage(new TextMessage(json));
                    sent++;
                } catch (IOException | IllegalStateException e) {
                    // 버튼을 누른 순간 연결이 끊긴 경우: 실패한 연결만 건너뛰고 나머지에는 보낸다
                    log.warn("재생기에 명령 전송 실패 id={}", s.getId(), e);
                }
            }
        }
        return sent;
    }
}
