package io.github.plm316703.backend.gateway;

/**
 * 관문이 메시지를 거부(차단)하는 사유 목록 (docs/message-spec.md의 거부 사유와 맞춤).
 * enum: 정해진 값 몇 개 중 하나만 가질 수 있는 타입 → 오타로 새 사유가 생기는 걸 막음
 *
 * 검사 순서: 형식 → 칸 → 순서 → 시간 → 세션 → 참여 → 신호 품질 → 판단 일치
 */
public enum RejectReason {
    INVALID_FORMAT,      // JSON으로 읽을 수 없음
    INVALID_FIELD,       // 필수값 없음, 타입 범위 허용값 위반
    UNSUPPORTED_VERSION, // 모르는 규격 버전
    OUT_OF_ORDER,        // seq 순서 어긋남 (중복·옛 메시지)
    STALE_COMMAND,       // 명령 유효시간 초과 (오래된 명령)
    NO_ACTIVE_SESSION,   // 열려 있는 측정 세션이 없음
    SUBJECT_MISMATCH,    // 세션에 등록된 데이터 피험자와 메시지 subject가 다름
    NOT_PARTICIPATING,   // 관문 규칙 ①: 등록 + 동의 중인 환자가 아님
    SIGNAL_QUALITY,      // 관문 규칙 ②: 신호 품질 기준 미만
    DECISION_MISMATCH,   // 12-5d: Python 판단과 관문 판단이 다름
    PACKET_LOSS          // 워치독: 일정 시간 메시지 없음
}
