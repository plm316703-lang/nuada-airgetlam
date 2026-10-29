package io.github.plm316703.backend.patient;


import io.github.plm316703.backend.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import lombok.AccessLevel;

import java.time.LocalDateTime;

;

/**
 * 환자-보호자 연결 엔티티 — patient_guardians 테이블
 * 환자와 보호자의 N:M 관계를 풀기 위한 연결 테이블
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "patient_guardians",
        uniqueConstraints = @UniqueConstraint(columnNames = {"patient_id", "guardian_id"}))
public class PatientGuardian {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //환자와의 다대일 관계
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    //보호자와의 다대일 관계
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guardian_id", nullable = false)
    private User guardian;

    //관계종류
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GuardianRelation relation;

    //연결시간
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    //생성자
    public PatientGuardian(Patient patient, User guardian, GuardianRelation relation){
        this.patient = patient;
        this.guardian = guardian;
        this.relation = relation;
    }




}
