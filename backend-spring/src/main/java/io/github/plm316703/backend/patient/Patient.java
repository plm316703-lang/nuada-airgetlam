package io.github.plm316703.backend.patient;

import io.github.plm316703.backend.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 환자 정보 엔티티 — patients 테이블
 * 환자 역할의 회원(User)마다 하나씩 존재 (1:1)
 */


@Entity
@Table(name = "patients")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)   //언제 가져올지(지연타입)
    @JoinColumn(name = "user_id", unique = true, nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AmputationSide amputationSide;

    @Column(nullable = false)
    private boolean researchConsent;

    @Column(nullable = true)
    private LocalDateTime consentedAt; //연구 동의 시간

    @Column(nullable = true)
    private LocalDateTime consentWithdrawnAt;   //연구 동의 철회환시간

    @Column(nullable = true)
    private LocalDateTime enrolledAt;   //연구 등록 시간 (연구책임자가 등록, 철회해도 유지)


    //익명 연구 코드, 중복 불가
    @Column(nullable = false, unique = true, length = 20)
    private String researchCode;

    /**
     * 환자 정보 생성자
     * 밖에서 받을 것 : 회원(user), 절단 부위(amputationSide)
     *안에서 정할것 : 연구 동의 여부(처음엔 false), 익명 연구 코드(자동생성)
     */
    public Patient(User user, AmputationSide amputationSide) {
       this.user = user;
       this.amputationSide = amputationSide;
       this.researchConsent = false;    //가입 시점에서는 동의하지 않은 상태
        //UUID: 전 세계에서 겹치지 않는 랜덤 문자열 (ex: 3f9a2cab-...)
        //앞 8글자만 잘라서 짧은 익명 코드로 사용
        this.researchCode = "P-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 연구 동의하기
     * setter 대신 이 메서드를 쓰면 "동의 = 시간 기록"이 항상 함꼐 일어남
     */
    public void giveConsent(){
        this.researchConsent = true;
        this.consentedAt = LocalDateTime.now(); //동의한 순간의 시간 기록
    }

    /**
     * 연구 동의 철회
     */
    public void withdrawConsent(){
        this.researchConsent = false;
        this.consentWithdrawnAt = LocalDateTime.now(); //동의 철회 순간의 시간 기록
    }

    /**
     * 연구 등록 (연구책임자가 한다).
     * 등록 기록은 동의를 철회해도 지우지 않는다 → 다시 동의하면 자동으로 다시 "참여 중"
     */
    public void enroll(){
        if(this.enrolledAt != null){
            throw new IllegalStateException("이미 연구에 등록된 환자입니다.");
        }
        this.enrolledAt = LocalDateTime.now();
    }

    /**
     * 관문 규칙 ①: 지금 로봇팔을 써도 되는 "참여 중인 환자"인가?
     * = 연구 등록 기록이 있고(enrolledAt) AND 지금 동의 중(researchConsent)
     */
    public boolean isParticipating(){
        return this.enrolledAt != null && this.researchConsent;
    }

}
