# Nuada Airgetlám

> 생각으로 움직이는 로봇팔 — 뇌파로 가상 로봇팔을 조종하는 환자 중심 BCI 시스템

> **이 저장소는 AI 코딩 에이전트(Claude Code) 활용 실험입니다.**
> 2026-11-20부터 구현의 대부분을 Claude Code가 맡았고, 저는 설계를 결정하고 모든 단계를 직접 실행해 확인했습니다.
> 무엇을 AI가 했고, 무엇을 제가 결정했고, AI가 틀린 것을 어떻게 잡았는지는 아래 문서에 그대로 남겼습니다.
> - [`docs/retrospective.md`](docs/retrospective.md) — 실험 회고 (결과·소감·블랙박스 문제)
> - [`docs/portfolio-log.md`](docs/portfolio-log.md) — 작업마다 문제 / AI 도움 / 내 판단 / 검증 / 실험 노트
> - [`CLAUDE.md`](CLAUDE.md) — AI에게 준 진행 규칙과 내 설계 결정
>
> 의료기기가 아닌 학습용 프로토타입이며, 실제 환자 데이터 대신 공개 뇌파 데이터(BCI Competition IV 2a)를 재생합니다.

## 프로젝트 이름의 의미

아일랜드 신화의 왕 **누아다(Nuada)**는 전투에서 팔을 잃고 왕위에서 물러나야 했습니다.
그러나 은으로 만든 팔, **아르게틀람(Airgetlám, "은의 팔")**을 얻어 다시 왕좌로 돌아옵니다.

이 프로젝트는 그 이야기를 기술로 다시 쓰려는 시도입니다.
몸을 움직일 수 없는 사람이 **생각만으로 팔을 되찾는 것**, 그리고 언젠가는
**인간에게 없던 팔과 능력을 더하는 것**이 이 프로젝트의 비전입니다.

## 무엇을 만드나

| 화면 | 기능 |
|---|---|
| 환자 화면 | 뇌파로 읽은 의도에 따라 움직이는 가상 로봇팔, AI가 제안한 행동 후보 확인 |
| 보호자 화면 | 로봇팔 상태, 집중·피로 지표, 행동 기록, 알림 |

## 시스템 구조

```
[환자 화면] ◄── [Python AI 서버] ──► [Spring 서버] ──► [보호자 화면]
 가상 로봇팔        뇌파 디코딩            계정, 기록 저장       상태·기록 확인
 (시각 피드백)      집중·피로 계산         실시간 전송
                   AI 행동 제안
```

- 뇌파 측정 장비 대신 **공개 뇌파 데이터셋(BCI Competition IV 2a)**을 실시간처럼 재생해 사용합니다.
- 집중·피로 지표는 연구용 근사치이며, 의학적 진단이 아닙니다.

## 폴더 구조

```
nuada-airgetlam/
├── backend-spring/     # Spring Boot 서버 (회원, 환자-보호자 연결, 행동 기록)
├── ai-server-python/   # Python 서버 (뇌파 디코딩, 상태 지표, AI 행동 제안)
├── docs/               # 설계 문서 (ERD, API 명세 등)
└── README.md
```

## 기술 스택

- **Backend:** Java, Spring Boot, Spring Data JPA, Spring Security, MySQL
- **AI / BCI:** Python, FastAPI, NumPy, MNE-Python, scikit-learn, LLM API
- **Frontend:** HTML, CSS, JavaScript (Canvas 2D → 이후 Three.js 3D)
- **Infra:** Docker, Docker Compose

## 로드맵

- [ ] **1단계 — 기본기:** 회원/역할, 환자-보호자 연결, 행동 기록, 2D 로봇팔(버튼 조작), 배포
- [ ] **2단계 — BCI 도메인:** 공개 뇌파 데이터 분석, 운동 상상 분류, 뇌파로 로봇팔 조종, 집중·피로 지표
- [ ] **3단계 — AI:** LLM 행동 후보 제안, 행동 기록 기반 RAG, 환자 확인 후 실행
- [ ] **이후:** 3D 로봇팔, AI 자동 실행 모드, 실제 뇌파 장비 연동

## 실행하기

| 방법 | 문서 | 한 줄 요약 |
|---|---|---|
| 로컬 (Docker 없이) | [docs/local-run.md](docs/local-run.md) | JDK 21 + MySQL 8 + Python, `scripts\run-*.ps1` 세 개 |
| Docker Compose | [docs/deploy.md](docs/deploy.md) | `docker compose up -d --build` → http://localhost:8080 |

두 방법 모두 저장소 최상위 `.env`(견본: `.env.example`) 하나를 읽는다.
관문 메시지 규격과 검사 순서는 [docs/message-spec.md](docs/message-spec.md).

## 개발 기록

진행 과정과 배운 점은 `docs/` 폴더에 기록합니다.
