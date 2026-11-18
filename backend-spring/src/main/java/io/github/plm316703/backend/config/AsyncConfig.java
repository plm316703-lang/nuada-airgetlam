package io.github.plm316703.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @EnableScheduling: 워치독(@Scheduled, 0.5초마다) 켜기
 * @EnableAsync     : RAG 요청(@Async)을 별도 스레드에서 → 관문이 Gemini 응답을 기다리지 않음
 */
@Configuration
@EnableScheduling
@EnableAsync
public class AsyncConfig {
}
