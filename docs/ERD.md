# Nuada Airgetlám — ERD (v1)

## 전체 관계도

```mermaid
erDiagram
    USERS ||--o| PATIENTS : "환자 회원이면 환자 정보 1개"
    PATIENTS ||--o{ PATIENT_GUARDIANS : "환자 1 : 연결 N"
    USERS ||--o{ PATIENT_GUARDIANS : "보호자 1 : 연결 N"
    PATIENTS ||--o{ RESEARCHER_ASSIGNMENTS : "환자 1 : 배정 N"
    USERS ||--o{ RESEARCHER_ASSIGNMENTS : "연구자 1 : 배정 N"
    PATIENTS ||--o{ SESSIONS : "환자 1 : 세션 N"
    SESSIONS ||--o{ ARM_MOVEMENTS : "세션 1 : 움직임 N"
    SESSIONS ||--o{ EEG_RECORDS : "세션 1 : 뇌파 파일 N"

    USERS {
        bigint id PK
        varchar email UK "로그인 아이디"
        varchar password "암호화해서 저장"
        varchar name
        varchar role "PATIENT / GUARDIAN / RESEARCHER"
        datetime created_at
    }
    PATIENTS {
        bigint id PK
        bigint user_id FK,UK "회원과 1:1"
        varchar amputation_side "LEFT / RIGHT / BOTH"
        boolean research_consent "연구 동의 여부"
        datetime consented_at 연구 동의 시간 
        datetime consentWithdrawnAt 연구 동의 철회 시간
        varchar research_code UK "익명 코드 (예: P-0001)"
    }
    PATIENT_GUARDIANS {
        bigint id PK
        bigint patient_id FK
        bigint guardian_id FK "users.id"
        varchar relation "FAMILY / CAREGIVER / OTHER"
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
        varchar mode "BUTTON / EEG"
        datetime started_at
        datetime ended_at "진행 중이면 NULL"
    }
    ARM_MOVEMENTS {
        bigint id PK
        bigint session_id FK
        datetime recorded_at
        varchar command "LEFT / RIGHT / UP / DOWN / GRIP / RELEASE"
        varchar decoded_intent "뇌파 모드에서만: LEFT_HAND / RIGHT_HAND / FEET / TONGUE"
        double confidence "뇌파 모드에서만: 0.0 ~ 1.0"
        double shoulder_pitch "어깨 위아래 각도"
        double shoulder_yaw "어깨 좌우 각도 (2D에서는 0)"
        double elbow "팔꿈치 각도"
        double wrist "손목 각도"
        boolean gripper_open "집게 열림 여부"
        boolean success "목표 동작 성공 여부"
    }
    EEG_RECORDS {
        bigint id PK
        bigint session_id FK
        varchar file_path "원본 뇌파 파일 위치"
        int sampling_rate "초당 측정 횟수 (Hz)"
        varchar channel_names "예: C3,Cz,C4,..."
        double duration_sec
        varchar source "예: BCI Competition IV 2a"
    }
```

## 설계 결정 기록

| 결정 | 이유 |
|---|---|
| 환자–보호자를 연결 테이블로 분리 (N:M) | 환자 한 명에 보호자 여러 명, 보호자 한 명이 환자 여러 명을 돌볼 수 있음. 관계 종류(가족/간병인)도 저장 |
| 연구자 배정 테이블 분리 (N:M) | 연구자가 특정 환자를 배정받아 데이터를 볼 수 있도록 함 |
| 환자에 익명 코드(research_code) 부여 | 연구자는 이름 대신 익명 코드로만 환자 데이터를 봄 |
| 연구 데이터 접근은 **연구 동의한 환자만** | 익명 열람과 배정 열람 모두 동의가 전제 조건 |
| 세션(Session) 도입 | 한 번의 사용 단위로 뇌파와 움직임을 묶어, 예측 모델 학습 데이터로 활용 |
| 버튼 모드도 세션으로 기록 | 버튼 조작과 뇌파 조작의 성공률 비교 데이터 확보 |
| 관절 5개 확장형 (어깨 2축, 팔꿈치, 손목, 집게) | 3D 확장 대비. 2D에서는 shoulder_yaw를 0으로 고정 |
| 뇌파 원본은 파일, DB에는 메타데이터만 | 22채널 × 250Hz = 초당 5,500개 값으로 DB에 행 단위 저장은 비효율적 |
| 테이블 이름 `users` (복수형) | `user`는 일부 DB에서 예약어라 충돌 위험 |
