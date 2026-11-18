package io.github.plm316703.backend.record;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GateDecisionRepository extends JpaRepository<GateDecision, Long> {

    // 대시보드 첫 화면: 최근 판단 50개 (최신순)
    List<GateDecision> findTop50ByOrderByIdDesc();
}
