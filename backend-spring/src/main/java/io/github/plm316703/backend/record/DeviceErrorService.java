package io.github.plm316703.backend.record;

import io.github.plm316703.backend.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 장비 오류 기록 담당 (주방장).
 *  - 같은 세션에서 해결되지 않은 같은 종류 오류가 있으면 횟수만 올린다 (1초마다 새 줄이 쌓이지 않게)
 *  - 새 오류가 생기면 "새 오류" 소식을 낸다 → RAG 서비스가 받아서 점검 안내를 요청
 */
@Service
@RequiredArgsConstructor
public class DeviceErrorService {

    private final DeviceErrorRepository repository;
    private final ApplicationEventPublisher events;

    /** 새 오류가 저장됨 (커밋 뒤에 RAG가 받음) */
    public record DeviceErrorOpenedEvent(Long errorId) {}

    public record Reported(DeviceError error, boolean isNew) {}

    @Transactional
    public Reported report(Long sessionId, ErrorType type, String detail, Double quality, boolean injected) {
        var open = repository.findFirstBySessionIdAndErrorTypeAndResolvedAtIsNull(sessionId, type);
        if (open.isPresent()) {
            open.get().seenAgain(quality);   // 변경 감지로 UPDATE
            return new Reported(open.get(), false);
        }
        DeviceError saved = repository.save(new DeviceError(sessionId, type, detail, quality, injected));
        events.publishEvent(new DeviceErrorOpenedEvent(saved.getId()));
        return new Reported(saved, true);
    }

    /** 수동 재개 성공 → 이 세션의 미해결 오류를 전부 "해결됨"으로 */
    @Transactional
    public List<DeviceError> resolveOpen(Long sessionId, User by) {
        List<DeviceError> open = repository.findAllBySessionIdAndResolvedAtIsNull(sessionId);
        open.forEach(e -> e.resolve(by));
        return open;
    }

    @Transactional
    public DeviceError saveRagResult(Long errorId, String answer, String sourcesJson, boolean sufficient) {
        DeviceError e = repository.findById(errorId).orElseThrow();
        e.ragDone(answer, sourcesJson, sufficient);
        return e;
    }

    @Transactional
    public DeviceError saveRagFailure(Long errorId, String why) {
        DeviceError e = repository.findById(errorId).orElseThrow();
        e.ragFailed(why);
        return e;
    }
}
