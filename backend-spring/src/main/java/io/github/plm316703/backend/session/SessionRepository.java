package io.github.plm316703.backend.session;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SessionRepository extends JpaRepository<MeasurementSession, Long> {

    // 지금 열려 있는(끝나지 않은) 세션. 동시에 하나만 허용한다.
    // @EntityGraph: 환자 정보도 한 번에 가져와서, 트랜잭션 밖에서 읽어도 오류가 나지 않게 함
    @EntityGraph(attributePaths = {"patient", "patient.user"})
    Optional<MeasurementSession> findFirstByEndedAtIsNullOrderByStartedAtDesc();
}
