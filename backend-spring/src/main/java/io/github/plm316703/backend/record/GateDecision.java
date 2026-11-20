package io.github.plm316703.backend.record;

import io.github.plm316703.backend.gateway.RejectReason;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 관문 판단 기록 — gate_decisions 테이블.
 * 판별 메시지 한 개마다 한 줄. "왜 움직였는지 / 왜 멈췄는지"를 나중에 설명할 수 있게 남긴다.
 */
@Entity
@Table(name = "gate_decisions", indexes = @Index(columnList = "session_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GateDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id")
    private Long sessionId;             // 세션이 없을 때 온 메시지도 기록하므로 null 허용

    @Column(length = 36)
    private String wsConnectionId;      // 어느 WebSocket 연결에서 온 메시지인지 (Spring이 붙여 주는 연결 id)
    // 연결이 끊겼다 다시 붙으면 id가 바뀌므로, 기록을 연결 단위로 구분할 수 있음

    private Integer subject;            // 메시지에 적힌 피험자 번호
    // SUBJECT_MISMATCH로 차단됐을 때 "실제로 몇 번이 왔는지" 증거로 남음

    private Long seq;
    @Column(nullable = false)
    private LocalDateTime receivedAt;   // 관문이 받은 시각
    @Column(length = 40)
    private String commandTimestamp;    // 판별기가 보낸 시각(원본 문자열)

    @Column(length = 20)
    private String intent;
    private Double confidence;
    private Double signalQuality;
    @Column(length = 30)
    private String signalIssue;
    @Column(length = 200)
    private String badChannels;          // 문제 채널 목록을 쉼표로 이은 글자 (예: "C3,Cz,C4"). 정상이면 null
    @Column(length = 10)
    private String pythonDecision;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private GateResult result;
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private RejectReason reason;        // BLOCKED일 때 사유
    @Column(length = 300)
    private String detail;

    private Double armAngleAfter;       // 판단 뒤 팔꿈치 각도
    @Column(length = 20)
    private String trueLabel;           // 시연 전용 정답 (판단에는 쓰지 않음, 화면 표시용)
    @Column(length = 30)
    private String scenario;            // 재생기가 오류를 주입한 경우 시나리오 이름

    @Builder
    private GateDecision(Long sessionId, String wsConnectionId, Integer subject,
                         Long seq, String commandTimestamp, String intent,
                         Double confidence, Double signalQuality, String signalIssue, String badChannels,
                         String pythonDecision, GateResult result, RejectReason reason,
                         String detail, Double armAngleAfter, String trueLabel, String scenario) {
        this.sessionId = sessionId;
        this.wsConnectionId = wsConnectionId;   // 매개변수로 받은 연결 id를 필드에 저장
        this.subject = subject;                 // 매개변수로 받은 피험자 번호를 필드에 저장
        this.seq = seq;
        this.receivedAt = LocalDateTime.now();
        this.commandTimestamp = commandTimestamp;
        this.intent = intent;
        this.confidence = confidence;
        this.signalQuality = signalQuality;
        this.signalIssue = signalIssue;
        this.badChannels = badChannels;
        this.pythonDecision = pythonDecision;
        this.result = result;
        this.reason = reason;
        // DB 칸 길이(300)를 넘는 설명은 잘라서 저장 (깨진 메시지 원문이 길 수 있음)
        this.detail = detail == null ? null : detail.substring(0, Math.min(detail.length(), 300));
        this.armAngleAfter = armAngleAfter;
        this.trueLabel = trueLabel;
        this.scenario = scenario;
    }
}
