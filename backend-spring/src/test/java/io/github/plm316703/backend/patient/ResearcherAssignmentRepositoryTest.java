package io.github.plm316703.backend.patient;


import io.github.plm316703.backend.user.Role;
import io.github.plm316703.backend.user.User;
import io.github.plm316703.backend.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 연구자-환자 배정(ResearcherAssignment) 테스트
 * PatientGuardianRepositoryTest와 같은 구조
 */
@SpringBootTest
@Transactional
public class ResearcherAssignmentRepositoryTest {

    @Autowired
    UserRepository userRepository;
    @Autowired
    PatientRepository patientRepository;
    @Autowired
    ResearcherAssignmentRepository researcherAssignmentRepository;

    @Test
    @DisplayName("연구자에게 환자를 배정하면 배정 시간이 자동으로 기록된다.")
    void assignPatientToResearcher() {
        //====== given: 환자와 연구자 준비 =====
        User patientUser = userRepository.save(
                new User("patient@test.com", "1234", "테스트환자", Role.PATIENT));
        User researcher = userRepository.save(
                new User("researcher@test.com", "1234", "테스트연구자", Role.RESEARCHER));
        Patient patient = patientRepository.save(
                new Patient(patientUser, AmputationSide.RIGHT_ARM));


        //======== when : 배정 ======
        ResearcherAssignment assignment = researcherAssignmentRepository.saveAndFlush(
                new ResearcherAssignment(patient, researcher));

        //====== then : 확인 =====
        assertThat(assignment.getAssignedAt()).isNotNull();
        assertThat(assignment.getResearcher().getEmail()).isEqualTo("researcher@test.com");
    }


    @Test
    @DisplayName("같은 연구자에게 같은 환자를 두 번 배정하면 예외가 발생한다")
    void duplicateAssignmentFails() {
        //====given
        User patientUser = userRepository.save(
                new User("patient@test.com", "1234", "테스트환자", Role.PATIENT));
        User researcher = userRepository.save(
                new User("researcher@test.com", "1234", "테스트연구자", Role.RESEARCHER));
        Patient patient = patientRepository.save(
                new Patient(patientUser, AmputationSide.RIGHT_ARM));
        //첫 번째 배정은 정상
        researcherAssignmentRepository.saveAndFlush(new ResearcherAssignment(patient, researcher));

        // ===== when & then =====
        // 같은 (환자, 연구자) 쌍을 다시 배정하면 조합 unique 위반
        assertThatThrownBy(() ->
                researcherAssignmentRepository.saveAndFlush(new ResearcherAssignment(patient, researcher)))
                .isInstanceOf(DataIntegrityViolationException.class);

    }
}
