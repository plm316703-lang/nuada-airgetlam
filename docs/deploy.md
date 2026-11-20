# 배포 안내 — Docker Compose

> 명령 한 줄(`docker compose up --build`)로 **MySQL + RAG + Spring 관문(화면 포함) + Python 재생기**를 한 번에 띄운다.
> 배포 전에 내 PC에서 직접 실행해 보는 방법은 [local-run.md](local-run.md).
> 발표 시연용이며, 클라우드(EC2) 배포는 아직 범위 밖이다.

---

## 1. 큰 그림

```
 ┌──────────────────────────── docker compose ─────────────────────────────┐
 │                                                                          │
 │  [replayer] ──WebSocket(X-API-Key)──▶ [backend] ──JDBC──▶ [mysql]         │
 │   판별 + 신호 품질 (1초마다, 계속)       관문 + 화면 :8080     MySQL 8.4     │
 │                                           │  ▲                           │
 │                         오류 시 POST /guide │  │ 점검 안내 (안내만)          │
 │                                           ▼  │                           │
 │                                         [rag] FastAPI + Chroma + Gemini   │
 └──────────────────────────────────────────────────────────────────────────┘
   내 PC에서 보이는 포트: localhost:8081 (화면·관문, 컨테이너 안에서는 8080), 127.0.0.1:3307 (DBeaver)
```

시작 순서(healthcheck): `mysql` 준비 → `backend` 8080 열림 → `replayer` 시작. `rag`는 따로 뜬다.

## 2. 파일 구성

| 파일 | 역할 |
|---|---|
| `docker-compose.yml` | 네 서비스, 볼륨(`mysql-data`, `mne-data`, `rag-data`), 포트 |
| `.env.example` / `.env` | 환경 변수 견본(커밋) / 실제 비밀 값(**커밋 안 함**) — 로컬 실행과 공용 |
| `backend-spring/Dockerfile` | JDK로 bootJar 빌드 → JRE 이미지에 jar만 (일반 사용자로 실행) |
| `ai-server-python/Dockerfile` | 재생기 (`realtime/replayer.py`, 규격 v2) |
| `ai-server-python/Dockerfile.rag` | RAG 서버 (`rag/rag_server.py` + `rag/manuals/*.md`) |

## 3. 실행

```powershell
Copy-Item .env.example .env   # 처음 한 번, 값 채우기 (local-run.md 2-2 표 참고)
docker compose up -d --build
```

브라우저 `http://localhost:8081` → 시연 계정으로 로그인 (`operator@nuada.test` 등, 비밀번호 = `.env`의 `DEMO_PASSWORD`).

### 시연 중 오류 주입 (Docker)

**관제 화면의 로봇팔 패널 버튼 7개**로 넣는다 (B2, 2026-11-20). 관제 담당자·연구책임자만 보이고,
`.env`의 `DEMO_INJECTION_ENABLED=false`면 버튼이 사라진다. Spring이 같은 WebSocket으로 재생기에 명령을 내려보낸다.

키보드로 넣고 싶을 때(예전 방식):

```powershell
docker attach nuada-airgetlam-replayer-1
```

붙은 상태에서 `e`(전극) `n`(전원) `m`(근육) `x`(불일치) `s`(오래된 명령) `d`(5초 끊김) `r`(12초 끊김) + Enter.
빠져나올 때는 **Ctrl+P, Ctrl+Q** (Ctrl+C를 누르면 재생기가 꺼짐 → 3초 뒤 워치독 정지가 시연됨).

> Docker의 MySQL(3307)은 로컬 MySQL(3306)과 **별개 DB**다. 시연 계정은 새로 만들어지고, 로컬에서 만든 계정·기록은 없다.
> 내 PC 쪽 포트는 **8081**이다 (11/20 결정). 이 PC에서 다른 Docker 프로젝트(`demo`)가 8080을 쓰고, Docker Desktop을 켜면 함께 자동으로 뜨기 때문.
> 덕분에 로컬 실행(IntelliJ Spring, 8080)과도 겹치지 않는다. 다만 Docker와 로컬을 **동시에** 켜면 재생기 두 개가 각자 다른 Spring에 붙으니 하나만 켠다.

### 자주 쓰는 명령

```powershell
docker compose logs -f backend               # 관문 판정·거부 로그
docker compose ps                            # 상태
docker compose down                          # 정리 (DB·데이터셋·RAG 색인은 볼륨에 남음)
docker compose down -v                       # ⚠ 볼륨까지 삭제 (DB 데이터 사라짐)
```

## 4. DBeaver 접속

| 항목 | 값 |
|---|---|
| Host / Port | `127.0.0.1` / `3307` |
| Database / User / Password | `.env`의 `MYSQL_DATABASE` / `MYSQL_USER` / `MYSQL_PASSWORD` |
| Driver properties | `allowPublicKeyRetrieval = true` |

테이블 10개: `users`, `patients`, `patient_guardians`, `researcher_assignments`, `sessions`, `gate_decisions`, `device_errors`, `control_events`,
`login_history`(R3), `invitations`(R4).

## 5. 설계 결정 (내 결정 / AI 제안)

| 항목 | 결정 | 누가 |
|---|---|---|
| 배포 방식 | Docker Compose, 로컬 시연 / 그 전에 로컬 직접 실행 | 내 결정 |
| 재생기 | compose up 할 때 항상 함께, 끝없이 재생 | 내 결정 |
| 비밀 정보 | `.env` + `.env.example` | 내 결정 |
| DB 포트 | `127.0.0.1:3307`에만 | 내 결정 |
| 시간 기준 | 명령 2초 / 정지 3초 / 초기화 10초 | 내 결정 |
| 재개 권한 | 관제 담당자 + 연구책임자 | 내 결정 |
| 로그인 토큰 | HttpOnly + SameSite=Strict 쿠키 | 내 결정 (AI 추천) |
| RAG | Chroma + Gemini 필수, Python FastAPI 서버 | 내 결정 |
| 오류 주입 | 재생기 시나리오 옵션 + 실행 중 키 입력 | 내 결정 (키 입력은 AI 제안) |
| 신호 품질 계산식 (채널 감점, 60Hz·40Hz 비율, 기준 0.6) | 연구용 근사치 | AI 제안 — 확인 필요 |
| 재개 조건 "연속 정상 3개" | 짧은 끊김 뒤 유효 데이터 확인 | AI 제안 — 확인 필요 |
| 빌드 중 테스트 생략, 멀티 스테이지, 일반 사용자 실행 | | AI 제안 |

## 6. 문제 해결

| 증상 | 원인 | 해결 |
|---|---|---|
| backend가 `gateway.api-key가 비어 있습니다` / `JWT_SECRET …`로 종료 | `.env` 값 없음·짧음 | `.env` 확인 |
| replayer `연결 거절: HTTP 401` | API 키 불일치 | 같은 `.env` 값 확인 |
| 오류 카드 "RAG 안내 실패 503" | `GEMINI_API_KEY` 없음 | `.env`에 넣고 `docker compose up -d rag` |
| `.env` 비밀번호를 바꿨는데 DB 로그인 실패 | MySQL은 볼륨이 **처음 만들어질 때만** 계정 생성 | `docker compose down -v` 후 다시 (데이터 삭제 주의) |
| `port is already allocated` | 로컬로 띄운 Spring/MySQL이 같은 포트 사용 | 로컬 서버를 끄거나 포트 변경 |

## 7. 실행 검증 기록

### 7-1. 1차 (2026-11-17, 재생기 v1 10개 전송)
MySQL → Spring → 재생기 순서 기동, `[인증 성공]`, MOVE 7 / ABSTAIN 3, 종료 코드 0. (당시 테이블 4개)

### 7-2. 2차 (2026-11-17, 완성품 — Docker 29.9.3 / Compose v5.7.0)

| 확인 항목 | 결과 |
|---|---|
| 단위·통합 테스트 `gradlew test` (MySQL 컨테이너) | ✅ 29개 통과 (기존 14 + 새 안전 규칙 15) |
| 이미지 빌드 4개, 기동 | ✅ |
| 로그인 없이 `/api/dashboard/state` | ✅ 401 |
| 환자 계정으로 관제 API | ✅ 403 |
| 관제 담당자 환자 목록 | ✅ 등록 환자만, 실명 없음(익명 코드) |
| 비참여 환자로 세션 시작 | ✅ 409 "참여 중인 환자만…" |
| 세션 시작 직후 재개 | ✅ 거절 (데이터 없음, 연속 정상 0/3) → 3개 후 재개 성공 → MOVE로 팔 동작 |
| 재생기 정지 | ✅ 3.1초 뒤 PACKET_LOSS 정지, 10초 뒤 초기화 필요 → 초기화 → 재개 |
| 전극 불량 주입 | ✅ 품질 0.1 → SIGNAL_QUALITY 차단, 그 자리(110°) 정지, ELECTRODE_CONTACT 기록, 즉시 재개 거절, 회복 후 재개 시 오류 해결 처리 |
| 전원 잡음 / 판단 불일치 / 오래된 명령 주입 | ✅ LINE_NOISE / DECISION_MISMATCH / STALE_COMMAND 로 정지·기록 |
| 서버 재시작 | ✅ 열린 세션을 **정지 상태로** 이어받음 (자동 재개 없음) |
| 동작 중 환자가 동의 철회 | ✅ 즉시 정지(NOT_PARTICIPATING), 연구자 배정 1건 삭제, 재개 거절, 다음 메시지 BLOCKED |
| 역할별 열람 | ✅ 연구자: 철회 환자 안 보임 → 재동의 후 코드만(배정 삭제됨) / 연구책임자: 등록 환자 실명 / 보호자: 연결 환자 상태, 관제 403 |
| 관제 화면 (브라우저) | ✅ 실시간 연결, 은색 로봇팔, 그래프, 판단 기록, 오류 카드, 주입 표시 |
| RAG 요청 전달 | ✅ Spring → RAG 요청 도달 (HTTP/1.1 고정 후) |
| RAG 안내문 생성 (Gemini) | ✅ 2026-11-18 **로컬 실행**(JDK 21 + MySQL 8.4 + venv)에서 확인 — 전극 불량 주입 → 8단계 점검 안내, 근거 번호 인용, "근거 충분", 근거 유사도 0.83~0.85. 마지막 줄은 규칙대로 "관제 담당자가 수동 재개를 요청"으로만 표현 |
| Gemini 모델 | `gemini-2.5-flash`가 신규 사용자에게 종료(404) → 기본값을 `gemini-3.8-flash`로 변경. 바뀌면 `.env`의 `GEMINI_MODEL`로 덮어쓰기 |
| (11/20 갱신) | 3.8-flash가 11/19 극도로 느려 `docker-compose.yml` 기본값을 `gemini-3.1-flash-lite`로 바꿈 |

### 7-3. 3차 (2026-11-20, A1~B2 반영 후 다시 실행)

11/17 이후 Chart.js 포함, RAG 25초 제한, 채널 지도, 역할 화면, 계정 발급·로그인 보안·초대, 오류 주입 버튼이 들어가서 다시 확인한다.
Compose에 `DEMO_INJECTION_ENABLED`·`JWT_LIFETIME_MINUTES` 전달을 추가했다.

| 확인 항목 | 결과 |
|---|---|
| 이미지 빌드 4개 | ✅ (backend·replayer·rag 빌드, mysql:8.4 받기) |
| 첫 기동 | ❌ → ✅ 8080 충돌: 다른 프로젝트 `demo` 컨테이너가 Docker Desktop과 함께 자동 기동해 8080 사용 → 내 PC 포트를 **8081**로 변경 |
| 기동·헬스체크 | ✅ mysql healthy → backend healthy(12초, 시연 계정 8개 생성) → replayer 시작, rag는 기존 색인 40개 사용 |
| 로그인 화면 `/login.html` | ✅ 200 |
| 로그인 없이 `/api/dashboard/state` | ✅ 401 |
| 재생기 첫 실행 | ✅ 공개 뇌파 데이터 다운로드(nemar, 볼륨에 저장) 후 관문 연결, 1초마다 판별 전송 (MOVE/ABSTAIN) |
| 로그인 → 관제 화면 → 세션 시작 → 수동 재개 → 오류 주입 버튼 | ✅ 직접 확인 (11/20) |
| 로컬에서 만든 계정으로 Docker 로그인 | ❌ 예상된 결과 — Docker DB(3307)는 로컬 DB(3306)와 별개라 로컬에서 만든 계정이 없음 |
| 로컬 DB → Docker DB 옮기기 | ✅ (내 결정) Docker DB 먼저 백업 → 로컬 `mysqldump`(테이블 10개) → Docker DB 비우고 가져오기 → 재기동. users 10 · 판단 7,954 · 로그인 기록 52건, 시연 계정 재생성 없음, 열린 세션은 정지 상태로 이어받음. 로컬에서 만든 계정으로 8081 로그인 ✅ (직접 확인) |

**로컬 DB를 Docker로 옮기는 방법** (다시 할 때): ① Docker DB 백업(`mysqldump`, root) ② 로컬 DB 내보내기(`--single-transaction --no-tablespaces --set-gtid-purged=OFF`)
③ `docker compose stop replayer backend` ④ Docker에서 DB를 지우고 다시 만든 뒤 가져오기 ⑤ `docker compose up -d`.
덤프 파일에는 계정·기록이 들어 있으므로 저장소에 두지 않고, 다 쓰면 지운다.
