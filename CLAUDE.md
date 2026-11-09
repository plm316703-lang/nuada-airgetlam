# CLAUDE.md — Nuada Airgetlám

이 파일은 Claude Code가 작업을 시작할 때 먼저 읽는 프로젝트 안내서입니다.

---

## 1. 나에 대해 (학습자 정보)

- 프로그래밍 경력 1년 미만의 초보 개발자, 국비 과정 수강 중 (Java·Spring·Python)
- 목표: 2027년 3월까지 개발자 취업, 장기적으로 BCI(뇌-컴퓨터 인터페이스) 분야
- 이 프로젝트는 **학습 + 취업 포트폴리오** 목적

## 2. Claude에게 부탁하는 진행 방식 (가장 중요)

1. **정답 코드를 먼저 주지 않는다.** "무엇을 만들어야 하는지(요구사항)"만 말한다.
2. **한 번에 한 걸음(한 줄, 작은 단계)씩** 진행한다. 요구사항·힌트를 한꺼번에 주지 않는다.
3. 내가 막히면 **힌트 1 → 힌트 2 → 정답** 순서로만 준다.
4. **20년 차 개발자가 1년 미만 초보에게 차근차근** 알려주듯 설명한다.
5. **영어 식별자(클래스·메서드·변수 이름)는 한국어로 풀어서** 설명한다.
   예) `ResearcherAssignmentRepository` → "연구자 배정 저장소"
6. 실행 전에 **결과를 먼저 예측**하게 질문한다.
7. 설계 판단이 필요한 부분은 Claude가 정하지 말고 **나에게 먼저 묻는다.** (내 결정 / AI 제안 구분)
8. 내가 직접 "정답 줘", "대신 써 줘"라고 할 때만 완성 코드를 준다.
9. 설명은 **위에서 아래로(큰 그림 → 세부)**, 코드에는 **한국어 주석**을 단다.

## 3. 프로젝트 소개

- **이름:** Nuada Airgetlám (아일랜드 신화의 은팔 왕 누아다에서 따옴)
- **한 줄 설명:** 팔을 잃은 환자가 뇌파로 가상 로봇팔을 조종하고, 보호자가 상태를 함께 보는 시스템
- **사용자:** 팔이 절단된 환자, 보호자, 연구자
- **화면:** 환자 화면(가상 로봇팔, 2D 먼저 → 3D로 확장 가능하게), 보호자 화면(상태·기록)
- **로드맵:** 1단계 Spring 기본기 → 2단계 뇌파(공개 데이터셋 BCI Competition IV 2a) → 3단계 AI(LLM·RAG)

## 4. 기술 스택과 구조

- Java 21, Spring Boot 4, Gradle, Spring Data JPA, MySQL 8, Lombok, JUnit
- IDE: IntelliJ Community (Gradle 프로젝트는 `backend-spring` 폴더 기준)

```
nuada-airgetlam/
├── backend-spring/      # Spring Boot 서버
│   └── src/main/java/io/github/plm316703/backend/
│       ├── user/        # User, Role, UserRepository
│       └── patient/     # Patient, AmputationSide, PatientRepository
│                        # PatientGuardian, GuardianRelation, PatientGuardianRepository
│                        # ResearcherAssignment, ResearcherAssignmentRepository
│                        # ResearcherAssignmentService
├── ai-server-python/    # (예정) 뇌파 분석·AI 서버
└── docs/                # ERD.md, learning-log.md
```

### 계층 구조 (식당 비유로 이해하고 있음)
- Controller = 웨이터 (⏳ 아직 없음)
- Service = 주방장, "해도 되는지" 규칙 판단 (✅ ResearcherAssignmentService)
- Repository = 창고 관리인, 저장·조회만 (✅)
- Entity = 재료 규격서, 클래스 ↔ 테이블 (✅)

## 5. DB 설계 요약

```
users (환자·보호자·연구자 모두, role로 구분)
  └─1:1─ patients (절단 부위, 연구 동의 여부·시간, 철회 시간, 익명 연구 코드)
            ├─1:N─ patient_guardians (환자–보호자 연결, 조합 unique) ─N:1─ users
            └─1:N─ researcher_assignments (환자–연구자 배정, 조합 unique) ─N:1─ users
```

## 6. 내가 내린 설계 결정 (바꾸기 전에 반드시 나에게 물어볼 것)

- 동의하지 않은 환자의 데이터는 **익명이라도** 연구에 쓰지 않는다. "익명"은 동의한 환자를 익명 코드로 보여주는 방식이다.
- 연구 동의를 **철회하면 연구자 배정 등 연구 관련 데이터를 철저히 삭제**한다. 철회 전에 이 방침을 반드시 안내한다. (철회 시간 기록과 환자 계정은 유지)
- 중복 배정은 **Service의 exists 검사 + DB unique 제약**을 함께 쓴다 (친절한 메시지 + 데이터 무결성).
- 철회 시 배정 삭제 로직은 **Service**에 둔다 (철회와 삭제를 @Transactional로 한 묶음).
- 예외: 잘못된 요청은 `IllegalArgumentException`, 지금 상태로 불가능하면 `IllegalStateException`.

## 7. 현재 진행 상황

- ✅ User, Patient, PatientGuardian, ResearcherAssignment 엔티티와 Repository, 테스트
- ✅ 쿼리 메서드 (countByPatient, findAllByPatient, findAllByGuardian, existsByPatientAndResearcher)
- ✅ ResearcherAssignmentService.assign (배정 규칙 4가지) + 테스트
- 👉 **지금:** 브랜치 `feature/consent-withdrawal`에서 "동의 철회 기능" 시작
  - 첫 단계: `ResearcherAssignmentRepository`에 "이 환자의 배정을 전부 삭제하는" 쿼리 메서드 **이름 짓기** (내가 직접)
  - 그다음: `ResearchConsentService.withdraw(환자 id)` — 환자 없으면 거절 → 동의 철회 → 배정 전부 삭제
  - 아직 내가 정하지 않은 것: 동의한 적 없는 환자가 철회를 요청하면 예외로 거절할지, 그냥 통과시킬지

## 8. 작업 규칙

- **비밀 정보:** DB 비밀번호는 `application-local.yaml`에만 둔다 (.gitignore 대상). 절대 커밋하지 않는다.
- **테스트 데이터:** 실제 이메일·이름 금지, `patient@test.com`, `테스트환자` 같은 가짜 값만 사용.
- **Git 흐름:** main 최신화 → 새 브랜치(`feature/...`) → 작업·테스트 → 커밋·푸시 → PR → 머지 → 로컬 정리
- **커밋 메시지:** `feat:` / `fix:` / `test:` / `docs:` / `refactor:` / `chore:` + 한국어 설명
- 커밋 전에는 항상 `git status`로 무엇이 올라가는지 확인한다.
