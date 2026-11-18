package io.github.plm316703.backend.rag;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

/**
 * Python RAG 서버(FastAPI, 기본 http://localhost:8000)에 점검 안내를 요청하는 전화기.
 * RAG는 "안내만" 한다. 이 응답으로 팔을 재개하거나 상태를 바꾸는 코드는 어디에도 없다.
 */
@Component
public class RagClient {

    private final RestClient restClient;

    public RagClient(@Value("${rag.base-url}") String baseUrl,
                     @Value("${rag.timeout-seconds}") long timeoutSeconds) {
        // HTTP/1.1 고정: 자바 기본값(HTTP/2)은 http:// 주소에 업그레이드를 시도하는데,
        // uvicorn(FastAPI)은 이를 지원하지 않아 요청 본문이 사라진다 (422 "body missing")
        HttpClient http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));   // Gemini 응답은 몇 초 걸릴 수 있음
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    /** RAG 서버로 보내는 질문 */
    public record GuideRequest(String errorType, String detail, Double signalQuality, boolean injected,
                               List<HistoryItem> history) {}

    /** 같은 종류로 과거에 해결된 오류 (오류 이력) */
    public record HistoryItem(Long id, String occurredAt, String resolvedAt, int occurrenceCount, String detail) {}

    /** RAG 서버의 답: 안내문 + 근거 + 근거가 충분한지 */
    public record GuideResponse(String answer, List<Source> sources, boolean evidenceSufficient, String model) {}

    public record Source(String doc, String section, Double score) {}

    public GuideResponse guide(GuideRequest request) {
        return restClient.post()
                .uri("/guide")
                .body(request)
                .retrieve()
                .body(GuideResponse.class);
    }
}
