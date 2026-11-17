package io.github.plm316703.backend.gateway;

/**
 * 관문이 메시지를 거부하는 사유 목록 (docs/message-spec.md의 거부 사유와 맞춤).
 * enum: 정해진 값 몇 개 중 하나만 가질 수 있는 타입 → 오타로 새 사유가 생기는 걸 막음
 */

public enum RejectReason {
    INVALID_FORMAT, //JSON으로 읽을 수 없음
    INVALID_FIELD, // 필수값 없음, 타입 범위 허용값 위반
    UNSUPPORTED_VERSION, //모르는 규격 버전
    OUT_OF_ORDER,   //SEQ순서 어긋남
    DECISION_MISMATCH     // 12-5d: Python 판단과 관문 판단이 다름
}
