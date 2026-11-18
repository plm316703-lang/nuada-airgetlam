package io.github.plm316703.backend.record;

/**
 * 관문이 메시지 하나에 내린 최종 결과.
 *  - MOVE    : 모든 검사 통과 + 팔이 실제로 움직임
 *  - ABSTAIN : 정상 보류 (확신도가 기준 미만). 이상 상황이 아님
 *  - HELD    : 메시지는 정상이지만 팔이 정지 상태라 실행하지 않음 (수동 재개 전)
 *  - BLOCKED : 규칙 위반으로 차단 → 팔 정지 + 차단 기록 (이상 상황)
 */
public enum GateResult {
    MOVE, ABSTAIN, HELD, BLOCKED
}
