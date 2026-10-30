package io.github.plm316703.backend.patient;


import io.github.plm316703.backend.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
        * 연구자-환자 배정 엔티티 — researcher_assignments 테이블
 *
         * 연구자가 특정 환자를 "배정"받아 데이터를 열람할 수 있도록 하는 연결 테이블.
        * 구조는 PatientGuardian과 같음 (N:M을 @ManyToOne 두 개로 풀기).
        *
        * 차이점:
        *  - 관계 종류(relation)가 없음
 *  - 생성 시간 이름이 assignedAt (배정된 시간)
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "researcher_assignments",
        //같은 연구자에게 같은 환자가 두 번 배정되지 않도록 조합 unique
        uniqueConstraints = @UniqueConstraint(columnNames = {"patient_id", "researcher_id"})
)
public class ResearcherAssignment {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //배정된 환자 (다대일 : 한 환자는 여러 연구자에게 배정될 수 있음)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    //배정받은 연구자(다대일: 한 연구자는 여러 한자를 배정받을 수 있음)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "researcher_id", nullable = false)
    private User researcher;

    //배정된 시간 - 저장될 떄 자동 기록, 이후 수정 불가
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime assignedAt;


    /**
     * 배정 생성자
     * 밖에서 받을 것: 환자, 연구자
     * 자동으로 정해지는 것: id(DB), assignedAt(@CreationTimestamp)
     */
    public ResearcherAssignment(Patient patient, User researcher){
        this.patient = patient;
        this. researcher = researcher;
    }

}
