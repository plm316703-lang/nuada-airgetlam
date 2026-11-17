package io.github.plm316703.backend.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
/**
 * 연결(session)별로 "마지막으로 받아들인 seq"를 기억해서,
 * 중복되거나 뒤늦게 온 옛 메시지를 OUT_OF_ORDER 로 거부하는 담당자.
 */

@Slf4j
@Component
public class SequenceTracker {

    // 장부: session id → 마지막으로 통과한 seq
    // ConcurrentHashMap: 여러 스레드(Tomcat 일꾼)가 동시에 읽고 써도 안전한 Map
    // (보통 HashMap은 동시에 쓰면 내용이 깨질 수 있음)
    private final Map<String, Long> lastSeqBySession = new ConcurrentHashMap<>();

    /**
     * seq가 이 연결에서 지금까지보다 큰지 검사하고, 통과하면 장부를 갱신한다.
     *
     * 참고: Spring(Tomcat)은 "같은 연결"의 메시지를 한 번에 하나씩 차례로 처리한다.
     * 그래서 같은 id에 대해 get과 put 사이에 다른 메시지가 끼어들 걱정은 없다.
     * ConcurrentHashMap이 필요한 이유는 "서로 다른 연결"이 동시에 장부를 쓰기 때문.
     */
    public Optional<MessageValidator.Rejection> check(String sessionId, long seq){
    Long last = lastSeqBySession.get(sessionId); // 처음 온 연결이면 null

        //이미 받은 번호와 같거나 작음 -> 중복 또는 옛 명령 -> 거부 (장부는 그대로 -
        if(last != null && seq <= last){
            return Optional.of(new MessageValidator.Rejection(
                    RejectReason.OUT_OF_ORDER, "seq=" + seq + " last=" + last));
            }
        // 번호가 건너뛰었음 (예: 6 다음 9) → 거부는 안 하지만 기록은 남김
        if (last != null && seq > last + 1) {
            log.warn("seq 건너뜀 id={} last={} seq={} missing={}",
                    sessionId, last, seq, seq - last - 1);
        }

        lastSeqBySession.put(sessionId, seq);   // 통과한 경우에만 장부 갱신
        return Optional.empty();
    }

    /** 연결이 끊기면 그 연결의 장부를 지움 (안 지우면 메모리가 계속 쌓임) */
    public void forget(String sessionId) {
        lastSeqBySession.remove(sessionId);
    }
}