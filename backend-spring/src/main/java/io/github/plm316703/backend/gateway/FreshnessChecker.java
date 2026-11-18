package io.github.plm316703.backend.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * 관문 규칙 ④: 오래된 명령인가?
 * 판별기가 만든 시각(timestamp)과 지금 시각의 차이가 기준(기본 2초)을 넘으면 STALE_COMMAND.
 * → 늦게 도착한 명령은 그 사이 상황이 바뀌었을 수 있으므로 실행하지 않는다.
 *
 * timestamp에는 시간대(+09:00)가 반드시 있어야 한다.
 * 시간대가 없으면 Docker 컨테이너(UTC)와 내 PC(한국 시간)의 9시간 차이를 구분할 수 없기 때문.
 */
@Component
public class FreshnessChecker {

    private static final long FUTURE_TOLERANCE_MS = 1000;   // 시계 오차 허용 (미래 시각 1초까지)

    private final long staleMs;

    public FreshnessChecker(@Value("${gateway.stale-command-ms}") long staleMs) {
        if (staleMs <= 0) {
            throw new IllegalStateException("gateway.stale-command-ms 는 0보다 커야 함: " + staleMs);
        }
        this.staleMs = staleMs;
    }

    public Optional<MessageValidator.Rejection> check(String timestamp, Instant now) {
        Instant sentAt;
        try {
            sentAt = OffsetDateTime.parse(timestamp).toInstant();   // 시간대가 없으면 여기서 실패
        } catch (DateTimeParseException e) {
            return Optional.of(new MessageValidator.Rejection(RejectReason.INVALID_FIELD,
                    "timestamp 형식 오류(시간대 필요): " + timestamp));
        }
        long ageMs = Duration.between(sentAt, now).toMillis();
        if (ageMs > staleMs) {
            return Optional.of(new MessageValidator.Rejection(RejectReason.STALE_COMMAND,
                    "age=" + ageMs + "ms limit=" + staleMs + "ms"));
        }
        if (ageMs < -FUTURE_TOLERANCE_MS) {
            return Optional.of(new MessageValidator.Rejection(RejectReason.INVALID_FIELD,
                    "미래 시각 timestamp (시계 확인 필요) age=" + ageMs + "ms"));
        }
        return Optional.empty();
    }

    public long staleMs() { return staleMs; }
}
