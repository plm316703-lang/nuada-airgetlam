# Nuada Airgetlám — ERD (v2)

> 갱신: 2026-11-21 — 실제 엔티티 10개(`backend-spring/src/main/java/.../@Entity`) 기준으로 다시 씀.
> v1(2026-10-28)은 구현 전 설계였고, 그 뒤 관문·기록·계정 보안이 들어오면서 테이블이 바뀌었다 (맨 아래 "v1 → v2" 참고).
> 테이블은 JPA가 서버 시작 때 만든다 (`ddl-auto: update`). 칸 이름은 Java의 `camelCase`가 DB에서 `snake_case`로 바뀐다.

## 1. 한눈에 보기

| 묶음 | 테이블 | 하는 일 |
|---|---|---|
| 사람 | `users`, `patients`, `patient_guardians`, `researcher_assignments` | 계정, 환자 정보(동의·익명 코드), 환자–보호자 연결, 연구자 배정 |
| 측정 | `sessions` | 한 번의 측정 단위. 관문은 열린 세션이 있어야 팔을 움직인다 |
| 기록 | `gate_decisions`, `device_errors`, `control_events` | 관문 판단 하나하나, 장비 오류 + RAG 안내, 정지·재개 같은 관제 동작 |
| 계정 보안 | `login_history`, `invitations` | 로그인 성공·실패와 계정 변경 기록, 환자·보호자 초대 링크 |

## 2. 전체 관계도

실선(`--`)은 실제 외래 키(FK), 점선(`..`)은 **FK 없이 id 숫자만 저장**하는 관계다.

```mermaid
erDiagram
    USERS ||--o| PATIENTS : "환자 계정이면 환자 정보 1개"
    PATIENTS ||--o{ PATIENT_GUARDIANS : "환자 1 : 연결 N"
    USERS ||--o{ PATIENT_GUARDIANS : "보호자 1 : 연결 N"
    PATIENTS ||--o{ RESEARCHER_ASSIGNMENTS : "환자 1 : 배정 N"
    USERS ||--o{ RESEARCHER_ASSIGNMENTS : "연구자 1 : 배정 N"
    PATIENTS ||--o{ SESSIONS : "환자 1 : 세션 N"
    USERS ||--o{ SESSIONS : "세션을 시작한 사람"
    SESSIONS |o..o{ GATE_DECISIONS : "session_id (FK 없음)"
    SESSIONS |o..o{ DEVICE_ERRORS : "session_id (FK 없음)"
    SESSIONS |o..o{ CONTROL_EVENTS : "session_id (FK 없음)"
    USERS |o--o{ DEVICE_ERRORS : "해결 처리한 사람"
    USERS |o--o{ CONTROL_EVENTS : "동작한 사람 (NULL = 시스템)"
    USERS ||--o{ INVITATIONS : "초대받은 계정"

    USERS {
        bigint id PK
        varchar email UK "로그인 아이디"
        varchar password "BCrypt로 암호화"
        varchar name
        varchar role "PATIENT / GUARDIAN / RESEARCHER / PRINCIPAL_INVESTIGATOR / OPERATOR / SYSTEM_ADMIN"
        datetime created_at
        boolean must_change_password "R2: 임시 비밀번호면 첫 로그인 때 변경 강제"
        datetime temp_password_expires_at "R2: 임시 비밀번호 만료 시각"
        int failed_login_count "R3: 연속 실패 횟수"
        datetime locked_until "R3: 잠금 해제 시각 (5회 실패 → 15분)"
    }
    PATIENTS {
        bigint id PK
        bigint user_id FK,UK "계정과 1:1"
        varchar amputation_side "LEFT_ARM / RIGHT_ARM / ..."
        boolean research_consent "지금 연구 동의 중인가"
        datetime consented_at
        datetime consent_withdrawn_at "철회해도 시각은 남김"
        datetime enrolled_at "연구책임자가 연구에 등록한 시각"
        varchar research_code UK "익명 코드 (예: P-0001)"
    }
    PATIENT_GUARDIANS {
        bigint id PK
        bigint patient_id FK
        bigint guardian_id FK "users.id"
        varchar relation "FAMILY / CAREGIVER / ..."
        datetime created_at
    }
    RESEARCHER_ASSIGNMENTS {
        bigint id PK
        bigint patient_id FK
        bigint researcher_id FK "users.id"
        datetime assigned_at
    }
    SESSIONS {
        bigint id PK
        bigint patient_id FK
        varchar device_code "장비 번호 (관제 담당자는 장비 번호 + 익명 코드만 봄)"
        varchar source_type "SYNTHETIC / PLAYBACK"
        int dataset_subject "재생하는 공개 데이터의 피험자 번호"
        datetime started_at
        datetime ended_at "진행 중이면 NULL"
        bigint started_by FK "users.id"
    }
    GATE_DECISIONS {
        bigint id PK
        bigint session_id "세션 없이 온 메시지도 기록 → NULL 허용, 인덱스"
        varchar ws_connection_id "어느 WebSocket 연결에서 왔나"
        int subject "메시지의 피험자 번호"
        bigint seq "메시지 순번"
        datetime received_at
        varchar command_timestamp "판별기가 보낸 시각 (원본 글자)"
        varchar intent
        double confidence
        double signal_quality
        varchar signal_issue
        varchar bad_channels "문제 채널 (예: C3,Cz,C4), 정상이면 NULL"
        varchar python_decision "판별기의 판단"
        varchar result "MOVE / ABSTAIN / HELD / BLOCKED"
        varchar reason "BLOCKED 사유 (RejectReason)"
        varchar detail "최대 300자로 잘라 저장"
        double arm_angle_after "판단 뒤 팔꿈치 각도"
        varchar true_label "시연용 정답 (판단에는 안 씀)"
        varchar scenario "오류 주입 시나리오 이름"
    }
    DEVICE_ERRORS {
        bigint id PK
        bigint session_id "FK 없음"
        varchar error_type "오류 8종 (매뉴얼 8개와 1:1)"
        varchar detail
        double signal_quality
        boolean injected "시연용으로 주입한 오류인가"
        datetime occurred_at
        datetime last_seen_at "같은 오류가 이어지면 갱신"
        int occurrence_count
        datetime resolved_at "수동 재개로 해결 처리"
        bigint resolved_by FK "users.id"
        varchar rag_status "PENDING / DONE / FAILED"
        text rag_answer "RAG 안내문"
        text rag_sources "근거 목록 (JSON 글자)"
        boolean rag_evidence_sufficient
    }
    CONTROL_EVENTS {
        bigint id PK
        bigint session_id "FK 없음"
        varchar type "SESSION_START / SESSION_END / STOP / RESUME / RESUME_DENIED / RESET_REQUIRED / RESET / INJECT"
        bigint actor_id FK "users.id, NULL = 시스템"
        varchar detail
        datetime occurred_at
    }
    LOGIN_HISTORY {
        bigint id PK
        datetime occurred_at
        varchar event "LOGIN_SUCCESS / LOGIN_FAILED / ACCOUNT_LOCKED / ... (9종), 인덱스"
        varchar fail_reason "BAD_CREDENTIALS / LOCKED / TEMP_EXPIRED"
        varchar email "대상 계정 (FK 없음)"
        varchar actor_email "한 사람 (관리자·연구책임자)"
        varchar ip
    }
    INVITATIONS {
        bigint id PK
        bigint user_id FK "초대받은 계정"
        varchar token_hash UK "링크 비밀값의 SHA-256 (원문은 저장 안 함)"
        datetime expires_at "72시간"
        datetime used_at "1회용"
        datetime revoked_at "다시 초대하면 이전 링크 무효"
        varchar created_by "초대한 직원 이메일"
        datetime created_at
    }
```

## 3. 설계 결정 기록

### 사람·측정 (v1부터 유지)
| 결정 | 이유 |
|---|---|
| 환자–보호자를 연결 테이블로 분리 (N:M) | 환자 한 명에 보호자 여러 명, 보호자 한 명이 환자 여러 명을 돌볼 수 있음. 관계 종류도 저장. 같은 조합은 한 번만(unique) |
| 연구자 배정 테이블 분리 (N:M) | 연구자가 배정받은 환자만 실명으로 봄. 같은 조합은 한 번만(unique) |
| 환자에 익명 코드(`research_code`) | 관제 담당자·미배정 연구자는 이름 대신 익명 코드만 봄 |
| 연구 데이터는 **동의한 환자만** | 관문 규칙 ①: 등록 + 동의 중이 아니면 `NOT_PARTICIPATING`으로 차단 |
| 동의 철회 시 철회 시각과 계정은 유지 | 연구 데이터는 지우되 "언제 철회했는지"는 남겨야 함 (CLAUDE.md 6장) |
| 테이블 이름 `users` (복수형) | `user`는 일부 DB에서 예약어 |

### 기록 (관문 도입 후)
| 결정 | 이유 |
|---|---|
| 기록 테이블의 `session_id`는 **FK 없이 숫자만** | 관문은 세션이 없거나 이상한 입력에도 **기록은 반드시 남긴다**. FK가 있으면 없는 세션 번호에서 저장이 실패할 수 있음 |
| 긴 값은 잘라서 저장 (`detail` 300자, `bad_channels` 200자) | 같은 원칙: 이상한 입력 때문에 저장이 실패하지 않게 |
| `bad_channels`는 표가 아니라 `"C3,Cz,C4"` 글자 | 화면에 마지막 판단만 보여 주면 되고, 채널별로 검색할 일이 없음 (B1 결정) |
| 같은 오류는 행 하나로 묶음 (`occurrence_count`, `last_seen_at`) | 잡음이 이어지면 매 메시지마다 오류 카드가 쌓이고 RAG가 반복 호출됨 |
| RAG 결과를 `device_errors`에 함께 저장 | 오류 1건 = 안내 1건. 생성이 실패해도(`FAILED`) 근거(`rag_sources`)는 남김 (A2) |
| `control_events.actor_id`가 NULL이면 시스템 | 정지는 시스템이, 재개는 사람이 한다는 구분이 기록에 보이게 (자동 재개 없음) |

### 계정 보안 (R2~R4)
| 결정 | 이유 |
|---|---|
| `login_history`에 계정 발급·변경·잠금 해제·초대도 함께 | 로그인 관련 감사 기록을 한 곳에서 봄. 보관 방침 1년 |
| `login_history.email`은 FK 없는 글자 | 없는 이메일로 로그인 실패한 것도 기록해야 함 |
| 초대 토큰은 SHA-256 해시만 저장 | DB가 새어도 링크를 다시 만들 수 없게 (비밀번호를 해시로 저장하는 것과 같은 이유) |
| 종류 칸(enum)은 varchar로 저장 (`@JdbcTypeCode(SqlTypes.VARCHAR)`) | Hibernate가 MySQL에 `enum('A','B')` 칸을 만들면 새 값을 추가할 때 저장이 실패함 (R4에서 발견, `login_history`·`control_events`에 적용) |

## 4. v1 → v2에서 바뀐 것

| v1 (10/28) | v2 (지금) | 이유 |
|---|---|---|
| `arm_movements` (관절 5개 각도, 버튼/뇌파 모드) | 없음 → `gate_decisions.arm_angle_after` | 버튼 모드를 빼고, 팔 움직임은 관문 판단 기록에 붙임. 가상 팔은 팔꿈치 1축 |
| `eeg_records` (뇌파 파일 메타데이터) | 없음 | 공개 데이터(BCI IV 2a)를 재생하므로 파일을 저장하지 않음. `sessions.dataset_subject`로 피험자만 기록 |
| `sessions.mode` (BUTTON / EEG) | `source_type`, `device_code`, `dataset_subject`, `started_by` | 측정 장비와 데이터 출처, 누가 시작했는지가 관제에 필요 |
| 역할 3개 | 역할 6개 | 연구책임자·관제 담당자·시스템 관리자 추가 |
| — | `gate_decisions`, `device_errors`, `control_events`, `login_history`, `invitations` | 관문·RAG·계정 보안 기능으로 추가 |
