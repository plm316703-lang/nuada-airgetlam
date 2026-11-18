package io.github.plm316703.backend.gateway;

import org.springframework.stereotype.Component;
import java.util.Optional;
import java.util.Set;

/**
 * 파싱된 메시지의 "내용"이 규격에 맞는지 검사하는 담당자.
 * 문제가 있으면 거부 사유를, 없으면 빈 Optional을 돌려준다.
 */
@Component
public class MessageValidator {

    /** 거부 결과: 사유(reason) + 사람이 읽을 설명(detail) */
    public record Rejection(RejectReason reason, String detail){}

    //허용값 목록(화이트리스트) - 이 안에 있는 값만 통과
    static final int SUPPORTED_VERSION = 2;   // 버전 2: 신호 품질 + 시간대 있는 timestamp
    private static final String TYPE_CLASSIFICATION = "classification";
    private static final Set<String> ALLOWED_INTENTS = Set.of("left_hand", "right_hand");
    private static final Set<String> ALLOWED_DECISIONS = Set.of("MOVE", "ABSTAIN");
    // 판별기가 추정해서 보낼 수 있는 신호 문제 종류 (record.ErrorType 이름과 같음)
    static final Set<String> ALLOWED_ISSUES = Set.of("ELECTRODE_CONTACT", "LINE_NOISE", "MUSCLE_ARTIFACT");
    private static final int MIN_SUBJECT = 1;   // BNCI2014_001 피험자 1~9번
    private static final int MAX_SUBJECT = 9;

    public Optional<Rejection> validate(ClassifierMessage m){

        // - 1. version 먼저 : 모르는 버전이면 나머지 칸은 볼 필요가 없음
        if(m.version() == null){
            return reject(RejectReason.INVALID_FIELD, "version없음" );
        }
        if (m.version() != SUPPORTED_VERSION) {
            return reject(RejectReason.UNSUPPORTED_VERSION, "version=" + m.version());
        }
        // ── ② 나머지 필수 칸: 비었는지 + 값이 말이 되는지 ──
        if (!TYPE_CLASSIFICATION.equals(m.type())) {
            // "상수.equals(값)" 순서로 쓰면 m.type()이 null이어도 NullPointerException이 안 남
            return reject(RejectReason.INVALID_FIELD, "type=" + m.type());
        }
        if (m.seq() == null || m.seq() < 0) {
            // || 는 왼쪽이 true면 오른쪽을 안 봄 → null일 때 m.seq() < 0 을 계산하지 않아 안전
            return reject(RejectReason.INVALID_FIELD, "seq=" + m.seq());
        }
        if (m.timestamp() == null || m.timestamp().isBlank()) {
            return reject(RejectReason.INVALID_FIELD, "timestamp 없음");
        }
        if (m.subject() == null || m.subject() < MIN_SUBJECT || m.subject() > MAX_SUBJECT) {
            return reject(RejectReason.INVALID_FIELD, "subject=" + m.subject());
        }
        if (!ALLOWED_INTENTS.contains(m.intent())) {
            return reject(RejectReason.INVALID_FIELD, "intent=" + m.intent());
        }
        if (!isProbability(m.confidence())) {
            return reject(RejectReason.INVALID_FIELD, "confidence=" + m.confidence());
        }
        if (!isProbability(m.threshold())) {
            return reject(RejectReason.INVALID_FIELD, "threshold=" + m.threshold());
        }
        if (!ALLOWED_DECISIONS.contains(m.decision())) {
            return reject(RejectReason.INVALID_FIELD, "decision=" + m.decision());
        }
        // ── ③ 신호 품질 칸 (버전 2 필수) ──
        if (m.signal() == null) {
            return reject(RejectReason.INVALID_FIELD, "signal 없음");
        }
        if (!isProbability(m.signal().quality())) {
            return reject(RejectReason.INVALID_FIELD, "signal.quality=" + m.signal().quality());
        }
        if (m.signal().issue() != null && !ALLOWED_ISSUES.contains(m.signal().issue())) {
            return reject(RejectReason.INVALID_FIELD, "signal.issue=" + m.signal().issue());
        }
        // trueLabel, scenario는 시연용 참고 칸 → 없어도 통과 (판단에 쓰지 않음)

        return Optional.empty();   // 모든 검사 통과
    }
    /**
     * 0.0 이상 1.0 이하의 "진짜 숫자"인지 확인.
     * NaN은 모든 비교가 false라서, "범위 안이 확인될 때만 true"로 써야 NaN이 걸러짐.
     */
    private boolean isProbability(Double v) {
        return v != null && v >= 0.0 && v <= 1.0;
    }
    // 거부 결과를 만드는 짧은 도우미
    private Optional<Rejection> reject(RejectReason reason, String detail) {
        return Optional.of(new Rejection(reason, detail));
    }
}
