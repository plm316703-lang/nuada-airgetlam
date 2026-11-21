# 로컬 실행 안내 (Docker 없이)

> Docker로 배포하기 전에 내 PC에서 직접 실행해 동작을 확인하는 방법.
> Docker 실행은 [deploy.md](deploy.md) 참고. 두 방식 모두 저장소 최상위의 `.env` 하나를 읽는다.

---

## 1. 큰 그림

```
 터미널 1: RAG 서버 (Python, :8000)       ← 장비 오류 점검 안내 (Gemini)
 터미널 2: Spring 관문 + 화면 (:8080)     ← 로컬 MySQL(:3306)에 저장
 터미널 3: 판별기 재생기 (Python)          → ws://localhost:8080/ws/classifier
 브라우저:  http://localhost:8080          → 로그인 → 역할별 화면
```

실행 순서: **MySQL → RAG → Spring → 브라우저에서 세션 시작 → 재생기**
(재생기를 먼저 켜도 된다. 세션이 없으면 관문이 `NO_ACTIVE_SESSION`으로 거부하고 기록만 남긴다.)

## 2. 처음 한 번 준비

### 2-1. 프로그램 설치 (관리자 권한 PowerShell)

| 프로그램 | 설치 명령 | 확인 |
|---|---|---|
| JDK 21 | `winget install EclipseAdoptium.Temurin.21.JDK` | 새 터미널에서 `java -version` |
| MySQL 8 | [MySQL Installer](https://dev.mysql.com/downloads/installer/)로 **MySQL Server 8.4** 설치 (root 비밀번호를 정함, Windows 서비스로 실행) | `Get-Service MySQL*` |
| Python 3.11 | `winget install Python.Python.3.11` (또는 학원 PC의 conda 환경 `nuada-bci`) | `py -3.11 --version` |

> MySQL을 설치할 때 포트는 기본값 3306. 이미 다른 값이면 `.env`에 `DB_PORT=그 포트`를 추가한다.

### 2-2. `.env` 만들기

```powershell
Copy-Item .env.example .env
notepad .env
```

| 키 | 넣을 값 |
|---|---|
| `MYSQL_DATABASE` / `MYSQL_USER` / `MYSQL_PASSWORD` | 프로젝트용 DB·계정 (다음 단계에서 이 값으로 만들어짐) |
| `GATEWAY_API_KEY` | 재생기와 관문이 공유하는 비밀키 (아무 긴 문자열) |
| `JWT_SECRET` | 로그인 토큰 서명 키, **32자 이상** |
| `DEMO_PASSWORD` | 시연 계정 8개(`@nuada.test`)의 공통 비밀번호 |
| `GEMINI_API_KEY` | [Google AI Studio](https://aistudio.google.com)에서 발급 |

### 2-3. DB·계정 만들기 + Python 패키지 설치

```powershell
powershell -ExecutionPolicy Bypass -File scripts\setup-local-db.ps1   # root 비밀번호를 물어봄
powershell -ExecutionPolicy Bypass -File scripts\setup-python.ps1     # ai-server-python\.venv 생성
```

> `-ExecutionPolicy Bypass`는 "이 스크립트 한 번만" 실행을 허락한다는 뜻이다 (PC 설정은 바꾸지 않음).
> conda 환경을 쓰면 `setup-python.ps1` 대신 `conda activate nuada-bci` 후
> `pip install -r ai-server-python\requirements.txt` → `pip install -r ai-server-python\requirements-rag.txt`
> (한 번에 넣으면 pip 의존성 계산이 실패하므로 두 번에 나눠서).

## 3. 실행 (터미널 3개)

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-rag.ps1        # 터미널 1
powershell -ExecutionPolicy Bypass -File scripts\run-backend.ps1    # 터미널 2 (IntelliJ에서 BackendApplication 실행도 같음)
powershell -ExecutionPolicy Bypass -File scripts\run-replayer.ps1   # 터미널 3
```

- Spring은 처음 시작할 때 테이블 8개를 자동으로 만들고(`ddl-auto: update`), `DEMO_PASSWORD`가 있으면 시연 계정을 만든다.
- 재생기는 처음 실행 때 뇌파 데이터(약 80MB)를 `C:\Users\<나>\mne_data`에 내려받는다.

## 4. 시연 흐름 (목표 시나리오)

1. 브라우저 `http://localhost:8080` → **관제 담당자**(`operator@nuada.test`)로 로그인
2. 세션 패널에서 참여 중인 환자를 골라 **세션 시작** → 팔은 **정지** 상태로 시작
3. 재생기 데이터가 들어와 "연속 정상 3개"가 되면 **수동 재개** → 팔이 왼손/오른손 상상에 따라 굽히고 펴짐
4. 재생기 터미널에서 `e` + Enter → 전극 불량 주입 → 관문이 `SIGNAL_QUALITY`로 차단, **그 자리에서 정지**, 오류 기록
5. 오류 카드에 **RAG 점검 안내**(근거 매뉴얼·근거 충분 여부)가 표시됨
6. 주입이 끝나 정상 데이터가 3개 쌓이면 **수동 재개** (서버가 조건을 다시 검사)

| 재생기 키 | 주입되는 오류 | 관문 결과 |
|---|---|---|
| `e` | 전극 접촉 불량 (C3·C4 평탄, Cz 출렁임) | SIGNAL_QUALITY → ELECTRODE_CONTACT |
| `n` | 전원 잡음 60Hz | SIGNAL_QUALITY → LINE_NOISE |
| `m` | 근육 잡음 (40Hz 이상) | SIGNAL_QUALITY → MUSCLE_ARTIFACT |
| `x` | 판별기 판단 뒤집기 | DECISION_MISMATCH |
| `s` | 5초 전 시각으로 전송 | STALE_COMMAND |
| `d` | 5초 동안 전송 안 함 | 워치독 3초 정지 → PACKET_LOSS |
| `r` | 12초 동안 전송 안 함 | 10초 → 초기화 필요 (초기화 후 재개) |

주입된 메시지는 화면·기록에 **"주입"**으로 표시된다 (성능 지표와 구분).

### 역할별로 확인할 것

| 계정 | 첫 화면 | 확인할 것 |
|---|---|---|
| `operator@nuada.test` | 관제 | 환자는 익명 코드만, 세션·재개·초기화 가능 |
| `pi@nuada.test` | 관제 | 등록 환자 실명, **연구 등록**·**연구자 배정** 버튼 |
| `researcher@nuada.test` | 관제 (보기 전용) | 동의 환자만, 배정된 환자만 실명, 조작 버튼 없음 |
| `patient@nuada.test` | 환자 | 내 로봇팔, **동의 철회** → 철회 안내 후 팔 즉시 정지 |
| `guardian@nuada.test` | 보호자 | 연결된 환자 상태 보기 |
| `admin@nuada.test` | 계정 관리 | 이메일·역할만 (실명·의료 정보 없음) |

## 5. 테스트

```powershell
cd backend-spring
.\gradlew.bat test      # 로컬 MySQL + .env 필요 (기존 테스트가 실제 DB를 씀)
```

새로 추가한 안전 규칙 테스트(`ArmSupervisorTest`, `GateCheckersTest`)는 DB 없이 돈다.

## 6. 문제 해결

| 증상 | 원인 | 해결 |
|---|---|---|
| Spring 시작 실패 `JWT_SECRET 은 32바이트…` | `.env`의 JWT_SECRET이 짧거나 없음 | 32자 이상으로 |
| `Access denied for user 'nuada'@'localhost'` | DB 계정 비밀번호가 `.env`와 다름 | `setup-local-db.ps1` 다시 실행 |
| `Communications link failure` | MySQL 서비스가 꺼져 있음 | `Start-Service MySQL84` (서비스 이름은 `Get-Service MySQL*`) |
| 재생기 `연결 거절: HTTP 401` | API 키 불일치 | 재생기와 Spring 이 같은 `.env`를 읽는지 확인 |
| 관문이 모든 메시지를 `UNSUPPORTED_VERSION` 거부 | 예전 규격 v1으로 보내는 스크립트를 씀 (옛 `step12_replay_ws.py`는 11/21 삭제) | `realtime/replayer.py`(v2) 사용 |
| 오류 카드에 "RAG 안내 실패 503" | `GEMINI_API_KEY` 없음 | `.env`에 넣고 RAG 서버 재시작 |
| 오류 카드에 "RAG 서버에 연결할 수 없습니다" | RAG 서버가 꺼져 있음 | 터미널 1 확인. 팔 안전에는 영향 없음 |
| 8080 포트 사용 중 | Docker 의 backend 가 떠 있음 | `docker compose down` 후 로컬 실행 |
