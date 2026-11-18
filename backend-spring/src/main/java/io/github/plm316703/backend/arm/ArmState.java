package io.github.plm316703.backend.arm;

/**
 * 로봇팔 상태 (상태 전이도)
 *
 *   IDLE ──세션 시작──▶ STOPPED ──수동 재개(조건 통과)──▶ RUNNING
 *                        ▲   ▲                              │
 *                        │   └──────── 차단·끊김 3초·철회 ────┘
 *                        │
 *                   초기화(수동)
 *                        │
 *                  RESET_REQUIRED ◀── 끊김 10초 이상 (어느 상태에서든, IDLE 제외)
 *
 *  - 자동으로 RUNNING이 되는 길은 없다. 언제나 사람이 재개 버튼을 누르고, 서버가 조건을 다시 검사한다.
 *  - 세션 종료 → IDLE
 */
public enum ArmState {
    IDLE,            // 세션 없음
    STOPPED,         // 그 자리에서 정지. 수동 재개 대기
    RUNNING,         // 관문을 통과한 명령만 실행
    RESET_REQUIRED   // 오래 끊김. 초기화(기본 자세)를 먼저 해야 재개 가능
}
