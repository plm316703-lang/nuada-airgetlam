package io.github.plm316703.backend.patient;

import io.github.plm316703.backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

//환자보호자연결 저장소
public interface PatientGuardianRepository extends JpaRepository<PatientGuardian, Long> {

    // [쿼리 메서드] 메서드 "이름"만 규칙대로 쓰면, Spring이 SQL을 자동으로 만들어 줌
    //   반환 타입이 long인 이유: 개수는 정수, count()와 같은 타입
    long countByPatient(Patient patient);

    //   반환 타입이 List인 이유: 결과가 여러 개일 수 있음 (보호자 여러 명)
    //   나중에 "보호자 화면에서 환자 목록 보기" 같은 기능에 그대로 쓰게 됨
    List<PatientGuardian> findAllByPatient(Patient patient);
    List<PatientGuardian> findAllByGuardian(User guardian);
}
