# 개발 학습 기록 — Nuada Airgetlám

> 프로젝트를 만들면서 배운 개념, 설계 결정, 실수와 교훈을 기록합니다.

---

## 1. 진행 상황

| 주차 | 계획 | 상태 |
|---|---|---|
| W0 | GitHub 저장소, Spring Boot 초기화, README | ✅ 완료 |
| W1~2 | ERD 설계, 회원·역할, JPA 기초 | 🟡 진행 중 (ResearcherAssignment 남음) |
| W3~4 | 환자–보호자 연결, 행동 기록 CRUD, 페이징 | ⏳ 예정 |

### 현재 구현된 구조

```
backend-spring/
├── user/     User, Role, UserRepository
└── patient/  Patient, AmputationSide, PatientRepository
              PatientGuardian, GuardianRelation, PatientGuardianRepository

test/
├── PatientTest                     (단위 테스트)
├── PatientRepositoryTest           (통합 테스트)
└── PatientGuardianRepositoryTest   (통합 테스트)
```

### 현재 DB 관계

```
users ──1:1── patients ──1:N── patient_guardians ──N:1── users(보호자)
             (user_id UNI)     (patient_id, guardian_id 조합 UNI)
```

---

## 2. Git / GitHub

| 개념 | 정리 |
|---|---|
| 기본 흐름 | `git add` → `git commit` → `git push` |
| 브랜치 작업 흐름 | `main` 최신화(`git pull`) → 새 브랜치(`git switch -c`) → 작업 → PR → 머지 → 로컬 정리 |
| 머지된 브랜치 | 끝난 브랜치로 취급. 추가 작업은 새 브랜치 + 새 PR |
| 스마트 체크아웃 | 수정 내용을 임시 보관(`stash`) → 브랜치 이동 → 다시 적용 |
| 강제 체크아웃 | 수정 내용을 **버리고** 이동 (주의) |
| 비밀 정보 보호 | `application-local.yaml`을 `.gitignore`로 제외 |
| 무시 여부 확인 | `git status -u`, `git check-ignore -v <파일>` |
| 커밋 메시지 규칙 | `feat:` 기능, `fix:` 버그, `docs:` 문서, `test:` 테스트, `refactor:` 개선, `chore:` 설정 |

---

## 3. Spring Boot 설정

| 개념 | 정리 |
|---|---|
| 설정 파일 분리 | `application.yaml`(공통, 공개) + `application-local.yaml`(비밀, 비공개) |
| 프로필 활성화 | `spring.profiles.active: local` |
| YAML 규칙 | 들여쓰기가 소속 관계를 결정, Tab 사용 불가, 줄 끝 주석 가능(`#` 앞 공백) |
| JDBC URL | `jdbc:mysql://주소:포트/DB이름?옵션` |
| `serverTimezone=Asia/Seoul` | Java와 MySQL의 시간 해석 차이 방지 |
| `open-in-view: false` | DB 연결을 화면 응답 끝까지 붙잡지 않음 (실무 권장) |
| 에러 로그 읽는 순서 | **Description**(무엇) → **Origin**(어디, 줄 번호) → **Action**(해결 힌트) |

---

## 4. JPA 엔티티와 관계

| 개념 | 정리 |
|---|---|
| `@Entity`, `@Table` | 클래스와 테이블 연결. `user`는 예약어라 `users` 사용 |
| `@NoArgsConstructor(access = AccessLevel.PROTECTED)` | JPA용 기본 생성자, 외부에서 빈 객체 생성 방지 |
| `@Enumerated(EnumType.STRING)` | enum을 문자열로 저장 → 순서가 바뀌어도 안전 |
| 도메인 메서드 | setter 대신 `giveConsent()`처럼 규칙을 보장하는 메서드 사용 |
| `@OneToOne` | 1:1 관계. FK에 `unique = true` (Patient → User) |
| `@ManyToOne` | 다대일 관계. FK에 unique 없음 (PatientGuardian → Patient, User) |
| 관계의 주인 | **FK 컬럼이 있는 테이블**의 엔티티에 `@JoinColumn` |
| `@Column` vs `@JoinColumn` | 일반 값 컬럼 vs FK 컬럼. 관계 필드에 `@Column`을 함께 쓰면 에러 |
| `unique = true` | 컬럼 **하나**의 중복 방지 |
| `uniqueConstraints` | 컬럼 **여러 개 조합**의 중복 방지 |
| `FetchType.LAZY` | 필요할 때만 조회. `@OneToOne`, `@ManyToOne`은 기본값이 EAGER라 직접 지정 |
| N:M 관계 | 연결 테이블 + `@ManyToOne` 두 개로 구현 |
| DB의 Key 표시 | `PRI` 기본키, `UNI` 한 번만, `MUL` 여러 번 가능 |
| 조합 인덱스 | 맨 앞 컬럼만으로도 검색 가능 → FK 인덱스로 재활용 (leftmost prefix) |
| 뇌파 원본 데이터 | DB가 아닌 파일로 저장, DB에는 메타데이터만 |

### 면접용 답변 정리

**Q. 다대일 관계의 FK에 unique를 걸지 않은 이유는?**
> `patient_id`에 unique를 걸면 한 환자가 연결 테이블에 한 번만 저장될 수 있어서, 환자 한 명에게 보호자를 여러 명 연결할 수 없게 됩니다. 다대일 관계에서는 같은 FK 값이 여러 행에 나와야 하므로 unique를 걸지 않았습니다.

**Q. `unique = true`와 `uniqueConstraints`의 차이는?**
> `unique = true`는 컬럼 하나의 중복을 막고, `uniqueConstraints`는 여러 컬럼 조합의 중복을 막습니다. 환자와 보호자는 각각 여러 번 연결될 수 있지만, 같은 환자–보호자 쌍이 중복 등록되면 안 되기 때문에 두 컬럼 조합에 제약을 걸었습니다.

**Q. 익명 연구 코드를 UUID 앞 8글자로 만든 이유는?**
> 가독성과 충돌 가능성의 균형을 고려했고, 혹시 겹치더라도 `unique` 제약으로 DB가 저장을 거부하도록 안전장치를 두었습니다.

---

## 5. 테스트

| 개념 | 정리 |
|---|---|
| given / when / then | 준비 → 실행 → 확인. 한국어 할 일 목록을 먼저 쓰고 코드로 번역 |
| 단위 테스트 | Spring·DB 없이 객체 로직만 확인. 빠름 |
| 통합 테스트 | `@SpringBootTest` + 실제 DB. `@Transactional`로 끝나면 롤백 |
| `em.flush()` | 변경 내용을 DB에 반영 |
| `em.clear()` | JPA가 메모리(1차 캐시)에 들고 있던 객체 비우기 |
| 변경 감지 (Dirty Checking) | `save()` 없이도 관리 중인 객체 값이 바뀌면 UPDATE |
| 준영속 상태 (Detached) | `clear()` 이후의 옛 객체는 값을 바꿔도 DB 반영 안 됨 |
| `saveAndFlush()` | 저장 후 즉시 DB 반영 |
| `assertThatThrownBy` | 예외가 발생하는지 확인 |
| `DataIntegrityViolationException` | DB 제약조건(unique, not null 등) 위반 예외 |
| 테스트 피라미드 | 단위 테스트는 많이, 통합 테스트는 꼭 필요한 것만 |

---

## 6. 실수와 교훈

| 실수 | 교훈 |
|---|---|
| `.gitignore`의 점(.) 누락 | 파일 이름 하나가 보안을 좌우함 |
| `.gitignore`에 줄 끝 주석 작성 | 파일 형식마다 주석 규칙이 다름 |
| `application-local.yaml`이 커밋될 뻔함 | 커밋 전 `git status` 확인 필수 |
| `profiles.name`, `ture` 오타 | 로그의 Origin 줄 번호로 바로 찾기 |
| enum 오타(`LETFARM`), 코드와 ERD 불일치(`ETC`/`OTHER`) | enum 이름은 데이터가 쌓이기 전에 확정, 코드와 문서 일치 |
| 철회 시간을 `consentedAt`에 저장 | 필드 하나에는 의미 하나 |
| 다른 값을 확인하고도 통과한 테스트 | "이 테스트는 무엇이 고장 났을 때 실패하는가?" |
| 통합 테스트에서 DB를 다시 읽지 않음 | 로그에서 UPDATE/SELECT가 실제로 나갔는지 확인 |
| `clear()` 이후 옛 객체로 작업 | 새로 조회한 객체를 사용 |
| `Modifier.PROTECTED`를 잘못 import | `Alt + Enter` 추천 목록에서 패키지 이름 확인 |
| 관계 필드에 `@Column` 추가 | 어노테이션마다 쓰는 자리가 정해져 있음 |
| 테스트에 실제 이메일·이름 사용 | 공개 저장소에는 가짜 데이터만 |
| 머지된 브랜치에 다시 push | 머지 후에는 새 브랜치에서 작업 |

---

## 7. 남은 숙제

- [ ] README 굵은 글씨 표시 오류 수정
- [ ] `PatientGuardianRepositoryTest`의 `count()`를 "특정 환자의 연결 수"로 개선 (쿼리 메서드)
- [ ] ResearcherAssignment 엔티티 추가
- [ ] Service 계층과 REST API 시작 (W3)
