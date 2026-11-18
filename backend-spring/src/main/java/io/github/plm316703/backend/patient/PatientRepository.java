package io.github.plm316703.backend.patient;

import io.github.plm316703.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PatientRepository extends JpaRepository<Patient, Long> {

    // 로그인한 환자 회원 → 환자 정보 찾기 (환자 화면에서 사용)
    Optional<Patient> findByUser(User user);
}
