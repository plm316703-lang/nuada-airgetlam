package io.github.plm316703.backend.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Python 판별 서버가 보내는 메시지를 받는 WebSocket 담당자.
 * 12-2 단계: 받은 내용을 로그로 출력만 한다. (검사·판단·저장은 12-5에서)
 *
 * TextWebSocketHandler = 텍스트 메시지(JSON 문자열)를 다루는 기본 담당자.
 * 필요한 순간(연결됨 / 메시지 받음 / 끊김)의 메서드만 골라서 덮어쓴다(@Override).
 */
@Slf4j // log 변수를 자동으로 만들어 줌 (Lombok)
@Component // Spring이 이 객체를 하나 만들어 관리 → WebSocketConfig에서 주입받음
public class ClassifierWebSocketHandler extends TextWebSocketHandler{

    //새 연결이 생겼을 때 1번이 호출 된다.
    @Override
    public void afterConnectionEstablished(WebSocketSession session){
        //session = 연결 하나, 연결 마다 고유 id가 있다(나중에 seq 초기화 기준이 됨)
        log.info("[연결] id={}, 주소={}", session.getId(), session.getRemoteAddress());
    }

    //텍스트 메세지가 하나 들어올 떄마다 호출된다
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message){
        //getpayLoad() = 받은 글자 그대로(아직 JSON으로 해석되지 않음)
        log.info("[수신] id ={}, 내용={}", session.getId(), message.getPayload());
    }

    //연결이 끊겼을 때 1번 호출된다.(정상 종료든 오류든)
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status){
        //status = 끊긴 이유코 코드. 1000 = 정상 종료
        log.info("[종료] id={}, 상태={}", session.getId(), status);
    }
}
