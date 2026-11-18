package io.github.plm316703.backend.session;

import io.github.plm316703.backend.patient.Patient;
import io.github.plm316703.backend.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 측정 세션 — sessions 테이블.
 * "어느 환자가, 어떤 장비(데이터)로, 언제부터 언제까지 로봇팔을 썼는가" 한 번 분량.
 * 관문은 판별 메시지를 "지금 열려 있는 세션"의 환자 기준으로 검사한다.
 */
@Entity
@Table(name = "sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MeasurementSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(nullable = false, length = 30)
    private String deviceCode;          // 장비 번호 (관제 담당자는 장비 번호 + 익명 코드만 봄)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SourceType sourceType;

    @Column(nullable = false)
    private int datasetSubject;         // 재생하는 공개 데이터의 피험자 번호 (메시지 subject와 일치해야 함)

    @Column(nullable = false)
    private LocalDateTime startedAt;

    @Column
    private LocalDateTime endedAt;      // null = 아직 진행 중

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "started_by", nullable = false)
    private User startedBy;

    public MeasurementSession(Patient patient, String deviceCode, SourceType sourceType,
                              int datasetSubject, User startedBy) {
        this.patient = patient;
        this.deviceCode = deviceCode;
        this.sourceType = sourceType;
        this.datasetSubject = datasetSubject;
        this.startedBy = startedBy;
        this.startedAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return endedAt == null;
    }

    public void end() {
        if (endedAt != null) {
            throw new IllegalStateException("이미 끝난 세션입니다.");
        }
        this.endedAt = LocalDateTime.now();
    }
}
