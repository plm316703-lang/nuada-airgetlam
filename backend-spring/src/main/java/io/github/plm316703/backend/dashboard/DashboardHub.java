package io.github.plm316703.backend.dashboard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 관제 화면(브라우저)으로 실시간 소식을 보내는 확성기. 주소: /ws/dashboard
 * 로그인 쿠키(JWT)로 인증된 관제 역할만 연결할 수 있다 (SecurityConfig).
 *
 * 화면은 처음 열 때 REST(/api/dashboard/state)로 전체 상태를 받고,
 * 그다음부터는 여기서 보내는 {"type": ..., "data": ...} 소식으로 화면을 갱신한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DashboardHub extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;

    // 연결된 화면들. 여러 스레드가 동시에 보내므로 ConcurrentWebSocketSessionDecorator로 감싼다
    // (WebSocket 세션 하나에 두 스레드가 동시에 쓰면 오류가 나기 때문)
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), new ConcurrentWebSocketSessionDecorator(session, 2000, 512 * 1024));
        log.info("관제 화면 연결 id={} user={}", session.getId(),
                session.getPrincipal() == null ? "-" : session.getPrincipal().getName());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
    }

    /** 모든 관제 화면에 소식 한 개를 보낸다. 한 화면이 실패해도 나머지는 계속 받는다. */
    public void broadcast(String type, Object data) {
        if (sessions.isEmpty()) return;
        String json;
        try {
            json = objectMapper.writeValueAsString(Map.of("type", type, "data", data));
        } catch (RuntimeException e) {
            log.warn("관제 소식 변환 실패 type={}", type, e);
            return;
        }
        TextMessage message = new TextMessage(json);
        sessions.values().forEach(s -> {
            try {
                if (s.isOpen()) s.sendMessage(message);
            } catch (IOException | IllegalStateException e) {
                log.debug("관제 화면 전송 실패 id={}", s.getId());
            }
        });
    }
}
