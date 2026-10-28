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

/**
 * Patient 저장/조회 테스트
 *
 * @SpringBootTest : 실제 애플리케이션과 같은 환경(설정, DB연결)을 띄워서 테스트
 * @Transactional : 테스트가 끝나면 저장했던 데이터를 모두 되둘림(롤백)
 *                      -> 테스트를 몇 번 돌려도 DB가 지저분해지지 않음
 */
@SpringBootTest
@Transactional
class PatientRepositoryTest {

    @Autowired UserRepository userRepository;   //회원 저장소
    @Autowired PatientRepository patientRepository; //환자 정보 저장소
    @Autowired EntityManager em;    //JPA의 핵심 관리자(캐시 비우기에 사용)

    @Test
    @DisplayName("환자 회원을 저장하면 환자 정보와 1:1로 연결되고, 익명 코드가 자동 생성")
    void saveAndFindPatient() {
        //  given : 테스트에 필요한 준비
        User user = new User("patient1@test.com", "1234", "홍길동", Role.PATIENT);
        userRepository.save(user);      //회원 먼저 저장(FK가 가리킬 대상이 있어야함)

        Patient patient = new Patient(user, AmputationSide.LEFT_ARM);
        patientRepository.save(patient);

        // 지금까지 저장한 내용을 DB에 실제로 반영(flush)하고,
        // JPA가 메모리에 들고 있던 객체를 비움(clear)
        // → 이렇게 해야 아래 조회가 "메모리"가 아니라 "진짜 DB"에서 읽어옴
        em.flush();
        em.clear();

        // when : 테스트하려는 동작
        Patient found = patientRepository.findById(patient.getId()).orElseThrow();

        // ===== then: 결과 검증 =====
        assertThat(found.getAmputationSide()).isEqualTo(AmputationSide.LEFT_ARM);
        assertThat(found.isResearchConsent()).isFalse();          // 처음엔 동의 안 한 상태
        assertThat(found.getResearchCode()).startsWith("P-");     // 익명 코드 자동 생성 확인
        assertThat(found.getUser().getEmail()).isEqualTo("patient1@test.com"); // 1:1 연결 확인
    }
    @Test
    @DisplayName("연구에 동의하면 동의 시간이 기록되고, 철회하면 철회 시간이 기록된다")
    void giveAndWithdrawConsent() {
        // given
        User user = userRepository.save(new User("patient2@test.com", "1234", "김철수", Role.PATIENT));
        Patient patient = patientRepository.save(new Patient(user, AmputationSide.RIGHT_ARM));

        // when 1: 동의
        patient.giveConsent();

        // then 1
        assertThat(patient.isResearchConsent()).isTrue();
        assertThat(patient.getConsentedAt()).isNotNull();

        // when 2: 철회
        patient.withdrawConsent();

        // then 2
        assertThat(patient.isResearchConsent()).isFalse();
        assertThat(patient.getConsentWithdrawnAt()).isNotNull();
    }
}
