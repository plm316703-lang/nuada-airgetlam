# CLAUDE.md — Nuada Airgetlám

이 파일은 Claude Code가 작업을 시작할 때 먼저 읽는 프로젝트 안내서입니다.
여러 컴퓨터(학원 PC, 집 PC)에서 이어서 작업하므로, **7. 현재 진행 상황**과 `docs/roadmap.md`를 기준으로 이어 간다.

---

## 1. 나에 대해 (학습자 정보)

- 프로그래밍 경력 1년 미만의 초보 개발자, 국비 과정 수강 중 (Java·Spring·Python)
- 목표: 2027년 3월까지 개발자 취업 (RAG 분야 관심), 장기적으로 BCI(뇌-컴퓨터 인터페이스) 분야
- 이 프로젝트는 **"Claude Code로 프로젝트가 어떻게 만들어지는지" 보는 첫 실험**이다. (2026-11-20 결정)
  - 국비 과정의 협업 프로젝트에서 나와 혼자 진행. **발표 없음**. 처음엔 11/22까지였으나 **11/20에 기한 해제** (계획한 기능을 다 하기로)
  - 결과물보다 **실험 기록**(AI가 잘한 것 / 틀린 것·내가 잡은 것 / 내가 결정한 것)이 중요
  - GitHub 저장소는 **Public** (11/20 확인, 공개 유지 결정). README 맨 위에 AI 활용 실험임을 밝힘
  - 다음 주(11/23~)에는 별도로 앱을 만들 예정

## 2. Claude에게 부탁하는 진행 방식 (가장 중요)

실험 목적상, **Claude Code가 직접 구현**하고 나는 설계를 결정하고 결과를 확인한다.

1. **Claude Code가 코드를 직접 작성·수정한다.** 다만 한 번에 몰아서 하지 않고, 아래 순서로 **작은 단계씩** 진행한다.
   계획(무엇을 왜 바꾸는지) → 구현 → 실행·확인 → 다음 단계
2. **설계 판단이 필요한 부분은 Claude가 정하지 말고 나에게 먼저 묻는다.** (내 결정 / AI 제안 구분)
   예: DB에 저장할지, 화면 어디에 둘지, 시간 제한을 몇 초로 할지
3. 단계마다 **무엇을 바꿨고 왜 그렇게 했는지** 한국어로 짧게 설명한다. (큰 그림 → 세부)
   **변경 사항은 코드로 보여 주지 말고** 말로 간략히 설명한다. (어느 파일의 무엇을, 왜)
4. 코드에는 **한국어 주석**을 단다. "무엇을 바꿨는지"가 아니라 **"왜 이렇게 했는지"**를 쓴다.
   (`// ← 추가` 같은 변경 표시는 쓰지 않는다. 변경 내역은 커밋과 PR에 남긴다)
5. **영어 식별자는 한국어로 풀어서** 설명한다. 예) `ResearcherAssignmentRepository` → "연구자 배정 저장소"
6. 실행 확인은 **내가 직접** 한다. Claude는 확인 방법(명령, 볼 곳, 기대 결과)을 알려 준다.
7. 커밋·푸시·PR은 **내가 요청할 때만** 한다. PR 본문 초안은 Claude가 쓴다.
8. 20년 차 개발자가 1년 미만 초보에게 설명하듯, 처음 나오는 개념은 짧게 풀어 준다.

### 2-1. 가이드 모드 (2026-11-20 결정, **2단계 로드맵 전체에 적용**)

1차 실험(Claude가 구현)의 회고에서 나온 "블랙박스" 문제에 대한 2차 실험이다.
이 모드에서는 위 1번(Claude가 직접 구현)과 7번(커밋·PR)을 대신해 아래를 따른다.

1. **작업은 내가 직접 한다.** 코드·설정 파일·GitHub 설정·서버 작업·커밋·PR 모두 내가 한다.
2. Claude의 역할: **다음 할 일을 작은 단계로 안내 → 개념 설명 → 이해 점검 질문 → 내가 한 결과 리뷰**(무엇이 왜 문제인지, 정답 대신 방향).
3. 막히면 **힌트 → 그래도 막히면 아주 짧은 예시(몇 줄)**까지만. 파일 전체를 써 주지 않는다.
4. 리뷰할 때 기존 코드(예: `rag/rag_server.py`)·현업 관례와 비교해서 다른 점을 짚어 준다.
5. Claude가 직접 해도 되는 것: **결정 기록·안내 문서 정리**(CLAUDE.md, roadmap, next-session, portfolio-log), 상태 확인(읽기 전용 조회).
   그 외 파일·설정 변경은 내가 요청할 때만.
6. 이 모드를 끝낼 때는 내가 말한다.

## 3. 프로젝트 소개

- **이름:** Nuada Airgetlám (아일랜드 신화의 은팔 왕 누아다에서 따옴)
- **한 줄 설명:** 뇌파 판별 결과를 **안전 관문**이 다시 검사해 가상 로봇팔을 움직이고,
  장비 오류가 나면 그 자리에서 정지 + 기록 + **RAG가 매뉴얼 근거로 점검 방법을 안내**하는 관제 시스템
- **흐름:** 명령 수신 → 관문 검사 → 팔 동작 → 오류 발생 → 정지·차단 기록 → RAG 점검 안내 → 조건 확인 후 **수동 재개**
- **사용자(역할):** 관제 담당자, 연구책임자, 환자, 보호자, 관리자 (시연 계정 `@nuada.test`)
- **현업 도구와의 위치:** OpenBCI GUI·BrainVision Recorder는 장비층("신호를 보는 도구"),
  이 프로젝트는 그 위의 관제·안전층("신호로 움직이는 장치를 안전하게 관제하는 도구")

## 4. 기술 스택과 구조

- Spring: Java 21, Spring Boot 4, Gradle, Spring Data JPA, Spring Security(JWT), WebSocket, MySQL 8, Lombok, JUnit
- Python: 3.11 (conda `nuada-bci` 또는 `ai-server-python/.venv`), MNE, MOABB, scikit-learn, FastAPI, Chroma, Gemini API
- RAG: **LangChain 없이 직접 구현** (chromadb + google-genai + FastAPI)
- 실행: Docker Compose(발표용) 또는 로컬 직접 실행 (`docs/local-run.md`, `docs/deploy.md`)
- IDE: IntelliJ (Gradle 프로젝트는 `backend-spring` 폴더 기준)

```
nuada-airgetlam/
├── backend-spring/src/main/java/io/github/plm316703/backend/
│   ├── gateway/    # 관문: WebSocket 수신, 메시지 검사(형식·칸·순서·신선도·판단 일치), GateService(본체)
│   ├── arm/        # 로봇팔 상태·워치독·정지/재개/초기화
│   ├── record/     # 기록: GateDecision(판단), ControlEvent(관제 동작), DeviceError(장비 오류)
│   ├── rag/        # RAG 서버 호출 (새 오류가 생기면 비동기로 안내 요청)
│   ├── session/    # 측정 세션
│   ├── dashboard/  # 관제 화면 API + 실시간 전송(DashboardHub), Views(화면 전송용 모양)
│   ├── auth/       # 로그인(JWT), 보안 설정
│   ├── patient/    # 환자, 보호자 연결, 연구자 배정, 연구 동의
│   ├── user/       # 사용자, 역할
│   └── config/     # 비동기, 예외 처리, 시연 계정 생성
├── backend-spring/src/main/resources/static/   # 화면 (dashboard, patient, guardian, admin, login)
│   └── js/vendor/chart.umd.min.js              # Chart.js 4.4.1 (CDN 대신 저장소에 포함)
├── ai-server-python/
│   ├── classifier/  # 판별기 학습 단계별 스크립트 (CSP+LDA, 피험자 3, 기준값 0.75)
│   ├── models/      # 저장된 판별기 model_s3.joblib
│   ├── realtime/    # replayer.py: 공개 데이터 재생 → 판별 → 관문 전송, 오류 주입
│   │                # signal_quality.py: 신호 품질 점수·원인·문제 채널(bad_channels) 계산, 채널 22개
│   └── rag/         # rag_server.py(FastAPI :8000) + manuals/*.md (점검 매뉴얼 8개, 출처 포함)
├── scripts/         # 로컬 실행 스크립트 (run-backend, run-rag, run-replayer, setup-*)
├── docs/            # roadmap, portfolio-log, local-run, deploy, message-spec, ERD 등
└── docker-compose.yml
```

## 5. DB 설계 요약 (테이블 8개 → R3·R4 후 10개)

`users`(R2: 비밀번호 변경 필요·임시 비밀번호 만료 시각, R3: 실패 횟수·잠금 해제 시각), `patients`, `patient_guardians`, `researcher_assignments`, `sessions`,
`gate_decisions`(관문 판단: MOVE/ABSTAIN/HELD/BLOCKED, 연결 id, 피험자 번호, 문제 채널 포함),
`device_errors`(장비 오류 + RAG 안내 결과), `control_events`(정지·재개·초기화·세션),
`login_history`(R3: 로그인 성공·실패 + 계정 발급·재발급·변경·잠금 해제 기록, 보관 방침 1년), `invitations`(R4: 환자·보호자 초대 링크, 비밀값은 SHA-256 해시만, 72시간·1회용)

## 6. 내가 내린 설계 결정 (바꾸기 전에 반드시 나에게 물어볼 것)

- 동의하지 않은 환자의 데이터는 **익명이라도** 연구에 쓰지 않는다.
- 연구 동의를 **철회하면 연구 관련 데이터를 철저히 삭제**한다. (철회 시간 기록과 환자 계정은 유지)
- **RAG는 안내만 한다.** 팔의 재개·상태 변경은 RAG가 하지 않는다.
- **자동 재개는 없다.** 정지된 팔은 연속 정상 데이터 조건을 만족한 뒤 사람이 수동 재개해야 한다.
- 관문은 판별기를 믿지 않는다. 확신도 기준을 관문이 다시 계산한다.
- 관문은 이상한 입력이 들어와도 **기록은 반드시 남긴다** (긴 값은 잘라서 저장).
- 오류 주입(시연)은 재생기의 가짜 데이터만 바꾼다. 팔·관문을 직접 건드리지 않는다.
- RAG Gemini 호출 제한 25초 (Spring의 40초보다 짧게), 생성 실패해도 찾은 근거는 보여 준다.
- RAG 답변은 매뉴얼의 수치·출처를 그대로 옮긴다 (`rag_server.py`의 `SYSTEM_RULES` 7~9번).
- 문제 채널(`bad_channels`)은 DB에 `"C3,Cz,C4"` 글자로 저장, 채널 지도는 **그래프 옆 새 패널**에 둔다.
- 예외: 잘못된 요청은 `IllegalArgumentException`, 지금 상태로 불가능하면 `IllegalStateException`.
- 환자·보호자에게는 정지 사유를 **쉬운 말로만** 보낸다 (서버에서 변환, 기술 문구는 관제 화면에서만).
- 계정은 스스로 가입하지 않고 **관리자가 발급**한다. 임시 비밀번호는 서버가 만들어 한 번만 보여 주고, 첫 로그인 때 바꾸게 한다.
- 로그인 실패 5회 → 15분 잠금, 30분 무조작 자동 로그아웃 (관제 화면은 세션 진행 중이면 제외).

## 7. 현재 진행 상황 (2026-11-20 갱신)

> **세션을 시작하면 `docs/next-session.md`를 먼저 읽는다.**

- ✅ 통합 완성(PR #14), 판단 기록에 연결 id·피험자 번호(PR #16), 오류 주입 7종 리허설 통과
- ✅ A1 Chart.js 저장소 포함 (PR #19)
- ✅ A2 RAG 25초 제한 + 생성 실패 시 근거 표시 (PR #20)
- ✅ A3 RAG 매뉴얼 출처 + 답변 규칙 보강 (PR #21)
- ✅ **B1 채널 상태 지도** (PR #22 머지)
  - ✅ B1-2a `record/GateDecision`에 `badChannels`(String, 길이 200, null 허용) 필드 + `@Builder` 매개변수
  - ✅ B1-2b `gateway/GateService`에 `joinChannels()` 도우미 추가, `decisionOf()`에서 `.badChannels(...)` 저장
    - ✅ 11/20 실행 확인 완료 (`bad_channels` 칸 생성, `e` 주입 시 값 저장)
  - ✅ B1-2c `dashboard/Views.DecisionView`에 `badChannels` 목록 추가 (최근 50개에도 포함, 없으면 빈 목록) — 11/20 실행 확인 완료
  - ✅ B1-3 그래프 옆 새 패널에 22채널 머리 지도 (`dashboard.html`, `dashboard.js`의 `updateChannelMap`, `app.css`) — 11/20 실행 확인
    - 마지막 판단 1개 기준 / 신호 정보 없으면 회색 / 문제 채널 없는 잡음(`n`·`m`)은 머리 윤곽 주황
    - 채널 목록·순서: `ai-server-python/realtime/signal_quality.py`의 `CHANNELS` (BCI IV 2a 몽타주)
  - ✅ B1-4 재생기 `e`·`n`·`m` 주입 시 지도 확인 (11/20)
- ✅ **R1 역할 UI** (PR #23 머지)
  - 서버: `patient/FriendlyStopReason`(쉬운 정지 사유), `MeController`가 `message`·`todayStops`를 보냄,
    `ControlEventRepository.findPatientEventsSince()`(그 환자 세션의 STOP만), `GateService`가 팔 정지 사유에 설명도 담음
  - 화면: `patient.html`·`guardian.html`(지금 상태 한 문장 + 오늘 멈춘 기록), `admin.html`(역할별 인원 + 이메일 찾기)
- ✅ **R2 계정 발급** (PR #24 머지)
  - 서버: `user/User`(mustChangePassword·tempPasswordExpiresAt), `user/PasswordRules`(임시 생성·새 비밀번호 검사),
    `user/AccountService`(발급·재발급·변경), `AdminController`(POST 계정·재발급), `AuthController`(POST /api/auth/password, 만료 검사),
    `JwtService`(pwc 클레임), `SecurityConfig`(pwc 토큰엔 역할 권한을 주지 않음 → 서버가 다른 API 차단)
  - 화면: `password.html`(새), `admin.html`(계정 만들기·재발급·비밀번호 상태), `common.js`(모든 화면에 "비밀번호 변경" 버튼, 변경 대기면 이동)
  - 설정: `application.yaml`의 `account.*` (12자·72시간·10자)
- ✅ **R3 로그인 보안** (PR #25 머지) — 자동 로그아웃 갱신 간격 버그를 직접 시험으로 발견·수정
  - 서버: `User`(failedLoginCount·lockedUntil), `record/LoginHistory`(login_history), `auth/LoginService`(잠금·기록),
    `AuthController`(POST /api/auth/refresh, 로그인은 LoginService로), `AccountService`(발급·재발급·변경·잠금 해제 기록, 재발급 시 잠금도 해제),
    `AdminController`(POST /users/{id}/unlock, GET /login-history)
  - 화면: `common.js`(자동 로그아웃 타이머·1분 전 경고·5분마다 증표 갱신), `dashboard.js`(세션 중 예외), `admin.html`(잠김 표시·해제·기록 표), `login.html`(자동 로그아웃 안내)
  - 설정: `security.jwt-lifetime-minutes` 30 (`JWT_LIFETIME_MINUTES`로 시험용 변경 가능), `account.max-failed-logins` 5, `account.lock-minutes` 15
- ✅ **R4 초대** (PR #26 머지)
  - 서버: `user/Invitation`(invitations, 토큰은 SHA-256 해시만), `patient/InvitationService`(환자 등록·보호자 초대(신분 확인 필수)·다시 초대·수락),
    `PatientAdminController`(POST /api/patients, /{id}/guardians, /invitations/{userId}), `InvitationController`(로그인 전 preview·accept),
    `SecurityConfig`(초대 API permitAll + 쿠키 무시), `LoginHistory`(INVITATION_CREATED·ACCEPTED, 칸을 varchar로)
  - 화면: `dashboard.js`(연구책임자: 환자 등록·보호자 초대·다시 초대·초대 대기 표시), `invite.html`(새, 링크 비밀값은 # 뒤), `login.html`(가입 후 이메일 채움)
  - 설정: `account.invite-hours` 72
  - ⚠ 발견: Hibernate가 MySQL에서 enum을 `enum('A','B')` 칸으로 만든다 → 새 값을 추가하면 저장 실패 위험.
    `@JdbcTypeCode(SqlTypes.VARCHAR)`를 붙이면 서버 시작 때 varchar로 바뀜(확인함). **B2에서 `control_events.type`에 값을 추가할 때도 같은 처리 필요**
- ✅ **B2 오류 주입 버튼** (PR #27 머지)
  - Spring: `gateway/ReplayerCommander`(열린 판별기 연결에 명령 전송), `arm/InjectionService`(시연 모드 검사·INJECT 기록),
    `DashboardController`(POST /api/dashboard/inject, 설정에 injectionKinds), `ControlEvent.Type.INJECT`(칸 varchar로 바뀐 것 확인)
  - 재생기: `replayer.py`의 `command_loop`(받기 스레드, 키보드와 같은 injector 사용)
  - 화면: 로봇팔 패널에 버튼 7개 (조작 역할 + 시연 모드일 때만, 12초 끊김만 확인 창)
  - 설정: `app.demo.injection-enabled` (`DEMO_INJECTION_ENABLED`, 기본 true), 규격: `docs/message-spec.md` 6장
- ✅ **회고·공개 정리** (PR #28·#29·#30, 마무리 PR) — `docs/retrospective.md`, README를 실제 기능으로 갱신, `next-session.md`를 인수인계 문서로
  - 공개 전 점검: `backend-spring.zip`(커밋 58dae63, 옛 로컬 DB 설정·비밀번호 포함) 삭제 + `.gitignore`에 `*.zip`·`application-local.yaml` 추가.
    저장소는 **이미 Public**이었고 zip의 비밀번호는 MySQL **root** 것 → root 비밀번호 변경 완료 (11/20)
  - portfolio-log PR #19~#21 빈칸을 당시 PR 본문 근거로 채움. R2·R4·B2의 "확인 중 이상한 점" 칸은 기억나면 내가 보완
- 👉 **지금: 2단계 로드맵 (가이드 모드 2-1장, 상세 `docs/roadmap.md` "2단계")**
  - 순서: **① 협업 기반**(Issues·Projects, PR 템플릿, CI, 브랜치 보호) → ② LangChain RAG + 평가 세트 → ③ 배포(무료 우선) + CD + 모니터링 → ④ PWA(보호자 알림) → ⑤ 모바일 앱
  - 다음 주 "별도 앱" 계획은 **이 프로젝트를 앱으로 만드는 것(④·⑤)으로 변경**
  - ✅ A0(LangChain 준비) 머지 (PR #33): `rag_langchain/README.md`·`requirements.txt`, `.venv`는 로컬에만(무시됨)
  - ✅ 중간 점검 정리 (11/21, 커밋 전): 옛 문서 3개(learning-log·project-overview·service-plan), 옛 재생 스크립트(step10·step12_replay_ws·step12_connect_test), 빈 `.gitkeep` 삭제.
    `step12_reject_test.py`는 CI 자동 테스트 재료로 보류
    - 위치 `ai-server-python/rag_langchain/`, 별도 서버 포트 8001, `/guide` 응답 모양은 기존과 같게, 별도 가상환경
    - 단계: A1 개념 → A2 자르기·색인 → A3 검색 비교 → A4 프롬프트·생성 → A5 실패 처리·API → A6 비교표
- ✅ **Docker 전체 실행 다시 확인** (PR #32) — 11/17 확인(deploy.md 7-2) 이후 변경분 반영.
  Compose에 `DEMO_INJECTION_ENABLED`·`JWT_LIFETIME_MINUTES` 전달, Gemini 기본값 flash-lite, 오류 주입 안내를 버튼으로.
  **Docker 화면 주소는 `http://localhost:8081`** (다른 Docker 프로젝트 `demo`가 8080을 써서 11/20 변경, 로컬 실행은 그대로 8080)
- 나중에: 전체 실행 스크립트(`scripts/run-all.ps1`), C1 주파수 화면, C2 LSL, RAG LangChain 재구현(직접 작성, Claude는 리뷰만)

## 8. 작업 규칙

- **비밀 정보:** 모든 비밀 값은 저장소 최상위 `.env`에만 둔다 (`.gitignore` 대상, 절대 커밋하지 않음).
  새 컴퓨터에서는 `.env.example`을 복사해 채운다. 키 값을 채팅·캡처에 노출하지 않는다.
- **Gemini 모델:** `.env`에 `GEMINI_MODEL=gemini-3.1-flash-lite` (기본값 3.8-flash는 2026-11-19 극도로 느림)
- **IntelliJ 실행 설정에 환경 변수를 넣지 않는다** (`.env`보다 우선해서 값이 어긋남)
- **테스트 데이터:** 실제 이메일·이름 금지, 가짜 값만 사용.
- **Git 흐름:** main 최신화 → 새 브랜치(`feature/...`, `chore/...`, `docs/...`) → 작업·실행 확인 → 커밋·푸시 → PR → 머지 → 로컬 정리
- **커밋 메시지:** `feat:` / `fix:` / `test:` / `docs:` / `refactor:` / `chore:` + 한국어 설명
- 커밋 전에는 항상 `git status`로 확인하고, `git add .` 대신 파일을 지정한다.
- PR마다 `docs/portfolio-log.md`에 기록을 남긴다 (문제 / AI 도움 / 내 판단과 행동 / 검증 / 증거 / 한계 / 상태).
- 매뉴얼 숫자(3초·10초·2초·0.75)는 `application.yaml`과 같아야 한다. 설정을 바꾸면 매뉴얼도 고친다.

## 9. 자주 겪은 문제 (새 컴퓨터에서 먼저 확인)

| 증상 | 원인 | 해결 |
|---|---|---|
| `Could not resolve placeholder 'JWT_SECRET'` | `.env` 없음 | `.env.example` 복사 후 값 채움 |
| `No module named 'mne'` | 다른 Python이 실행됨 | `conda activate nuada-bci` 또는 `.venv` 확인 |
| `GATEWAY_API_KEY가 없습니다` | `python-dotenv` 미설치로 `.env`를 조용히 건너뜀 | `pip install python-dotenv==1.2.4` |
| 재생기 `HTTP 401` | Spring과 재생기의 `GATEWAY_API_KEY`가 다름 | 실행 설정의 환경 변수 삭제, `.env` 하나로 |
| `Field 'decided_at' doesn't have a default value` | 옛 버전이 만든 `gate_decisions` 칸이 남음 (`ddl-auto: update`는 추가만 함) | `DROP TABLE gate_decisions` 후 재시작 |
| 오류 카드 "RAG 안내 생성 실패" | Gemini 생성 모델이 느림 (25초 제한) | `.env`의 `GEMINI_MODEL` 변경 |
| 재생기·RAG 창 출력이 멈춤 | Windows 콘솔 빠른 편집 모드(창 클릭 시 "선택") | Esc, 발표 전 빠른 편집 모드 끄기 |
| PowerShell에서 RAG 응답 한글이 깨짐 | PowerShell 5.1이 UTF-8 응답을 잘못 읽음 | `Invoke-WebRequest -UseBasicParsing` 후 `[Text.Encoding]::UTF8.GetString($resp.RawContentStream.ToArray())` |
