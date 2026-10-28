package io.github.plm316703.backend.patient;

import io.github.plm316703.backend.user.Role;
import io.github.plm316703.backend.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PatientTest  {      //@SpringBootTest 없읍 순수 자바 테스트(Unit Test)

    @Test
    @DisplayName("연구에 동의하면 동의 여부가 true가 되고, 동의 시간이 기록된다")
    void giveConsent(){
     //---given : 준비
        //환자를 만들려면 회원이 필요
        User user = new User("test@test.com", "1234", "홍길동", Role.PATIENT);
        //그 회원으로 환장정보 만들기(DB 저장은 안함, 그냥 객체만 )
        Patient patient = new Patient(user, AmputationSide.LEFT_ARM);

        // when 1: 동의
        patient.giveConsent();

    // then: 확인
        //동의 여부가 true인가?
    assertThat(patient.isResearchConsent()).isTrue();
    //"동의 시간이 비어있지 않은가?
    assertThat(patient.getConsentedAt()).isNotNull();
    }

    @Test
    @DisplayName("연구에 동의하면 동의 시간이 기록되고, 철회하면 철회 시간이 기록된다")
    void withdrawConsent(){
        //given
        //환자를 만들려면 회원이 필요
        User user = new User("test@test.com", "1234", "홍길동", Role.PATIENT);
        //그 회원으로 환장정보 만들기(DB 저장은 안함, 그냥 객체만 )
        Patient patient = new Patient(user, AmputationSide.LEFT_ARM);

        // when 1: 동의
        patient.giveConsent();
        //when 2: 철회하기
        patient.withdrawConsent();

        //then 확인
        assertThat(patient.isResearchConsent()).isFalse();
        assertThat(patient.getConsentWithdrawnAt()).isNotNull();

    }
}
