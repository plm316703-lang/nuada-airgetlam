package io.github.plm316703.backend.record;

import io.github.plm316703.backend.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 관제 동작 기록 — control_events 테이블.
 * 정지·재개·초기화·세션 시작/종료를 "누가(사람 또는 시스템) 언제 왜" 했는지 남긴다.
 */
@Entity
@Table(name = "control_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ControlEvent {

    public enum Type {
        SESSION_START, SESSION_END,
        STOP,            // 차단·끊김·철회로 정지 (시스템)
        RESUME,          // 수동 재개 성공
        RESUME_DENIED,   // 재개 요청했지만 조건 미충족
        RESET_REQUIRED,  // 오래 끊겨 초기화 필요 상태로
        RESET            // 초기화 (기본 자세)
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id")
    private Long sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;                 // null = 시스템이 자동으로 함

    @Column(length = 300)
    private String detail;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    public ControlEvent(Long sessionId, Type type, User actor, String detail) {
        this.sessionId = sessionId;
        this.type = type;
        this.actor = actor;
        this.detail = detail == null ? null : detail.substring(0, Math.min(detail.length(), 300));
        this.occurredAt = LocalDateTime.now();
    }
}
