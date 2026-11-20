package io.github.plm316703.backend.record;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface LoginHistoryRepository extends JpaRepository<LoginHistory, Long> {

    // 관리자 화면: 최근 100건
    List<LoginHistory> findTop100ByOrderByIdDesc();

    // 관리자 화면 "실패만 보기": 실패·잠김만 최근 100건
    List<LoginHistory> findTop100ByEventInOrderByIdDesc(Collection<LoginHistory.Event> events);
}
