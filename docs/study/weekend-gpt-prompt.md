# 주말 공부 프롬프트 (GPT용) — Nuada Airgetlám으로 Java·Python 기초 다지기

> 사용법
> 1. **토요일:** 새 GPT 대화를 열고 아래 **[1] 시작 프롬프트** 전체를 붙여 넣는다.
> 2. GPT가 "📎 코드 J1을 붙여 주세요"처럼 요청하면, 이 파일 맨 아래 **[3] 코드 묶음**에서 그 부분을 복사해 붙인다.
> 3. **일요일:** 대화가 길어지면 느려지고 앞 내용을 잊으므로, 새 대화를 열고 **[1] + [2] 이어하기 프롬프트**를 붙인다.
> 4. 연습 코드는 저장소 밖 폴더(예: `D:\study\java`, `D:\study\python`)에 만든다. 이 저장소는 Public이고 실험 기록용이라 연습 파일이 섞이지 않게 한다.

---

## [1] 시작 프롬프트 (그대로 복사)

```text
너는 20년 차 개발자이자 프로그래밍 과외 선생님이야. 나는 프로그래밍 1년 미만 초보(국비 과정, Java·Spring·Python)야.
이번 주말(토·일) 이틀 동안 너와 공부할 거야. 모든 설명은 한국어로 해 줘.

## 내 상황과 목표
- AI(Claude Code)와 함께 "Nuada Airgetlám"이라는 프로젝트를 끝냈어. 그런데 코드를 대부분 AI가 썼기 때문에,
  솔직히 AI 없이는 콘솔 계산기 같은 단순한 프로그램도 혼자 못 만들어.
- 목표는 두 가지를 동시에 이루는 것:
  (1) 이 프로젝트의 전체 구조를 톱다운(큰 그림 → 세부)으로 이해하기
  (2) 빈 파일에서 Java·Python 기초 프로그램을 혼자 짤 수 있는 실력 기르기
- 그래서 "기초 문법 연습 → 그 문법이 실제로 쓰인 프로젝트 코드 읽기 → 프로젝트 코드의 축소판을 내가 직접 다시 짜기" 순서를 반복하고 싶어.

## 프로젝트 요약 (너는 저장소를 볼 수 없으니 이 내용을 기준으로 해)
- 한 줄 설명: 뇌파 판별 결과를 "안전 관문"이 다시 검사해 가상 로봇팔을 움직이고, 장비 오류가 나면
  그 자리에서 정지 + 기록 + RAG가 매뉴얼 근거로 점검 방법을 안내하는 관제 시스템.
- 전체 흐름:
  [Python 재생기] 공개 뇌파 데이터(BCI IV 2a) 시행 하나를 꺼냄 → 신호 품질 계산 → 판별기(CSP+LDA)로 왼손/오른손 + 확신도 계산
    → JSON 메시지로 만들어 WebSocket으로 전송
  [Spring 관문] 메시지 수신 → 8단계 검사 ①형식(JSON) ②칸·범위·버전 ③순서(seq) ④오래된 명령(2초) ⑤열린 세션·피험자 일치
    ⑥참여 중인 환자(동의) ⑦신호 품질 ⑧판단 일치(확신도 기준 0.75를 관문이 다시 계산)
    → 하나라도 걸리면 BLOCKED: 팔 정지 + DB 기록 + (장비 오류면) RAG에 안내 요청
    → 다 통과하면 MOVE(팔 움직임) / ABSTAIN(확신 부족, 보류) / HELD(팔이 정지 중이라 실행 안 함)
  [로봇팔 상태] IDLE(세션 없음) → STOPPED(정지, 수동 재개 대기) → RUNNING(동작) / RESET_REQUIRED(10초 이상 끊김)
    자동 재개는 없다. 사람이 재개 버튼을 눌러야 하고, 서버가 "연속 정상 메시지 수" 조건을 다시 검사한다.
  [Python RAG 서버] FastAPI. 점검 매뉴얼 8개를 '## 소제목' 단위로 잘라 Chroma에 색인 → 오류 설명으로 검색 → Gemini가 근거를 인용해 안내문 생성
  [관제 화면] HTML+JS, 실시간 그래프·채널 지도·오류 카드. 역할: 관제 담당자, 연구책임자, 환자, 보호자, 관리자 (로그인 JWT)
- 기술: Java 21, Spring Boot 4, JPA, MySQL, WebSocket / Python 3.11, numpy, scikit-learn, MNE, FastAPI, chromadb
- 내 설계 원칙 몇 가지: "관문은 판별기를 믿지 않는다", "이상한 입력이 와도 기록은 반드시 남긴다",
  "잘못된 요청은 IllegalArgumentException, 지금 상태로 불가능하면 IllegalStateException"

## 진행 규칙 (가장 중요)
1. 한 번에 한 단계만 진행해. 각 단계가 끝나면 멈추고 내가 "다음"이라고 할 때까지 기다려.
2. 연습 문제의 정답 코드를 먼저 보여 주지 마. 내가 먼저 짜서 붙여 넣으면 리뷰해 줘.
3. 막혔다고 하면 힌트를 3단계로: ① 필요한 개념 이름과 짧은 설명 → ② 한국어 의사코드(순서만) → ③ 핵심 2~3줄 예시.
   그래도 안 되면 그때 전체 풀이를 보여 주고, 다음 단계 전에 "안 보고 다시 쓰기"를 꼭 시켜.
4. 리뷰할 때 볼 것: 실행되는가 / 경계값(0, 음수, 빈 값, null)에서 안 터지는가 / 이름이 뜻을 말하는가 /
   예외를 알맞게 썼는가 / 프로젝트 코드와 비교해 무엇이 다르고 왜 그렇게 했을지.
   정답을 고쳐 주기보다 "어디가 왜 문제인지"를 짚고 내가 고치게 해.
5. 처음 나오는 개념은 짧게 풀어 줘. 영어 식별자는 한국어로 풀어서 말해 줘. (예: SequenceTracker → "순서 추적기")
6. 각 블록이 끝나면 이해 점검 질문 2~3개를 내 줘 (객관식 말고 내 말로 설명하게).
7. 각 블록 마지막에 "5분 백지 테스트": 아무것도 안 보고 그 블록의 핵심 함수 하나를 다시 쓰게 해. 못 쓴 부분은 "복습 목록"에 적어 둬.
8. 프로젝트 코드가 필요하면 "📎 코드 J1을 붙여 주세요"처럼 이름으로 요청해. 내가 붙여 주지 않은 프로젝트 내용은 지어내지 마.
9. 연습은 Spring 없이 순수 Java(main 메서드), 순수 Python(스크립트)으로 한다. 환경: IntelliJ, Java 21, Python 3.11, 콘솔 실행.
10. 블록을 시작할 때마다 "지금 위치: 토요일 J2 (3/7)"처럼 진도를 표시해.

## 이틀 계획 (블록 하나 = 약 60~90분, 내 속도에 맞춰 조절)

### 토요일 — 큰 그림 + Java
- T0. 톱다운 지도 (40분)
  위 흐름을 바탕으로 내가 종이에 "누가 누구에게 무엇을 보내는지" 그림을 그리게 하고, 내 설명을 듣고 빠진 곳을 질문으로 짚어 줘.
  그다음 오늘 배울 Java 문법(조건문, 메서드, 예외, enum, switch, Map, Optional, 클래스·생성자, record)이
  관문의 어느 부분에 쓰였는지 표로 정리해 줘.
- J1. 조건문·메서드·예외 → 📎 코드 J1 (DecisionGate)
  연습: 콘솔 사칙연산 계산기. Scanner로 "3 + 4" 입력 → 결과 출력, "q"면 종료.
  0으로 나누기·모르는 연산자는 IllegalArgumentException을 던지고 main에서 잡아 안내문 출력.
  프로젝트 연결: 생성자에서 설정값(0~1)을 검사해 시작 자체를 막는 이유, 삼항 연산자로 MOVE/ABSTAIN 정하기.
  축소판: 기준값을 받아 confidence와 decision이 일치하는지 검사하는 메서드를 내가 다시 짜기.
- J2. enum·switch·상태 기계 → 📎 코드 J2 (ArmState, ArmSupervisor 일부, FriendlyStopReason 일부)
  연습: 신호등 또는 자판기 상태 기계 (enum + switch 식).
  축소판: MiniArm 클래스 — 상태 IDLE/STOPPED/RUNNING, 각도 90도에서 시작, 10~150도 범위, 한 번에 10도.
  startSession(), stop(사유), resume()(연속 정상 3번 이상일 때만, 아니면 IllegalStateException), applyMove("left"/"right").
  main에서 시나리오를 돌려 "자동 재개는 없다"가 지켜지는지 출력으로 확인.
- J3. 컬렉션(Map·List)·Optional → 📎 코드 J3 (SequenceTracker)
  연습: 문장을 입력받아 단어별 개수를 HashMap으로 세고, 많은 순으로 출력.
  축소판: 연결 id별 마지막 seq를 기억해 중복·옛 번호를 거부하는 순서 추적기를 내가 다시 짜기 (Optional로 거부 이유 반환).
  질문으로 짚어 줄 것: HashMap과 ConcurrentHashMap 차이, null을 반환하지 않고 Optional을 쓰는 이유.
- J4. 클래스·생성자·문자열·반복 → 📎 코드 J4 (PasswordRules)
  연습: 임시 비밀번호 생성기(헷갈리는 글자 0/O/1/l 제외) + 새 비밀번호 검사기(길이, 이메일 앞부분 포함 금지).
  질문: Random과 SecureRandom 차이, StringBuilder를 쓰는 이유.
- J5. 종합: 순수 Java 미니 관문 → 📎 코드 J5 (GateService.handle 요약)
  record Message(long seq, String timestamp, double confidence, String decision, double quality) 를 만들고,
  순서 → 신선도(2초) → 신호 품질(0.5 미만 차단) → 판단 일치 순서로 검사해 MOVE/ABSTAIN/HELD/BLOCKED를 돌려주는 MiniGate를 짜기.
  J2의 MiniArm, J3의 순서 추적기를 재사용. main에서 정상·중복·오래됨·품질 낮음 메시지를 넣어 결과 확인.
  마지막에 "Spring은 여기에 무엇을 더해 주는가"(@Component, 생성자 주입, @Value 설정값)를 내가 직접 new로 조립한 코드와 비교해 설명해 줘.
- 토요일 마무리: 복습 목록 정리 + 오늘 배운 것을 내가 3줄로 요약하게 하기.

### 일요일 — Python + 두 언어 비교
- P1. 함수·조건·반복·예외 (Java와 비교)
  연습: 토요일 계산기를 Python으로 다시 짜기. 다 짜고 나면 Java 버전과 나란히 놓고 차이(타입, 예외, 입력 처리)를 표로.
- P2. dict·list·JSON → 📎 코드 P2 (build_message)
  연습: 학생 성적 dict 목록을 만들어 평균·최고점 계산, json.dumps로 출력.
  축소판: seq, timestamp(시간대 포함), confidence, decision을 가진 메시지 dict를 만드는 함수 +
  J5의 검사 중 2개(칸 누락, confidence 0~1 범위)를 Python으로 옮긴 validate 함수.
- P3. 클래스·스레드·잠금 → 📎 코드 P3 (Injector)
  연습: 은행 계좌 클래스(입금·출금, 잔액 부족이면 ValueError).
  축소판: 오류 주입기 Injector(trigger, take)를 내가 다시 짜고, 스레드 2개가 동시에 쓰면 왜 Lock이 필요한지 설명해 줘.
  Java의 synchronized(ArmSupervisor)와 비교.
- P4. numpy 기초 → 📎 코드 P4 (signal_quality.assess)
  연습: 숫자 리스트의 평균·표준편차를 for문으로 직접 계산 → 같은 것을 numpy로 → 결과 비교.
  축소판: (채널 5개, 시간 100) 가짜 배열을 만들고, 한 채널을 거의 0으로(평탄), 한 채널을 크게(튐) 망가뜨린 뒤
  "중앙값의 10% 미만 / 5배 초과" 규칙으로 문제 채널 이름 목록을 찾는 함수 짜기.
- P5. 판별 결과 평가 → 📎 코드 P5 (step8 evaluate)
  연습: 확신도 리스트와 정답 여부 리스트로, 기준값 t 이상만 답했을 때 "답변율"과 "답한 것 중 정확도"를 순수 Python으로 계산.
  그다음 numpy 불리언 마스크로 같은 계산. 기준값을 0.5~0.95로 바꿔 가며 표로 출력.
  질문: 기준값을 높이면 왜 답변율은 떨어지고 정확도는 올라가는지.
- P6. 문자열·파일·정규식 → 📎 코드 P6 (rag load_chunks)
  연습: 직접 만든 md 파일 2개를 읽어 '## ' 소제목 단위로 자르고 (파일 이름, 소제목, 본문 길이)를 출력.
  질문: RAG에서 문서를 왜 조각으로 자르는지, 조각을 너무 크게/작게 자르면 무엇이 문제인지.
- P7. 최종 백지 테스트 (60분)
  아무것도 안 보고, 시간을 재면서: (1) Java 콘솔 계산기 (2) Python 콘솔 계산기 (3) Java 또는 Python 순서 추적기.
  끝나면 이틀간의 복습 목록을 보고, 다음 주에 매일 30분씩 할 복습 계획을 짜 줘.

## 시작
먼저 T0부터 시작해. 위 프로젝트 흐름을 너의 말로 아주 짧게(5줄 이내) 다시 정리하지 말고,
나에게 "이 시스템을 한 문장으로 설명해 보라"는 질문부터 던져.
```

---

## [2] 이어하기 프롬프트 (일요일 새 대화에서 [1] 다음에 붙임)

```text
(위 시작 프롬프트에 이어서)
어제 토요일 블록 T0~J5를 마쳤어. 아래는 어제 남긴 복습 목록과 내 3줄 요약이야.
- 복습 목록: (여기에 붙여 넣기)
- 3줄 요약: (여기에 붙여 넣기)
오늘은 일요일 P1부터 시작해. 시작 전에 복습 목록에서 5분짜리 워밍업 문제 2개만 내 줘.
```

---

## [3] 코드 묶음 (GPT가 요청할 때 해당 부분만 복사)

> 실제 프로젝트 코드에서 공부에 필요한 부분만 추렸다. 전체 파일은 저장소의 경로에서 볼 수 있다.

### 📎 코드 J1 — DecisionGate (관문 규칙 ⑧ 판단 일치)
경로: `backend-spring/src/main/java/io/github/plm316703/backend/gateway/DecisionGate.java`

```java
@Component
public class DecisionGate {

    // 관문 자신의 기준값 (메시지의 threshold 는 믿지 않음)
    private final double moveThreshold;

    public DecisionGate(@Value("${gateway.move-threshold}") double moveThreshold) {
        // 설정값이 말이 안 되면(예: 1.5, -0.1) 시작 단계에서 바로 멈춤
        if (!(moveThreshold >= 0.0 && moveThreshold <= 1.0)) {
            throw new IllegalStateException("gateway.move-threshold 는 0~1 이어야 함: " + moveThreshold);
        }
        this.moveThreshold = moveThreshold;
    }

    public Optional<MessageValidator.Rejection> check(ClassifierMessage m) {
        // 관문이 직접 다시 계산한 판단
        String gatewayDecision = m.confidence() >= moveThreshold ? "MOVE" : "ABSTAIN";

        // Python 판단과 다르면 거부 → 어느 쪽으로 달라도 최종적으로 움직이지 않음
        if (!gatewayDecision.equals(m.decision())) {
            return Optional.of(new MessageValidator.Rejection(
                    RejectReason.DECISION_MISMATCH,
                    "python=" + m.decision() + " gateway=" + gatewayDecision
                            + " conf=" + m.confidence() + " th=" + moveThreshold));
        }
        return Optional.empty();   // 의견 일치
    }
}
```

### 📎 코드 J2 — ArmState / ArmSupervisor 일부 / FriendlyStopReason 일부
경로: `backend-spring/.../arm/ArmState.java`, `arm/ArmSupervisor.java`, `patient/FriendlyStopReason.java`

```java
public enum ArmState {
    IDLE,            // 세션 없음
    STOPPED,         // 그 자리에서 정지. 수동 재개 대기
    RUNNING,         // 관문을 통과한 명령만 실행
    RESET_REQUIRED   // 오래 끊김. 초기화(기본 자세)를 먼저 해야 재개 가능
}
```

```java
// ArmSupervisor 일부 — 여러 스레드가 동시에 건드리므로 synchronized
public static final double HOME_ANGLE = 90.0;
public static final double MIN_ANGLE = 10.0;
public static final double MAX_ANGLE = 150.0;
public static final double STEP = 10.0;

private ArmState state = ArmState.IDLE;
private double angle = HOME_ANGLE;
private int consecutiveClean;        // 연속 정상 메시지 수 (차단되면 0으로)

/** 세션 시작: 정지 상태로 시작한다 (자동 시작 없음). */
public synchronized void startSession(Long sessionId, Long patientId, String patientCode, int datasetSubject) {
    this.state = ArmState.STOPPED;
    this.angle = HOME_ANGLE;
    this.consecutiveClean = 0;
    // ... (나머지 필드 초기화 생략)
}

public synchronized void markClean() {
    this.consecutiveClean++;
}

/** 정지. RUNNING이었으면 STOPPED로 바꾸고 true (= "지금 막 멈췄다"). */
public synchronized boolean stop(String reason) {
    this.consecutiveClean = 0;
    if (state == ArmState.RUNNING) {
        state = ArmState.STOPPED;
        // ... 사유·시각 저장
        return true;
    }
    return false;
}

/** 통과한 MOVE 명령 실행. RUNNING일 때만 움직이고 true. */
public synchronized boolean applyMove(String intent) {
    if (state != ArmState.RUNNING) {
        return false;
    }
    // ... (각도 계산 부분은 직접 짜 보기)
}
```

```java
// FriendlyStopReason 일부 — 환자·보호자용 쉬운 문장
public static String nowMessage(ArmState state, String rawReason) {
    return switch (state) {
        case RUNNING -> "팔이 정상적으로 움직이고 있어요.";
        case IDLE -> "지금은 측정 세션이 없어요.";
        case STOPPED -> reason(rawReason) + " 관제 담당자가 확인한 뒤 다시 움직여요.";
        case RESET_REQUIRED -> "장비 연결이 오래 끊겨 멈췄어요. 관제 담당자가 팔을 처음 자세로 되돌린 뒤 다시 시작해요.";
    };
}
```

### 📎 코드 J3 — SequenceTracker (관문 규칙 ③ 순서)
경로: `backend-spring/.../gateway/SequenceTracker.java`

```java
@Slf4j
@Component
public class SequenceTracker {

    // 장부: session id → 마지막으로 통과한 seq
    // ConcurrentHashMap: 여러 스레드가 동시에 읽고 써도 안전한 Map
    private final Map<String, Long> lastSeqBySession = new ConcurrentHashMap<>();

    public Optional<MessageValidator.Rejection> check(String sessionId, long seq) {
        Long last = lastSeqBySession.get(sessionId); // 처음 온 연결이면 null

        // 이미 받은 번호와 같거나 작음 → 중복 또는 옛 명령 → 거부 (장부는 그대로)
        if (last != null && seq <= last) {
            return Optional.of(new MessageValidator.Rejection(
                    RejectReason.OUT_OF_ORDER, "seq=" + seq + " last=" + last));
        }
        // 번호가 건너뛰었음 (예: 6 다음 9) → 거부는 안 하지만 기록은 남김
        if (last != null && seq > last + 1) {
            log.warn("seq 건너뜀 id={} last={} seq={} missing={}", sessionId, last, seq, seq - last - 1);
        }

        lastSeqBySession.put(sessionId, seq);   // 통과한 경우에만 장부 갱신
        return Optional.empty();
    }

    /** 연결이 끊기면 그 연결의 장부를 지움 (안 지우면 메모리가 계속 쌓임) */
    public void forget(String sessionId) {
        lastSeqBySession.remove(sessionId);
    }
}
```

### 📎 코드 J4 — PasswordRules (임시 비밀번호 생성·검사)
경로: `backend-spring/.../user/PasswordRules.java`

```java
// 0/O/o, 1/l/I 처럼 손으로 옮겨 적을 때 헷갈리는 글자를 뺀 문자표
private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
private static final int MAX_LENGTH = 64;   // BCrypt는 72바이트까지만 비교하므로 상한

// 예측할 수 없는 난수가 필요하므로 일반 Random이 아니라 SecureRandom
private final SecureRandom random = new SecureRandom();

public String generateTemp() {
    StringBuilder sb = new StringBuilder(tempLength);
    for (int i = 0; i < tempLength; i++) {
        sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
    }
    return sb.toString();
}

public void validateNew(String password, String email) {
    if (password == null || password.length() < minLength) {
        throw new IllegalArgumentException("새 비밀번호는 " + minLength + "자 이상이어야 합니다.");
    }
    if (password.length() > MAX_LENGTH) {
        throw new IllegalArgumentException("새 비밀번호는 " + MAX_LENGTH + "자 이하로 해 주세요.");
    }
    if (password.isBlank()) {
        throw new IllegalArgumentException("공백만으로 된 비밀번호는 쓸 수 없습니다.");
    }
    String local = email == null ? "" : email.split("@")[0].toLowerCase(Locale.ROOT);
    // 너무 짧은 이메일 앞부분(예: "a")까지 막으면 정상 비밀번호도 거절되므로 3자 이상일 때만 검사
    if (local.length() >= 3 && password.toLowerCase(Locale.ROOT).contains(local)) {
        throw new IllegalArgumentException("비밀번호에 이메일 앞부분(" + local + ")을 넣을 수 없습니다.");
    }
}
```

### 📎 코드 J5 — GateService.handle 요약 (관문 본체) + FreshnessChecker 핵심
경로: `backend-spring/.../gateway/GateService.java`, `gateway/FreshnessChecker.java`

```java
/** WebSocket 담당자가 메시지를 받을 때마다 호출 */
public void handle(String connectionId, String payload) {
    Instant now = Instant.now();
    arm.markReceived(now);   // 내용이 엉망이어도 "연결은 살아 있음" (워치독용)

    // ① 형식
    ClassifierMessage msg;
    try {
        msg = objectMapper.readValue(payload, ClassifierMessage.class);
    } catch (JacksonException e) {
        // 읽을 수 없는 메시지는 절대 MOVE로 이어지지 않게 여기서 끝냄
        block(null, connectionId, RejectReason.INVALID_FORMAT, e.getOriginalMessage(), ErrorType.INVALID_MESSAGE);
        return;
    }

    // ② 칸·범위·버전
    Optional<MessageValidator.Rejection> r = validator.validate(msg);
    if (r.isPresent()) { block(msg, connectionId, r.get(), ErrorType.INVALID_MESSAGE); return; }

    // ③ 순서
    r = sequenceTracker.check(connectionId, msg.seq());
    if (r.isPresent()) { block(msg, connectionId, r.get(), ErrorType.INVALID_MESSAGE); return; }

    // ④ 오래된 명령
    r = freshnessChecker.check(msg.timestamp(), now);
    if (r.isPresent()) { /* ... */ block(msg, connectionId, r.get(), /* 오류 종류 */ null); return; }

    // ⑤ 열린 세션·피험자 일치, ⑥ 참여 중인 환자 (DB 조회) — 생략

    // ⑦ 신호 품질
    if (msg.signal().quality() < minSignalQuality) { /* ... */ block(/* ... */); return; }

    // ⑧ 판단 일치
    r = decisionGate.check(msg);
    if (r.isPresent()) { block(msg, connectionId, r.get(), ErrorType.DECISION_MISMATCH); return; }

    // 모든 검사 통과
    arm.markClean();
    GateResult result;
    if ("ABSTAIN".equals(msg.decision())) {
        result = GateResult.ABSTAIN;                       // 정상 보류 (확신도 부족)
    } else {
        result = arm.applyMove(msg.intent()) ? GateResult.MOVE : GateResult.HELD;
    }
    GateDecision saved = decisionRepository.save(decisionOf(connectionId, msg, result, null, null));
    publish(saved);   // 관제 화면에 실시간 전송
}
```

```java
// FreshnessChecker 핵심 — timestamp에는 시간대(+09:00)가 반드시 있어야 한다
public Optional<MessageValidator.Rejection> check(String timestamp, Instant now) {
    Instant sentAt;
    try {
        sentAt = OffsetDateTime.parse(timestamp).toInstant();   // 시간대가 없으면 여기서 실패
    } catch (DateTimeParseException e) {
        return Optional.of(new MessageValidator.Rejection(RejectReason.INVALID_FIELD,
                "timestamp 형식 오류(시간대 필요): " + timestamp));
    }
    long ageMs = Duration.between(sentAt, now).toMillis();
    if (ageMs > staleMs) {          // 기본 2000ms
        return Optional.of(new MessageValidator.Rejection(RejectReason.STALE_COMMAND,
                "age=" + ageMs + "ms limit=" + staleMs + "ms"));
    }
    return Optional.empty();
}
```

### 📎 코드 P2 — build_message (재생기: 시행 하나 → 메시지)
경로: `ai-server-python/realtime/replayer.py`

```python
def build_message(seq, trial, label, model, meta, kind, rng):
    """시행 하나 → 규격 v2 메시지 (dict)"""
    x = sq.inject(trial, kind, rng) if kind in SIGNAL_KINDS else trial
    signal = sq.assess(x)
    proba = model.predict_proba(x[np.newaxis])[0]
    best = int(proba.argmax())
    confidence = float(proba[best])
    threshold = meta["threshold"]
    decision = "MOVE" if confidence >= threshold else "ABSTAIN"
    if kind == "mismatch":
        decision = "ABSTAIN" if decision == "MOVE" else "MOVE"   # 판별기 쪽 이상을 흉내냄

    sent_at = datetime.now().astimezone()                         # 시간대(+09:00) 포함
    if kind == "stale":
        sent_at -= timedelta(seconds=5)

    return {
        "version": 2,
        "type": "classification",
        "seq": seq,
        "timestamp": sent_at.isoformat(timespec="milliseconds"),
        "subject": meta["subject"],
        "intent": str(model.classes_[best]),
        "confidence": round(confidence, 3),
        "threshold": threshold,
        "decision": decision,
        "signal": signal,
        "scenario": kind,                                        # 정상이면 None → JSON null
        "true_label": str(label),                                # 시연 전용 정답 (관문 판단에 쓰지 않음)
    }
```

### 📎 코드 P3 — Injector (오류 주입 상태, 스레드 2개가 함께 씀)
경로: `ai-server-python/realtime/replayer.py`

```python
class Injector:
    """지금 주입 중인 오류 상태. 키보드 스레드와 전송 스레드가 함께 쓰므로 잠금 사용"""

    def __init__(self, length):
        self.lock = threading.Lock()
        self.kind = None
        self.left = 0
        self.length = length

    def trigger(self, kind, source="키보드"):
        with self.lock:
            self.kind = kind
            self.left = self.length if kind in SIGNAL_KINDS else 1
        print(f"\n▶ 다음 시행부터 '{kind}' 주입 ({source})", flush=True)

    def take(self):
        """이번 시행에 적용할 오류 종류 (없으면 None)"""
        with self.lock:
            if self.left <= 0:
                return None
            self.left -= 1
            return self.kind
```

### 📎 코드 P4 — signal_quality.assess (신호 품질 계산)
경로: `ai-server-python/realtime/signal_quality.py`

```python
CHANNELS = ["Fz", "FC3", "FC1", "FCz", "FC2", "FC4", "C5", "C3", "C1", "Cz", "C2",
            "C4", "C6", "CP3", "CP1", "CPz", "CP2", "CP4", "P1", "Pz", "P2", "POz"]

FLAT_RATIO = 0.1     # 채널 표준편차가 중앙값의 10% 미만 → 평탄(전극 떨어짐)
SPIKE_RATIO = 5.0    # 채널 표준편차가 중앙값의 5배 초과 → 튐(전극 흔들림)
BAND_LIMIT = 0.3     # 60Hz·고주파 힘이 전체의 30% 초과 → 잡음
MOTOR_CHANNELS = {"C3", "Cz", "C4"}
CHANNEL_PENALTY = 0.1
MOTOR_PENALTY = 0.2

def assess(trial):
    """trial: (채널 22, 시간) 배열 → {"quality", "issue", "bad_channels"}"""
    stds = trial.std(axis=-1)
    med = float(np.median(stds)) + 1e-30
    flat = stds < FLAT_RATIO * med
    spike = stds > SPIKE_RATIO * med
    bad = flat | spike
    bad_channels = [CHANNELS[i] for i in np.flatnonzero(bad)]

    line = float(np.median(_band_ratio(trial, 58, 62)))      # 60Hz 근처 힘의 비율
    muscle = float(np.median(_band_ratio(trial, 40, 120)))   # 40Hz 이상 힘의 비율

    # 나쁜 채널 하나당 0.1, 운동 영역(C3·Cz·C4) 채널이면 0.2 더 깎는다
    motor_bad = sum(1 for name in bad_channels if name in MOTOR_CHANNELS)
    channel_score = max(0.0, 1.0 - CHANNEL_PENALTY * len(bad_channels) - MOTOR_PENALTY * motor_bad)
    noise_score = 1.0 - min(1.0, max(line, muscle))
    quality = round(float(np.clip(channel_score * noise_score, 0.0, 1.0)), 3)

    issue = None
    if bad.any():
        issue = "ELECTRODE_CONTACT"
    elif line > BAND_LIMIT:
        issue = "LINE_NOISE"
    elif muscle > BAND_LIMIT:
        issue = "MUSCLE_ARTIFACT"
    return {"quality": quality, "issue": issue, "bad_channels": bad_channels}
```

### 📎 코드 P5 — step8 evaluate (판단 보류 기준값 고르기)
경로: `ai-server-python/classifier/step8_threshold.py`

```python
CANDIDATES = np.round(np.arange(0.50, 0.96, 0.05), 2)     # 후보 기준값: 0.50, 0.55, ..., 0.95
MIN_ANSWER_RATE = 0.90

def evaluate(confidence, correct, t):
    """기준값 T를 적용했을 때 (답변율, 답한 것 중 정확도)를 돌려준다."""
    answered = confidence >= t  # 기준 이상만 답함
    rate = answered.mean()
    acc = correct[answered].mean() if answered.any() else np.nan
    return rate, acc

# 규칙 1: 답변율 90% 이상인 기준값만 남김
# 규칙 2: 그중 정확도가 가장 높은 값, 같으면 더 낮은 기준값
ok = table[table["답변율"] >= MIN_ANSWER_RATE]
chosen = ok.loc[ok["정확도"].idxmax(), "기준값"] if len(ok) else 0.5
```

### 📎 코드 P6 — rag_server.load_chunks (매뉴얼 → 조각)
경로: `ai-server-python/rag/rag_server.py`

```python
def load_chunks():
    """매뉴얼 파일을 '## 소제목' 단위로 자른다. 조각 = (id, 본문, 메타데이터)"""
    chunks = []
    for path in sorted(MANUAL_DIR.glob("*.md")):
        error_type = path.stem
        text = path.read_text(encoding="utf-8")
        title = text.splitlines()[0].lstrip("# ").strip()
        for part in re.split(r"\n(?=## )", text):
            if not part.startswith("## "):
                continue
            section = part.splitlines()[0][3:].strip()
            body = f"{title}\n{part.strip()}"
            chunks.append((f"{error_type}#{section}", body,
                           {"error_type": error_type, "doc": path.name, "section": section}))
    return chunks


def manuals_fingerprint(chunks):
    """매뉴얼 내용이 바뀌면 지문이 바뀜 → 새 컬렉션으로 다시 임베딩"""
    h = hashlib.sha256(EMBED_MODEL.encode())
    for cid, body, _ in chunks:
        h.update(cid.encode())
        h.update(body.encode())
    return h.hexdigest()[:12]
```
