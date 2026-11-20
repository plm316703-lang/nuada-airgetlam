package io.github.plm316703.backend.record;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ControlEventRepository extends JpaRepository<ControlEvent, Long> {

    // 대시보드: 최근 관제 동작 20개 (누가 했는지 이름까지 한 번에)
    @EntityGraph(attributePaths = "actor")
    List<ControlEvent> findTop20ByOrderByIdDesc();

    /**
     * 한 환자의 세션에서 일어난 특정 종류의 관제 동작 (환자·보호자 화면 "오늘 기록").
     * control_events는 세션 번호만 갖고 있어서, 그 환자의 세션 번호 목록 안에 있는 것만 고른다
     * → 다른 환자의 기록이 섞이지 않게 하는 것이 핵심.
     */
    @Query("""
            select c from ControlEvent c
            where c.type = :type and c.occurredAt >= :from
              and c.sessionId in (select s.id from MeasurementSession s where s.patient.id = :patientId)
            order by c.occurredAt desc""")
    List<ControlEvent> findPatientEventsSince(@Param("patientId") Long patientId,
                                              @Param("type") ControlEvent.Type type,
                                              @Param("from") LocalDateTime from);
}
