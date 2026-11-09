package io.github.plm316703.backend.patient;

import io.github.plm316703.backend.user.Role;
import io.github.plm316703.backend.user.User;
import io.github.plm316703.backend.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 연구동의 서비스(ResearchConsentService) 검사
 *
 * 검사하는 규칙 3가지
 *  1. 동의한 환자가 철회하면 → 동의가 꺼지고, 그 환자의 연구자 배정이 "전부" 지워진다
 *  2. 없는 환자 번호로 철회하면 → 거절 (IllegalArgumentException: 잘못된 주문)
 *  3. 한 번도 동의한 적 없는 환자가 철회하면 → 거절 (IllegalStateException: 지금 상태로는 안 됨)
 *
 * @SpringBootTest : 공장(Spring)과 창고(DB)를 진짜로 켜고 검사
 * @Transactional  : 검사가 끝나면 창고를 원래대로 되돌림(롤백)
 */
@SpringBootTest 
@Transactional 
public class ResearchConsentServiceTest {

    @Autowired ResearchConsentService researchConsentService;                   // 검사 대상: 연구동의 주방장
    @Autowired ResearcherAssignmentRepository researcherAssignmentRepository;   // 결과 확인용: 배정 창고
    @Autowired UserRepository userRepository;                                   // 준비물: 회원 창고
    @Autowired PatientRepository patientRepository;                             // 준비물 + 결과 확인용: 환자 창고
    @Autowired EntityManager em;                                                // 메모리(1차 캐시)를 비울 때 사용

    @Test 
    @DisplayName ("동의한 환자가 철회하면 동의가 꺼지고, 그 환자의 연구자 배정이 전부 지워진다")
    void withdrawDeletesAllAssignments(){
        // 1) 환자 회원 저장 (역할: 환자)
        User patientUser = userRepository.save(
            new User("patient@test.com", "1234", "테스트환자", Role.PATIENT));
        // 2) 연구자 회원 2명 저장 (역할: 연구자)
        //    2명으로 하는 이유: deleteAllByPatient의 "All(전부)"이 진짜 전부 지우는지 확인하려고
        //    (이메일은 unique라서 서로 달라야 함)
        User researcher1 = userRepository.save(
                new User("researcher1@test.com", "1234", "테스트연구자1", Role.RESEARCHER));
        User researcher2 = userRepository.save(
                new User("researcher2@test.com", "1234", "테스트연구자2", Role.RESEARCHER));

        //3) 환자 회원으로 환자 정보 저장
        Patient patient = patientRepository.save(
                new Patient(patientUser, AmputationSide.LEFT_ARM));

        //4) 연구에 동의 시키기( 동의해야 배정할수 있고, 철회도 할 수 있음)
        patient.giveConsent();

        //5) 두 연구자에게 배정
        researcherAssignmentRepository.save(new ResearcherAssignment(patient, researcher1));
        researcherAssignmentRepository.save(new ResearcherAssignment(patient, researcher2));

        //when == 주방장에게 "이 환자 철회해 주세요" 주문(번호로 주문
        Long deletedCount = researchConsentService.withdraw(patient.getId());

        // DB에 실제로 반영(flush)하고 메모리를 비워서(clear),
        // 아래 확인이 메모리가 아니라 "진짜 DB"를 보도록 만듦
        em.flush();
        em.clear();

        // ===then 확인===

        //1) 지워진 배정이 2건인가
        assertThat(deletedCount).isEqualTo(2L);

        //2) DB에서도 두 배정이 정말 사라졌는가?
        assertThat(researcherAssignmentRepository.existsByPatientAndResearcher(patient, researcher1)).isFalse();
        assertThat(researcherAssignmentRepository.existsByPatientAndResearcher(patient, researcher2)).isFalse();

        //3) 환자를 DB에서 다시 꺼내서 동의가 꺼지고 철회 시간이 기록 됬는가?
        Patient reloaded = patientRepository.findById(patient.getId()).orElseThrow();
        assertThat(reloaded.isResearchConsent()).isFalse();
        assertThat(reloaded.getConsentWithdrawnAt()).isNotNull();

    }
    @Test
    @DisplayName("존재하지 않는 환자 번호로 철회하면 예외가 발생한다")
    void withdrawFailsWhenPatientNotFound(){
        //given : 아무것도 저장하지 않음
        Long notExistingId = -1L;

        // when & then: "잘못된 주문이에요" 예외 + 메시지에 "존재하지"가 들어 있는가?
        assertThatThrownBy(() -> researchConsentService.withdraw(notExistingId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("존재하지");
    }

    @Test
    @DisplayName("한 번도 동의한 적 없는 환자가 철회하면 예외가 발생한다")
    void withdrawFailsWhenNeverConsented() {
        // given: 환자는 있지만 giveConsent()를 부르지 않음 → 동의한 적 없는 상태
        User patientUser = userRepository.save(
                new User("patient@test.com", "1234", "테스트환자", Role.PATIENT));
        Patient patient = patientRepository.save(
                new Patient(patientUser, AmputationSide.RIGHT_ARM));

        // when & then: "지금 상태로는 안 돼요" 예외 + 메시지에 "동의한 적"이 들어 있는가?
        assertThatThrownBy(() -> researchConsentService.withdraw(patient.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("동의한 적");
    }
}
