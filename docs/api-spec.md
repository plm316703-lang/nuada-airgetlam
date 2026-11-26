# API 명세

> 2026-11-26 작성. 컨트롤러 코드(`backend-spring/.../*Controller.java`)와 `auth/SecurityConfig.java`의 권한 규칙을 그대로 옮겼다 (Claude Code 작성).
> 코드를 바꾸면 이 문서도 함께 고친다. WebSocket 메시지의 자세한 규격은 [`message-spec.md`](message-spec.md).

## 1. 공통 규칙

| 항목 | 내용 |
|---|---|
| 주소 | 로컬 `http://localhost:8080`, Docker `http://localhost:8081` |
| 형식 | 요청·응답 모두 JSON (`Content-Type: application/json`). 칸 이름은 camelCase |
| 로그인 | `POST /api/auth/login` 성공 시 **HttpOnly 쿠키 `NUADA_TOKEN`**(JWT, `SameSite=Strict`)을 받는다. 이후 요청은 브라우저가 쿠키를 자동으로 붙인다. 자바스크립트는 토큰을 읽을 수 없다 |
| 증표 수명 | 30분 (`security.jwt-lifetime-minutes`). 사람이 조작하는 동안 화면이 최대 5분에 한 번 `POST /api/auth/refresh`로 새로 받는다. 30분 동안 조작이 없으면 자동 로그아웃 (관제 화면은 세션 진행 중이면 제외) |
| 비밀번호 변경 대기 | 임시 비밀번호로 로그인한 계정의 증표에는 역할 권한이 없다 → `/api/auth/password` 외의 역할 API는 **403** |
| 오류 응답 | 본문은 `{"message": "…"}` |

| 상태 코드 | 언제 |
|---|---|
| 400 | 잘못된 요청 (`IllegalArgumentException`, 입력값 검사 실패) |
| 401 | 로그인 필요, 로그인 실패, 증표 만료 |
| 403 | 역할 권한 없음 |
| 409 | 지금 상태로는 불가능 (`IllegalStateException`, 예: 이미 세션이 진행 중), 중복 데이터 |

### 역할 묶음

| 이름 | 역할 |
|---|---|
| 관제 보기 (MONITOR) | 관제 담당자(OPERATOR), 연구책임자(PRINCIPAL_INVESTIGATOR), 연구자(RESEARCHER) |
| 관제 조작 (CONTROL) | 관제 담당자, 연구책임자 |
| 그 외 | 환자(PATIENT), 보호자(GUARDIAN), 시스템 관리자(SYSTEM_ADMIN) |

## 2. 인증 `/api/auth`

| 메서드 | 주소 | 권한 | 요청 본문 | 응답 |
|---|---|---|---|---|
| POST | `/api/auth/login` | 누구나 | `{email, password}` | 200 `Me` + 쿠키 / 401 (5회 실패 시 15분 잠금) |
| POST | `/api/auth/logout` | 누구나 | 없음 | 204 + 쿠키 삭제 |
| GET | `/api/auth/me` | 로그인 | 없음 | `Me` |
| POST | `/api/auth/refresh` | 로그인 | 없음 | `Me` + 새 쿠키 |
| POST | `/api/auth/password` | 로그인 (변경 대기 포함) | `{currentPassword, newPassword}` | `Me` + 새 쿠키 (새 비밀번호 10자 이상, 임시와 다르게, 이메일 앞부분 포함 금지) |

`Me` = `{email, name, role, home(역할별 첫 화면 주소), mustChangePassword, idleLogoutMinutes}`

## 3. 관제 `/api/dashboard`

| 메서드 | 주소 | 권한 | 요청 본문 | 응답 |
|---|---|---|---|---|
| GET | `/api/dashboard/state` | 관제 보기 | 없음 | 화면 첫 상태: `{arm, session, decisions(최근 50), errors(최근 20), controls(최근 20), config(관문 기준값), injectionKinds}` |
| POST | `/api/dashboard/session/start` | 관제 조작 | `{patientId}` (연구 참여 중인 환자만) | `ArmView` |
| POST | `/api/dashboard/session/end` | 관제 조작 | 없음 | `ArmView` |
| POST | `/api/dashboard/arm/resume` | 관제 조작 | 없음 | `{resumed, blockers[]}` — 연속 정상 3개 등 조건을 **서버가** 다시 검사. 막히면 `resumed:false`와 사유 목록 |
| POST | `/api/dashboard/arm/reset` | 관제 조작 | 없음 | `ArmView` (기본 자세 90°, 재개는 따로) |
| POST | `/api/dashboard/inject` | 관제 조작 + 시연 모드 | `{kind}` | 시연용 오류 주입 → 재생기에 명령 전송, `INJECT` 기록 |

- `kind`: `electrode`(전극 불량) `noise`(전원 잡음 60Hz) `muscle`(근육 잡음) `mismatch`(판단 불일치) `stale`(오래된 명령) `disconnect`(끊김 5초) `reset`(끊김 12초)
- 시연 모드는 `DEMO_INJECTION_ENABLED`(기본 true). 끄면 `injectionKinds`가 비고 주입 요청은 거절된다
- `ArmView` = `{state(IDLE|RUNNING|STOPPED|RESET_REQUIRED), angle, stopReason, stoppedAt, sessionId, patientCode, datasetSubject, lastMessageAgoMs, consecutiveClean, requiredClean}`

## 4. 환자·연구 관리

| 메서드 | 주소 | 권한 | 요청 본문 | 응답 |
|---|---|---|---|---|
| GET | `/api/patients` | 관제 보기 | 없음 | 환자 목록 (연구 코드, 등록·동의·참여 여부). 실명은 연구책임자(등록된 환자)·연구자(배정된 환자)에게만, 관제 담당자에게는 없음 |
| POST | `/api/patients` | 연구책임자 | `{name, email, amputationSide}` | 환자 등록 + 초대 `InviteView` |
| POST | `/api/patients/{id}/guardians` | 연구책임자 | `{name, email, relation, identityChecked}` (신분 확인 필수) | 보호자 초대 `InviteView` (기존 보호자면 연결만, `token` null) |
| POST | `/api/patients/invitations/{userId}` | 연구책임자 | 없음 | 다시 초대 `InviteView` (이전 링크는 무효) |
| POST | `/api/patients/{id}/enroll` | 연구책임자 | 없음 | 연구 등록 |
| POST | `/api/patients/{id}/assignments` | 연구책임자 | `{researcherId}` | 연구자 배정 id |
| GET | `/api/researchers` | 연구책임자 | 없음 | `[{id, name}]` |

`InviteView` = `{userId, email, role, token, expiresAt}` — `token`(초대 비밀값)은 **이 응답에만** 담긴다. DB에는 SHA-256 해시만 저장, 72시간·1회용.

## 5. 초대 수락 (로그인 전)

| 메서드 | 주소 | 권한 | 요청 본문 | 응답 |
|---|---|---|---|---|
| POST | `/api/invitations/preview` | 누구나 | `{token}` | 초대 내용 미리 보기 (만료·사용됨이면 오류) |
| POST | `/api/invitations/accept` | 누구나 | `{token, password}` | 비밀번호를 정해 가입 완료 |

토큰은 주소창이 아니라 링크의 `#` 뒤에 두어 서버 기록·Referer에 남지 않게 했다.

## 6. 내 정보 (환자·보호자)

| 메서드 | 주소 | 권한 | 응답 |
|---|---|---|---|
| GET | `/api/me/patient` | 환자 | `{name, researchCode, amputationSide, enrolled, consent, consentedAt, consentWithdrawnAt, participating, arm, todayStops}` |
| POST | `/api/me/patient/consent` | 환자 | 연구 동의 |
| POST | `/api/me/patient/withdraw` | 환자 | 동의 철회 → 연구 관련 데이터 삭제, `{deletedAssignments}` |
| GET | `/api/me/guardian/patients` | 보호자 | 연결된 환자 목록 (상태·오늘 멈춘 기록) |

`arm.message`와 `todayStops[].reason`은 서버가 **쉬운 말**로 바꿔 보낸다 (`FriendlyStopReason`). 기술 문구는 이 API로 나가지 않는다.

## 7. 관리자 `/api/admin`

| 메서드 | 주소 | 권한 | 요청 본문 | 응답 |
|---|---|---|---|---|
| GET | `/api/admin/users` | 시스템 관리자 | 없음 | 계정 목록 (역할, 비밀번호 상태, 잠김 여부) |
| POST | `/api/admin/users` | 시스템 관리자 | `{email, name, role}` (관제 담당자·연구자·연구책임자만) | `{account, tempPassword, expiresAt}` — 임시 비밀번호 12자는 **이 응답에만** |
| POST | `/api/admin/users/{id}/temp-password` | 시스템 관리자 | 없음 | 임시 비밀번호 재발급 (잠금도 해제) |
| POST | `/api/admin/users/{id}/unlock` | 시스템 관리자 | 없음 | 잠금 해제 |
| GET | `/api/admin/login-history?failuresOnly=false` | 시스템 관리자 | 없음 | 최근 100건: 로그인 성공·실패, 계정 발급·재발급·변경·잠금 해제·초대 |

## 8. WebSocket

| 주소 | 방향 | 인증 | 내용 |
|---|---|---|---|
| `/ws/classifier` | 재생기(Python) → Spring | 연결 헤더 `X-API-Key` = `GATEWAY_API_KEY` | 시행마다 판별 메시지 1개 (규격 v2). 같은 연결로 Spring → 재생기 오류 주입 명령도 보냄 ([`message-spec.md`](message-spec.md) 5·6장) |
| `/ws/dashboard` | Spring → 관제 화면 | 로그인 쿠키, 관제 보기 역할 | 실시간 소식: `decision`, `arm`, `error`, `control` 등 |

## 9. RAG 서버 (FastAPI, Spring만 부름)

| 메서드 | 주소 | 요청 | 응답 |
|---|---|---|---|
| GET | `/health` | 없음 | `{ready, error, embedModel, model, manuals}` |
| POST | `/guide` | `{errorType, detail, signalQuality, injected, history[≤10]}` | `{answer, sources[{doc, section, score}], evidenceSufficient, model}` |

- 실패 처리: 색인 준비 안 됨 503, 근거 검색 실패 502, **안내문 생성이 실패하거나 25초를 넘겨도 찾은 근거는 200으로 돌려준다**
- 같은 오류 종류의 근거 유사도가 0.55 미만이면 `evidenceSufficient:false`, 답 첫 줄에 "근거 부족"
- RAG는 안내만 한다. 팔의 상태를 바꾸는 API는 없다
