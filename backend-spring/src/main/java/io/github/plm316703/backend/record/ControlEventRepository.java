package io.github.plm316703.backend.record;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ControlEventRepository extends JpaRepository<ControlEvent, Long> {

    // 대시보드: 최근 관제 동작 20개 (누가 했는지 이름까지 한 번에)
    @EntityGraph(attributePaths = "actor")
    List<ControlEvent> findTop20ByOrderByIdDesc();
}
