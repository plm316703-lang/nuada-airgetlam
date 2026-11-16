# 메시지 규격: Python 판별 서버 → Spring 관문

> 버전: 1 / 작성: 2026-11-16 / 상태: 초안
> 전송: WebSocket, 텍스트 프레임 1개 = JSON 객체 1개 (UTF-8)
> 방향: Python(클라이언트) → Spring(서버). 인증은 연결할 때 공유 비밀키(API 키)로 한다 (설정 결정 #23)

## 1. classification 메시지 (시행 하나의 판별 결과)

| 칸 | 타입 | 필수 | 의미 | 예 |
|---|---|---|---|---|
| version | 정수 | ○ | 규격 버전. 지금은 1 | 1 |
| type | 문자열 | ○ | 메시지 종류. 지금은 "classification"만 | "classification" |
| seq | 정수 (0 이상) | ○ | 연결 후 보낸 순서 번호. 1씩 증가 | 3 |
| timestamp | 문자열 (ISO-8601, 밀리초) | ○ | Python이 판별을 끝낸 시각 | "2026-11-16T14:20:47.311" |
| subject | 정수 | ○ | 피험자 번호 (시연에서는 3) | 3 |
| intent | 문자열 | ○ | 판별한 의도. "left_hand" 또는 "right_hand" | "right_hand" |
| confidence | 실수 (0.5 ~ 1.0) | ○ | 판별 확신도 = 두 확률 중 큰 값 | 0.513 |
| threshold | 실수 (0.5 ~ 1.0) | ○ | Python이 사용한 기준값 (참고용) | 0.75 |
| decision | 문자열 | ○ | Python의 **제안**. "MOVE" 또는 "ABSTAIN" | "ABSTAIN" |
| true_label | 문자열 또는 null | × | **시연 전용 정답.** 관문 판단에 사용 금지 | "left_hand" |

예시:
{"version": 1, "type": "classification", "seq": 3, "timestamp": "2026-11-16T14:20:47.311",
"subject": 3, "intent": "right_hand", "confidence": 0.513, "threshold": 0.75,
"decision": "ABSTAIN", "true_label": "left_hand"}

## 2. 관문(Spring)의 처리 규칙

### 2-1. 최종 판단
- 관문은 자신이 가진 기준값(세션별 보정값)으로 다시 판단한다:
  confidence >= 관문 기준값 → MOVE, 아니면 → ABSTAIN
- Python의 decision과 관문의 판단이 다르면 **관문 판단을 따르고** 불일치를 기록한다
- true_label은 읽지 않는다 (관제 화면 표시용으로만 전달)

### 2-2. 메시지를 거부하는 경우 → 팔은 움직이지 않음 + 차단 기록
| 경우 | 기록할 사유 코드 (초안) |
|---|---|
| JSON으로 읽을 수 없음 | INVALID_FORMAT |
| version이 1이 아님 | UNSUPPORTED_VERSION |
| 필수 칸이 없거나 타입이 다름 | INVALID_FIELD |
| intent가 left_hand / right_hand가 아님 | INVALID_FIELD |
| confidence가 0.5 ~ 1.0 범위 밖 | INVALID_FIELD |
| seq가 이전보다 작거나 같음 (순서 꼬임, 중복) | OUT_OF_ORDER |
| timestamp가 너무 오래됨 (명령 유효시간 초과, 값은 통신 실험 후 결정) | STALE_COMMAND |

### 2-3. 연결 관련 (다른 문서에서 자세히)
- 마지막 메시지 후 N초 동안 아무것도 오지 않으면 차단 + 기본 자세 (워치독, PACKET_LOSS). N은 통신 실험 후 결정

## 3. 정해지지 않은 것
- 명령 유효시간(STALE_COMMAND 기준), 워치독 N초 → 통신 지연 실험 후
- 신호 품질 메시지(장비 오류 더미 데이터)의 형식 → 관제·RAG 단계에서 추가 (type 추가 예정)
- 시각 기준: Python과 Spring PC의 시계가 다를 수 있음 → 같은 PC에서 시연하면 문제 없음, 다른 PC면 재검토

## 4. 변경 이력
| 버전 | 날짜 | 내용 |
|---|---|---|
| 1 | 2026-11-16 | 최초 작성 (step10 재생기 메시지 기준) |