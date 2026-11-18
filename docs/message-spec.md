# 메시지 규격: Python 판별 서버 → Spring 관문

> 버전: 2 / 작성: 2026-11-16, 개정: 2026-11-17 / 상태: 구현됨 (realtime/replayer.py ↔ GateService)
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
- **Python의 decision과 관문의 판단이 둘 다 MOVE일 때만 MOVE** (페일세이프: 이상하면 안전한 쪽으로 멈춘다)
- 둘이 다르면 ABSTAIN으로 처리하고 불일치를 기록한다 → 사유 코드 DECISION_MISMATCH
    - 판단이 엇갈렸다는 것은 Python 서버 쪽 이상의 신호이므로, 정상 보류와 달리 관제 화면에 경고로 표시한다
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

- 새 WebSocket 연결이 생기면 관문은 기대하는 seq를 초기화한다 (첫 메시지의 seq를 기준으로 삼음)
- 보류(ABSTAIN)는 정상 동작, 거부(차단 기록)는 이상 상황으로 구분해 기록한다

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
| 1 | 2026-11-16 | 페일세이프 판단 규칙(DECISION_MISMATCH), 재연결 시 seq 초기화, 보류·거부 구분 추가 |

---

## 5. 버전 2 (2026-11-17) — 현재 규격

버전 1에서 바뀐 칸만 정리한다. 관문은 **버전 2만** 받는다 (v1 메시지는 신호 품질이 없어 `UNSUPPORTED_VERSION`으로 거부).

| 칸 | 타입 | 필수 | 의미 | 예 |
|---|---|---|---|---|
| version | 정수 | ○ | **2** | 2 |
| timestamp | 문자열 (ISO-8601 + **시간대**) | ○ | 판별을 끝낸 시각. 시간대가 없으면 `INVALID_FIELD` (Docker 컨테이너 UTC와 PC 한국 시간이 섞여도 안전) | "2026-11-17T20:14:34.604+09:00" |
| signal.quality | 실수 0~1 | ○ | 신호 품질 (실제 신호에서 계산: 평탄·튀는 채널, 60Hz, 40Hz 이상 비율) | 0.98 |
| signal.issue | 문자열 또는 null | × | 판별기가 추정한 문제: ELECTRODE_CONTACT / LINE_NOISE / MUSCLE_ARTIFACT | "LINE_NOISE" |
| signal.bad_channels | 문자열 배열 | × | 문제 채널 이름 | ["C3","Cz","C4"] |
| scenario | 문자열 또는 null | × | **시연용 오류 주입** 이름. 있으면 화면·기록에 "주입" 표시 | "electrode" |

예시:
{"version": 2, "type": "classification", "seq": 13, "timestamp": "2026-11-17T20:58:01.120+09:00",
"subject": 3, "intent": "right_hand", "confidence": 0.62, "threshold": 0.75, "decision": "ABSTAIN",
"signal": {"quality": 0.1, "issue": "ELECTRODE_CONTACT", "bad_channels": ["C3", "Cz", "C4"]},
"scenario": "electrode", "true_label": "left_hand"}

### 5-1. 관문 검사 순서와 결과

| 순서 | 검사 | 거부 사유 | 장비 오류 기록(RAG) |
|---|---|---|---|
| ① | JSON 형식 | INVALID_FORMAT | INVALID_MESSAGE |
| ② | 칸·범위·버전 | INVALID_FIELD / UNSUPPORTED_VERSION | INVALID_MESSAGE |
| ③ | seq 순서 | OUT_OF_ORDER | INVALID_MESSAGE |
| ④ | 명령 유효시간 **2초** | STALE_COMMAND | STALE_COMMAND |
| ⑤ | 열린 세션 + 피험자 일치 | NO_ACTIVE_SESSION / SUBJECT_MISMATCH | (정책 차단, 기록 안 함) |
| ⑥ | 참여 중인 환자 (등록 + 동의) | NOT_PARTICIPATING | (정책 차단, 기록 안 함) |
| ⑦ | 신호 품질 **≥ 0.6** | SIGNAL_QUALITY | issue 종류 또는 SIGNAL_QUALITY_LOW |
| ⑧ | 확신도 기준 **0.75**로 다시 판단, 판별기와 일치 | DECISION_MISMATCH | DECISION_MISMATCH |
| 워치독 | 메시지 없음 **3초** → 정지, **10초** → 초기화 필요 | PACKET_LOSS | PACKET_LOSS |

통과한 메시지의 결과: `ABSTAIN`(정상 보류) / `MOVE`(팔 동작) / `HELD`(정상이지만 팔이 정지 상태라 실행 안 함).
거부되면 `BLOCKED` + 팔 **그 자리 정지**. 자동 재개는 없고, 관제 담당자·연구책임자가 수동 재개를 요청하면
서버가 "정지 상태 · 참여 환자 · 데이터 수신 중 · 연속 정상 3개"를 다시 검사한다. 기준값은 `application.yaml`의 `gateway.*`.

### 4-1. 변경 이력 (추가)
| 버전 | 날짜 | 내용 |
|---|---|---|
| 2 | 2026-11-17 | signal(신호 품질), scenario 추가, timestamp 시간대 필수, 명령 유효시간 2초·워치독 3초/10초 결정, 검사 ④~⑦ 구현 |
