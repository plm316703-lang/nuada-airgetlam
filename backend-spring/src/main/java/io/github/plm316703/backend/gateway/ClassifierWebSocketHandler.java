package io.github.plm316703.backend.gateway;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * /ws/classifier 로 들어오는 판별기 메시지를 받는 담당자 (웨이터).
 * 받은 메시지는 GateService(관문 본체)에 그대로 넘기고, 연결 시작·종료만 직접 기록한다.
 * 검사 순서와 규칙은 GateService 주석 참고.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClassifierWebSocketHandler extends TextWebSocketHandler {

    private final GateService gateService;
    private final SequenceTracker sequenceTracker;
    private final ReplayerCommander commander;

    // 연결이 열렸을 때: 누가(id) 어디서(addr) 왔는지 기록 + 오류 주입 명령을 보낼 수 있게 등록 (B2)
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        commander.register(session);
        log.info("연결됨 id={} addr={}", session.getId(), session.getRemoteAddress());
    }

    // 메시지를 받을 때마다 호출됨
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage textMessage) {
        try {
            gateService.handle(session.getId(), textMessage.getPayload());
        } catch (RuntimeException e) {
            // 관문 안에서 예상 못 한 오류(예: DB 끊김)가 나도 연결을 끊지 않고 기록만 한다.
            // 이때 팔은 움직이지 않는다 (MOVE까지 도달하지 못했으므로) + 워치독은 계속 감시
            log.error("관문 처리 중 오류 id={}", session.getId(), e);
        }
    }

    // 연결이 닫혔을 때: 종료 코드(1000 정상, 1006 갑자기 끊김 등) 기록
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sequenceTracker.forget(session.getId());   // 이 연결의 seq 장부 삭제 (메모리 누수 방지)
        commander.unregister(session);
        log.info("연결 종료 id={} code={}", session.getId(), status.getCode());
    }
}
