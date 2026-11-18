package io.github.plm316703.backend.record;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceErrorRepository extends JpaRepository<DeviceError, Long> {

    // 이 세션에서 아직 해결되지 않은 같은 종류 오류 (있으면 새로 만들지 않고 횟수만 올림)
    Optional<DeviceError> findFirstBySessionIdAndErrorTypeAndResolvedAtIsNull(Long sessionId, ErrorType type);

    // 이 세션의 미해결 오류 전부 (수동 재개 때 해결 처리)
    List<DeviceError> findAllBySessionIdAndResolvedAtIsNull(Long sessionId);

    // 대시보드: 최근 오류 20개
    List<DeviceError> findTop20ByOrderByIdDesc();

    // RAG에 넘길 "오류 이력": 같은 종류로 이미 해결된 과거 오류 5개
    List<DeviceError> findTop5ByErrorTypeAndResolvedAtIsNotNullAndIdNotOrderByIdDesc(ErrorType type, Long excludeId);
}
