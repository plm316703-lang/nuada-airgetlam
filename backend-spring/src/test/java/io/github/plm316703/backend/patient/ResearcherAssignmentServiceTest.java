package io.github.plm316703.backend.patient;


import io.github.plm316703.backend.user.Role;
import io.github.plm316703.backend.user.User;
import io.github.plm316703.backend.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 연구자배정 서비스(주방장) 검사
 * 이번에는 창고 관리인이 아니라 "주방장의 규칙"을 검사한다.
 */

@SpringBootTest
@Transactional
public class ResearcherAssignmentServiceTest {

    @Autowired ResearcherAssignmentService researcherAssignmentService; //검사 대상 : 주방장
    @Autowired ResearcherAssignmentRepository researcherAssignmentRepository; //결과 확인용
    @Autowired UserRepository userRepository; //준비물 만들기용
    @Autowired PatientRepository patientRepository; //준비물 만들기용

    @Test
    @DisplayName("연구에 동의한 환자는 연구자에게 배정할 수 있다")
    void assignSuccess(){
        // given: 연구에 "동의한" 환자와 연구자 준비
        User patientUser = userRepository.save(
                new User("patient@test.com", "1234", "테스트환자", Role.PATIENT));
        User researcher = userRepository.save(
                new User("researcher@test.com", "1234", "테스트연구자", Role.RESEARCHER));
        Patient patient = patientRepository.save(
                new Patient(patientUser, AmputationSide.LEFT_ARM));
        patient.giveConsent();   // ← 핵심: 동의시키기

        // when: 주방장에게 배정 주문 (객체가 아니라 "번호(id)"로 주문함)
        Long assignmentId = researcherAssignmentService.assign(patient.getId(), researcher.getId());

        // then: 배정 번호가 생겼고, 창고에도 실제로 1건 저장되었는가?
        assertThat(assignmentId).isNotNull();
        assertThat(researcherAssignmentRepository.existsByPatientAndResearcher(patient, researcher)).isTrue();
    }

    @Test
    @DisplayName("연구에 동의하지 않은 환자는 배정할 수 없다")
    void assignFailsWithoutConsent() {
        // given: 동의하지 "않은" 환자 (giveConsent를 부르지 않음)
        User patientUser = userRepository.save(
                new User("patient@test.com", "1234", "테스트환자", Role.PATIENT));
        User researcher = userRepository.save(
                new User("researcher@test.com", "1234", "테스트연구자", Role.RESEARCHER));
        Patient patient = patientRepository.save(
                new Patient(patientUser, AmputationSide.LEFT_ARM));

        // when & then: 주문하면 "지금은 안 돼요" 예외가 나야 함
        //   hasMessageContaining: 예외 메시지에 이 글자가 들어 있는가?
        //   → "왜 거절됐는지"까지 정확히 확인
        assertThatThrownBy(() ->
                researcherAssignmentService.assign(patient.getId(), researcher.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("동의");
    }
    @Test
    @DisplayName("연구자 역할이 아닌 회원에게는 배정할 수 없다")
    void assignFailsWhenNotResearcher() {
        // given: 동의한 환자 + "보호자" 역할의 회원 (연구자가 아님!)
        User patientUser = userRepository.save(
                new User("patient@test.com", "1234", "테스트환자", Role.PATIENT));
        User guardian = userRepository.save(
                new User("guardian@test.com", "1234", "테스트보호자", Role.GUARDIAN));
        Patient patient = patientRepository.save(
                new Patient(patientUser, AmputationSide.LEFT_ARM));
        patient.giveConsent();

        // when & then: "잘못된 주문이에요" 예외가 나야 함
        assertThatThrownBy(() ->
                researcherAssignmentService.assign(patient.getId(), guardian.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("연구자");
    }
}
