package io.github.plm316703.backend.gateway;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.JacksonException;      // Jackson 3: 파싱 실패 예외 (RuntimeException)
import tools.jackson.databind.ObjectMapper;      // Jackson 3: JSON ↔ Java 변환기

/**
 * /ws/classifier 로 들어오는 판별기 메시지를 받는 담당자.
 * 받은 메시지를 4단계로 검사하고, 하나라도 걸리면 거부(움직이지 않음)한다.
 *   12-5a 파싱 → 12-5b 칸·범위·버전 → 12-5c 순서 → 12-5d 판단 일치
 */
@Slf4j
@Component
@RequiredArgsConstructor   // final 필드 4개를 받는 생성자를 Lombok이 만들어 줌 → Spring이 주입
public class ClassifierWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;           // 12-5a: JSON 변환기 (Spring Boot가 만든 것을 주입)
    private final MessageValidator validator;          // 12-5b: 내용 검사 담당
    private final SequenceTracker sequenceTracker;     // 12-5c: seq 순서 검사 담당
    private final DecisionGate decisionGate;           // 12-5d: 페일세이프 판단 담당

    // 연결이 열렸을 때: 누가(id) 어디서(addr) 왔는지 기록
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("연결됨 id={} addr={}", session.getId(), session.getRemoteAddress());
    }

    // 메시지를 받을 때마다 호출됨
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage textMessage) {
        String payload = textMessage.getPayload();   // 받은 원본 문자열

        // ── 12-5a: JSON → ClassifierMessage 변환 ──────────────────
        ClassifierMessage msg;
        try {
            msg = objectMapper.readValue(payload, ClassifierMessage.class);
        } catch (JacksonException e) {
            // JSON이 깨졌거나("{abc"), 타입이 안 맞을 때("seq":"하나") 여기로 옴
            // 페일세이프: 읽을 수 없는 메시지는 절대 MOVE로 이어지지 않게 여기서 끝냄
            log.warn("거부 id={} reason={} detail={}",
                    session.getId(), RejectReason.INVALID_FORMAT, e.getOriginalMessage());
            return;
        }

        // ── 12-5b: 필수 칸·타입·범위·version 검사 ─────────────────
        var rejection = validator.validate(msg);
        if (rejection.isPresent()) {
            log.warn("거부 id={} seq={} reason={} detail={}",
                    session.getId(), msg.seq(),
                    rejection.get().reason(), rejection.get().detail());
            return;   // 페일세이프: 검사에 걸린 메시지는 여기서 끝 (MOVE로 이어지지 않음)
        }

        // ── 12-5c: seq 순서 검사 (중복·뒤늦은 옛 메시지 거부) ─────────
        // 12-5b를 통과했으므로 msg.seq()는 null이 아님이 보장됨
        var outOfOrder = sequenceTracker.check(session.getId(), msg.seq());
        if (outOfOrder.isPresent()) {
            log.warn("거부 id={} seq={} reason={} detail={}",
                    session.getId(), msg.seq(),
                    outOfOrder.get().reason(), outOfOrder.get().detail());
            return;   // 페일세이프: 순서가 어긋난 명령은 실행하지 않음
        }

        // ── 12-5d: 페일세이프 판단 (Python 판단 vs 관문 판단) ─────────
        var mismatch = decisionGate.check(msg);
        if (mismatch.isPresent()) {
            log.warn("거부 id={} seq={} reason={} detail={}",
                    session.getId(), msg.seq(),
                    mismatch.get().reason(), mismatch.get().detail());
            return;   // 페일세이프: 판단이 엇갈리면 움직이지 않음
        }

        // ── 모든 검사 통과: 최종 판정 (다음 단계에서 DB 저장·관제 화면 전달 예정) ──
        log.info("판정 id={} seq={} intent={} conf={} final={}",
                session.getId(), msg.seq(), msg.intent(), msg.confidence(), msg.decision());
    }

    // 연결이 닫혔을 때: 종료 코드(1000 정상, 1006 갑자기 끊김 등) 기록
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sequenceTracker.forget(session.getId());   // 이 연결의 seq 장부 삭제 (메모리 누수 방지)
        log.info("연결 종료 id={} code={}", session.getId(), status.getCode());
    }
}