package io.github.plm316703.backend.record;

import io.github.plm316703.backend.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 장비·통신 오류 기록 — device_errors 테이블.
 * 같은 오류가 해결 전에 계속 들어오면 새 줄을 만들지 않고 횟수(occurrenceCount)만 올린다.
 * RAG 안내 결과도 여기에 함께 저장한다 (안내만 저장, 제어는 하지 않음).
 */
@Entity
@Table(name = "device_errors")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeviceError {

    public enum RagStatus { PENDING, DONE, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id")
    private Long sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ErrorType errorType;

    @Column(length = 300)
    private String detail;
    private Double signalQuality;
    @Column(nullable = false)
    private boolean injected;           // 재생기 시나리오로 일부러 만든 오류인가 (화면·기록에 표시)

    @Column(nullable = false)
    private LocalDateTime occurredAt;
    @Column(nullable = false)
    private LocalDateTime lastSeenAt;
    @Column(nullable = false)
    private int occurrenceCount;

    private LocalDateTime resolvedAt;   // 수동 재개로 해결 처리된 시각
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by")
    private User resolvedBy;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private RagStatus ragStatus;
    @Lob
    @Column(columnDefinition = "TEXT")
    private String ragAnswer;
    @Lob
    @Column(columnDefinition = "TEXT")
    private String ragSources;          // 근거 목록 (JSON 문자열)
    private Boolean ragEvidenceSufficient;

    public DeviceError(Long sessionId, ErrorType errorType, String detail, Double signalQuality, boolean injected) {
        this.sessionId = sessionId;
        this.errorType = errorType;
        this.detail = detail == null ? null : detail.substring(0, Math.min(detail.length(), 300));
        this.signalQuality = signalQuality;
        this.injected = injected;
        this.occurredAt = LocalDateTime.now();
        this.lastSeenAt = this.occurredAt;
        this.occurrenceCount = 1;
        this.ragStatus = RagStatus.PENDING;
    }

    /** 해결 전에 같은 오류가 또 들어옴 */
    public void seenAgain(Double signalQuality) {
        this.lastSeenAt = LocalDateTime.now();
        this.occurrenceCount++;
        if (signalQuality != null) this.signalQuality = signalQuality;
    }

    public void resolve(User by) {
        this.resolvedAt = LocalDateTime.now();
        this.resolvedBy = by;
    }

    public void ragDone(String answer, String sourcesJson, boolean evidenceSufficient) {
        this.ragStatus = RagStatus.DONE;
        this.ragAnswer = answer;
        this.ragSources = sourcesJson;
        this.ragEvidenceSufficient = evidenceSufficient;
    }

    public void ragFailed(String why) {
        this.ragStatus = RagStatus.FAILED;
        this.ragAnswer = why;
    }
}
