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

@SpringBootTest
@Transactional
public class PatientGuardianRepositoryTest {

    @Autowired UserRepository userRepository;   //회원 저장소
    @Autowired PatientRepository patientRepository; //환자 정보 저장소
    @Autowired PatientGuardianRepository  patientGuardianRepository;    //연결 담당

    @Test
    @DisplayName("환자 한 명에게 보호자 두 명 연결할 수 있다")
    void onePatientTwoGuardians(){

        //given : 준비
        //1) 환자 회원 1명 저장(역활 : PATIENT)
        User patientUser = userRepository.save(
                    new User("patient@test.com", "1234", "홍길동", Role.PATIENT));
        //2) 보호자 회원 2명 저장(역활 : GUARDIAN)
        //이메일은 유니크라서 서로 다름
        User guardian1 = userRepository.save(
                new User("guardian1@test.com", "1234", "홍엄마", Role.GUARDIAN));
        User guardian2 = userRepository.save(
                new User("guardian2@test.com", "1234", "김간병", Role.GUARDIAN));

        //3) 환자 회원으로 환자 정보 저장
        Patient patient = patientRepository.save(
                new Patient(patientUser, AmputationSide.LEFT_ARM));

        //=====when: 실행 ======
        //4. 보호자 1과 가족으로 연결
        patientGuardianRepository.save(
                new PatientGuardian(patient, guardian1, GuardianRelation.FAMILY));
        // 5) 보호자 2와 간병인으로 연결
        patientGuardianRepository.save(
                new PatientGuardian(patient, guardian2, GuardianRelation.CAREGIVER));


        //then : 확인
        //6. 연결 테이블에 2건이 저장되었는가?
        //count()는 테이블 전체 행 수를 세는 기본 메서드
        assertThat(patientGuardianRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("같은 환자-보호자 쌍을 두 번 연결하면 예외가 발생한다")
    void duplicatePairFails(){
        //given : 준비
        //1) 환자 회원 1명 저장
        User patientUser = userRepository.save(
                new User("fkfkfk315@naver.com", "1234","박민혁", Role.PATIENT));
        //2) 보호자 회원 1명 저장
        User guardian = userRepository.save(
                new User("guardian1@test.com", "1234", "홍엄마", Role.GUARDIAN));
        //3) 환자 회원으로 환자 정보 저장
        Patient patient = patientRepository.save(
                new Patient(patientUser, AmputationSide.LEFT_ARM));

        // 첫 번째 연결은 정상 저장
        patientGuardianRepository.saveAndFlush(
                new PatientGuardian(patient, guardian, GuardianRelation.FAMILY));

        // ===== when & then =====
        // 같은 쌍을 다시 저장하면 DB 제약조건 위반 예외가 나야 함
        assertThatThrownBy(() ->
                patientGuardianRepository.saveAndFlush(
                        new PatientGuardian(patient, guardian, GuardianRelation.CAREGIVER)))
                .isInstanceOf(DataIntegrityViolationException.class);

    }
}
