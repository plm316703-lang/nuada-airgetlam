package io.github.plm316703.backend.config;

import io.github.plm316703.backend.patient.*;
import io.github.plm316703.backend.user.Role;
import io.github.plm316703.backend.user.User;
import io.github.plm316703.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시연용 가짜 계정 만들기 (서버 시작 때 한 번, 이미 있으면 건너뜀).
 *  - 이메일은 전부 @nuada.test (가짜 도메인), 이름은 "테스트…" (프로젝트 규칙: 실제 개인정보 금지)
 *  - 비밀번호는 코드에 쓰지 않고 환경 변수 DEMO_PASSWORD 에서 읽어 BCrypt로 저장
 *  - DEMO_PASSWORD 가 비어 있으면 아무것도 만들지 않음
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoDataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final PatientGuardianRepository guardianRepository;
    private final ResearcherAssignmentRepository assignmentRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.demo.password:}")
    private String demoPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (demoPassword == null || demoPassword.isBlank()) {
            log.info("DEMO_PASSWORD 없음 → 시연 계정을 만들지 않습니다.");
            return;
        }
        if (userRepository.findByEmail("operator@nuada.test").isPresent()) {
            return;   // 이미 만들어져 있음
        }
        String hash = passwordEncoder.encode(demoPassword);

        userRepository.save(new User("admin@nuada.test", hash, "테스트관리자", Role.SYSTEM_ADMIN));
        userRepository.save(new User("pi@nuada.test", hash, "테스트책임자", Role.PRINCIPAL_INVESTIGATOR));
        userRepository.save(new User("operator@nuada.test", hash, "테스트관제", Role.OPERATOR));
        User researcher = userRepository.save(new User("researcher@nuada.test", hash, "테스트연구자", Role.RESEARCHER));

        // 환자 1: 등록 + 동의 → 참여 중 (시연 주인공)
        User u1 = userRepository.save(new User("patient@nuada.test", hash, "테스트환자", Role.PATIENT));
        Patient p1 = new Patient(u1, AmputationSide.LEFT_ARM);
        p1.enroll();
        p1.giveConsent();
        patientRepository.save(p1);
        assignmentRepository.save(new ResearcherAssignment(p1, researcher));

        // 환자 2: 등록만, 동의 안 함 → 세션 시작 불가 (관문 규칙 ① 시연용)
        User u2 = userRepository.save(new User("patient2@nuada.test", hash, "테스트환자2", Role.PATIENT));
        Patient p2 = new Patient(u2, AmputationSide.RIGHT_ARM);
        p2.enroll();
        patientRepository.save(p2);

        // 환자 3: 미등록 → 연구책임자가 등록하는 시연용
        User u3 = userRepository.save(new User("patient3@nuada.test", hash, "테스트환자3", Role.PATIENT));
        patientRepository.save(new Patient(u3, AmputationSide.BOTH_ARMS));

        // 보호자: 환자 1과 연결
        User g = userRepository.save(new User("guardian@nuada.test", hash, "테스트보호자", Role.GUARDIAN));
        guardianRepository.save(new PatientGuardian(p1, g, GuardianRelation.FAMILY));

        log.info("시연 계정 생성 완료 (@nuada.test 8개, 비밀번호 = DEMO_PASSWORD)");
    }
}
