package io.github.plm316703.backend.rag;

import io.github.plm316703.backend.dashboard.DashboardHub;
import io.github.plm316703.backend.dashboard.Views;
import io.github.plm316703.backend.record.DeviceError;
import io.github.plm316703.backend.record.DeviceErrorRepository;
import io.github.plm316703.backend.record.DeviceErrorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * 새 장비 오류가 저장되면(커밋 뒤) RAG 서버에 점검 안내를 요청하고,
 * 받은 안내를 오류 기록에 저장한 뒤 관제 화면에 보낸다.
 *
 * @Async: 별도 스레드에서 실행 → Gemini가 몇 초 걸려도 관문(판별 메시지 처리)은 기다리지 않는다.
 * RAG가 실패해도 팔은 이미 정지 상태이고, 안전에는 영향이 없다 (안내가 없을 뿐).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private final RagClient ragClient;
    private final DeviceErrorRepository errorRepository;
    private final DeviceErrorService errorService;
    private final DashboardHub hub;
    private final ObjectMapper objectMapper;

    // fallbackExecution = true: 트랜잭션 밖에서 낸 소식도 받는다
    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onErrorOpened(DeviceErrorService.DeviceErrorOpenedEvent event) {
        DeviceError error = errorRepository.findById(event.errorId()).orElse(null);
        if (error == null) return;

        // 오류 이력: 같은 종류로 이미 해결된 과거 오류 최대 5개
        List<RagClient.HistoryItem> history = errorRepository
                .findTop5ByErrorTypeAndResolvedAtIsNotNullAndIdNotOrderByIdDesc(error.getErrorType(), error.getId())
                .stream()
                .map(h -> new RagClient.HistoryItem(h.getId(), String.valueOf(h.getOccurredAt()),
                        String.valueOf(h.getResolvedAt()), h.getOccurrenceCount(), h.getDetail()))
                .toList();

        var request = new RagClient.GuideRequest(error.getErrorType().name(), error.getDetail(),
                error.getSignalQuality(), error.isInjected(), history);

        DeviceError updated;
        try {
            RagClient.GuideResponse res = ragClient.guide(request);
            String sourcesJson = objectMapper.writeValueAsString(res.sources());
            updated = errorService.saveRagResult(error.getId(), res.answer(), sourcesJson, res.evidenceSufficient());
            log.info("RAG 안내 저장 errorId={} type={} 근거충분={}", error.getId(), error.getErrorType(),
                    res.evidenceSufficient());
        } catch (RestClientResponseException e) {
            // RAG 서버가 답은 했지만 실패 (예: 503 Gemini 키 없음)
            String why = "RAG 서버 오류 " + e.getStatusCode().value() + ": " + e.getResponseBodyAsString();
            log.warn("RAG 실패 errorId={} {}", error.getId(), why);
            updated = errorService.saveRagFailure(error.getId(), why);
        } catch (RestClientException e) {
            // RAG 서버가 꺼져 있거나 시간 초과
            log.warn("RAG 연결 실패 errorId={} {}", error.getId(), e.getMessage());
            updated = errorService.saveRagFailure(error.getId(), "RAG 서버에 연결할 수 없습니다: " + e.getMessage());
        }
        hub.broadcast("error", Views.ErrorView.of(updated));
    }
}
