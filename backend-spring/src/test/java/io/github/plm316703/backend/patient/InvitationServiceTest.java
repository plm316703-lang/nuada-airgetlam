package io.github.plm316703.backend.patient;

import io.github.plm316703.backend.auth.LoginService;
import io.github.plm316703.backend.user.Role;
import io.github.plm316703.backend.user.User;
import io.github.plm316703.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 초대 서비스(InvitationService) 검사 — 환자 등록·초대 링크·보호자 초대.
 * @SpringBootTest + @Transactional: 진짜 DB로 검사하고 끝나면 되돌린다.
 */
@SpringBootTest
@Transactional
class InvitationServiceTest {

    private static final String NEW_PASSWORD = "silver arm nuada 01";

    @Autowired InvitationService invitationService;
    @Autowired LoginService loginService;
    @Autowired UserRepository userRepository;
    @Autowired PatientRepository patientRepository;

    User pi;

    @BeforeEach
    void setUp() {
        pi = userRepository.save(new User("invite-pi@nuada.test", "x", "테스트책임자", Role.PRINCIPAL_INVESTIGATOR));
    }

    @Test
    @DisplayName("등록한 환자는 링크로 비밀번호를 정하기 전에는 로그인할 수 없고, 정한 뒤에는 로그인된다")
    void registerThenAccept() {
        var invited = invitationService.registerPatient("테스트초대환자", "Invite-Patient@nuada.test",
                AmputationSide.LEFT_ARM, pi, "127.0.0.1");

        assertThat(invited.user().getEmail()).isEqualTo("invite-patient@nuada.test");   // 소문자로 저장
        assertThat(patientRepository.findByUser(invited.user())).isPresent();          // 환자 정보도 함께 생김
        assertThat(invitationService.isPending(invited.user())).isTrue();
        assertThat(loginService.login("invite-patient@nuada.test", NEW_PASSWORD, "127.0.0.1").ok()).isFalse();

        invitationService.accept(invited.token(), NEW_PASSWORD, "127.0.0.1");

        assertThat(invitationService.isPending(invited.user())).isFalse();
        assertThat(loginService.login("invite-patient@nuada.test", NEW_PASSWORD, "127.0.0.1").ok()).isTrue();
    }

    @Test
    @DisplayName("초대 링크는 한 번만 쓸 수 있다")
    void tokenIsOneTime() {
        var invited = invitationService.registerPatient("테스트일회용", "one-time@nuada.test",
                AmputationSide.RIGHT_ARM, pi, "127.0.0.1");
        invitationService.accept(invited.token(), NEW_PASSWORD, "127.0.0.1");

        assertThatThrownBy(() -> invitationService.accept(invited.token(), "another password 99", "127.0.0.1"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("이미 사용한");
    }

    @Test
    @DisplayName("다시 초대하면 이전 링크는 쓸 수 없고 새 링크만 쓸 수 있다")
    void reinviteRevokesOld() {
        var first = invitationService.registerPatient("테스트재초대", "reinvite@nuada.test",
                AmputationSide.BOTH_ARMS, pi, "127.0.0.1");
        var second = invitationService.reinvite(first.user().getId(), pi, "127.0.0.1");

        assertThatThrownBy(() -> invitationService.preview(first.token()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("가장 최근");
        assertThat(invitationService.preview(second.token()).email()).isEqualTo("reinvite@nuada.test");
    }

    @Test
    @DisplayName("보호자는 신분 확인 체크 없이는 초대할 수 없다")
    void guardianNeedsIdentityCheck() {
        var patient = invitationService.registerPatient("테스트보호대상", "ward@nuada.test",
                AmputationSide.LEFT_ARM, pi, "127.0.0.1");
        Long patientId = patientRepository.findByUser(patient.user()).orElseThrow().getId();

        assertThatThrownBy(() -> invitationService.inviteGuardian(patientId, "테스트보호자", "g@nuada.test",
                GuardianRelation.FAMILY, false, pi, "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("신분");

        var g = invitationService.inviteGuardian(patientId, "테스트보호자", "g@nuada.test",
                GuardianRelation.FAMILY, true, pi, "127.0.0.1");
        assertThat(g.user().getRole()).isEqualTo(Role.GUARDIAN);
        assertThat(g.token()).isNotBlank();
    }

    @Test
    @DisplayName("없는 링크는 아무 정보 없이 거절한다")
    void unknownToken() {
        assertThatThrownBy(() -> invitationService.preview("no-such-token"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("초대 링크가 올바르지 않습니다.");
    }
}
