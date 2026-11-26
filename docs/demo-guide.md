# 시연 안내 — 다른 컴퓨터에서 처음부터 시연하기

> 2026-11-26 작성. 배포(인터넷 공개) 없이, 시연할 컴퓨터에서 **GitHub에서 코드 받기 → `.env` 준비 → Docker로 실행**하는 방법.
> 실행 명령의 자세한 설명은 [`deploy.md`](deploy.md), 문제 해결 표는 `deploy.md` 6장.

```
[GitHub 저장소] ──git clone──▶ [시연할 컴퓨터] ◀── .env (따로 준비, 절대 커밋하지 않음)
                                   └─ docker compose up -d --build → http://localhost:8081
```

---

## 1. 준비물

| 항목 | 내용 |
|---|---|
| Docker Desktop | WSL2 사용. 처음 설치하는 PC는 BIOS에서 가상화가 켜져 있어야 함. 설치 후 재부팅 |
| Git | `git clone`용 |
| 인터넷 | 첫 실행 때 Docker 이미지·라이브러리·공개 뇌파 데이터셋(BCI IV 2a)을 받음. RAG는 실행 중에도 Gemini를 부름 |
| PC 사양 | 메모리 8GB 이상 권장 (컨테이너 4개가 약 1.3GB + Docker 자체). CPU는 4코어 이상이 안전 (5장 주의) |
| 포트 | **8081**(화면), 3307(DB, 이 PC 안에서만)이 비어 있어야 함 |

## 2. `.env` 준비

`.env`는 GitHub에 없다. 시연 PC에서 견본을 복사해 채운다.

```powershell
Copy-Item .env.example .env
```

| 값 | 어떻게 |
|---|---|
| `GEMINI_API_KEY` | **유일하게 옮기거나 새로 발급해야 하는 값** (https://aistudio.google.com). 비밀번호 관리자나 USB로 옮기고 채팅·메일로 보내지 않는다 |
| `GEMINI_MODEL` | `gemini-3.1-flash-lite` 권장 (11/19 기본 모델이 매우 느렸음) |
| `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD`, `GATEWAY_API_KEY`, `JWT_SECRET` | 시연 PC에서 **새로 만들어도 됨**. `.env.example` 맨 아래의 PowerShell 한 줄로 무작위 값 생성 (`JWT_SECRET`은 32자 이상) |
| `DEMO_PASSWORD` | 시연 계정(`@nuada.test`) 비밀번호. 새로 정해도 됨 (비워 두면 시연 계정이 만들어지지 않음) |
| `MYSQL_DATABASE`, `MYSQL_USER` | 견본 값 그대로 |

## 3. 실행

```powershell
git clone https://github.com/plm316703-lang/nuada-airgetlam.git
cd nuada-airgetlam
docker compose up -d --build
docker compose ps
```

- 첫 실행은 빌드·다운로드 때문에 몇 분 이상 걸린다. `backend`가 `healthy`가 되면 준비 완료
- 브라우저: **http://localhost:8081**
- DB는 새로 만들어지고 **시연 계정과 환자가 자동으로 생긴다** (`DemoDataInitializer`). 이 컴퓨터의 시연 기록은 옮겨지지 않는다

| 계정 | 역할 | 시연에서 |
|---|---|---|
| `operator@nuada.test` | 관제 담당자 | 세션 시작, 오류 주입, 수동 재개 |
| `pi@nuada.test` | 연구책임자 | 위와 같음 + 환자 등록·보호자 초대 |
| `patient@nuada.test` | 환자 (테스트환자, 동의함) | 환자 화면 (밝은 테마) |
| `guardian@nuada.test` | 보호자 (테스트환자의 가족) | 보호자 화면 |
| `admin@nuada.test` | 관리자 | 계정 발급·로그인 기록 |

비밀번호는 모두 `.env`의 `DEMO_PASSWORD`.

## 4. 시연 순서 (약 5분)

| # | 화면 | 할 일 | 말할 것 |
|---|---|---|---|
| 1 | 관제 (`operator`) | **세션 시작** (환자 목록에는 동의한 테스트환자 하나만 나옴) | "뇌파 판별 결과를 관문이 다시 검사해 가상 로봇팔을 움직인다" |
| 2 | 관제 | 팔이 정지 상태로 시작 → 연속 정상 3개가 쌓이면 **수동 재개** | "자동 재개는 없다. 사람이 조건을 확인하고 재개한다" |
| 3 | 관제 | 그래프·판단 기록에서 MOVE / ABSTAIN 확인 | "확신도 0.75 미만은 관문이 보류한다. 판별기를 믿지 않는다" |
| 4 | 관제 | 오류 주입 **전극 불량** | "시연용 가짜 데이터만 바꾼다. 팔·관문은 건드리지 않는다" |
| 5 | 관제 | 그 자리 정지 + 채널 지도 C3·Cz·C4 빨강 | "그래프로 언제, 지도로 어디가 나빠졌는지" |
| 6 | 관제 | RAG 점검 안내 카드 (근거 매뉴얼·출처) | "RAG는 안내만 한다. 매뉴얼 수치·출처를 그대로 옮긴다" |
| 7 | 관제 | 연속 정상 데이터 확인 후 수동 재개 → 오류 카드 "재개로 해결" | |
| 8 | 환자·보호자 (다른 브라우저 창이나 시크릿 창) | 밝은 화면, 쉬운 말 정지 사유, 오늘 멈춘 기록 | "기술 문구는 서버에서 쉬운 말로 바꿔 보낸다" |
| 9 | (선택) README | 1차·2차 실험 이야기, LangChain 비교표 | "맡기기 vs 직접 하기, 그리고 무엇을 배웠나" |

## 5. 주의 (11/26에 실제로 겪은 것)

| 상황 | 왜 | 대응 |
|---|---|---|
| 판단 기록이 전부 **BLOCKED 오래된 명령**, 연결 끊김까지 기록됨 | 같은 PC에서 화면 녹화·무거운 프로그램을 같이 돌리면 재생기 메시지가 2초 넘게 밀린다 (관문의 신선도 검사가 의도대로 막은 것) | 시연 중에는 녹화·무거운 프로그램을 끈다. 이미 밀렸다면 잠시 기다리면 회복됨 → 연속 정상 3개 후 수동 재개 |
| RAG 카드가 "안내문 생성 실패" | Gemini 과부하(503)·시간 초과(25초) | 근거 목록은 그대로 나온다. "생성이 실패해도 근거는 보여 주게 설계했다"고 설명. 다음 오류에서 다시 시도됨 |
| RAG 카드가 "RAG 안내 실패 503"이고 근거도 없음 | `GEMINI_API_KEY` 없음, 또는 인터넷 없음 | `.env` 확인 후 `docker compose up -d rag`. 인터넷이 없으면 관문·팔·지도까지만 시연 |
| 로그인 실패 | `DEMO_PASSWORD` 오타, 또는 5회 실패로 15분 잠김 | `admin`으로 잠금 해제·재발급 |
| `port is already allocated` | 8081(또는 3307)을 다른 프로그램이 사용 | 그 프로그램을 끄거나 `docker-compose.yml`의 포트 변경 |
| `.env`를 바꿨는데 DB 로그인 실패 | MySQL은 볼륨을 처음 만들 때만 계정 생성 | `docker compose down -v` 후 다시 (시연 기록 삭제됨) |

## 6. 체크리스트

**전날 (시연할 그 PC에서)**
- [ ] `docker compose up -d --build` 끝까지 성공 (이미지·데이터셋을 미리 받아 둠)
- [ ] 4장 순서를 처음부터 끝까지 한 번 (RAG 안내가 실제 문장으로 나오는지)
- [ ] 화면 확대·브라우저 창 크기 맞추기 (관제 화면은 가로 1280px 이상이 보기 좋음)

**당일**
- [ ] Docker Desktop 켜기 → `docker compose up -d` → `docker compose ps`에서 `backend` healthy
- [ ] 녹화·화면 공유 외의 무거운 프로그램 끄기
- [ ] 예비: 인터넷·Docker가 안 되면 README 맨 위 데모 GIF(`docs/images/demo.gif`)로 설명

**끝난 뒤 (공용 PC라면 꼭)**
- [ ] `docker compose down -v` (시연 DB·색인 삭제)
- [ ] `.env` 삭제 (Gemini 키가 남지 않게)

## 7. (선택) README 사진 다시 찍기

시연 환경이 켜져 있고 세션이 진행 중일 때:

```powershell
python scripts\capture_demo.py
```

`pillow`·`websocket-client`가 있는 Python(Anaconda 기본 환경)과 Chrome 또는 Edge가 필요하다.
새 PC에서는 테스트환자로 세션을 시작하면 환자·보호자 사진도 실시간 상태로 찍힌다.
