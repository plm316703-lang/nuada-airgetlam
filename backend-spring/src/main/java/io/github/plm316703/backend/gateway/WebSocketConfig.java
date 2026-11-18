package io.github.plm316703.backend.gateway;

import io.github.plm316703.backend.dashboard.DashboardHub;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 주소와 담당자를 연결하는 설정.
 *  - /ws/classifier : Python 판별기 → 관문 (공유 API 키 문지기)
 *  - /ws/dashboard  : 관문 → 관제 화면 (로그인 쿠키 JWT, SecurityConfig에서 역할 확인)
 */
@Configuration  //설정 클래스라고 Spring에게 알림
@EnableWebSocket        //WebSocket 기능 켜기
@RequiredArgsConstructor       // final 필드를 받는 생성자 자동 생성 → 생성자 주입 (Lombok)
public class WebSocketConfig implements WebSocketConfigurer {

    private final ClassifierWebSocketHandler classifierHandler; //@Component로 만들어진 담당자를 주입
    private final ApiKeyHandshakeInterceptor apiKeyInterceptor;
    private final DashboardHub dashboardHub;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry){
        registry.addHandler(classifierHandler, "/ws/classifier")
                .addInterceptors(apiKeyInterceptor);   // 이 주소로 오는 연결은 문지기를 먼저 거친다
        // 같은 출처(같은 주소의 화면)에서 온 연결만 허용 (기본값). 로그인 확인은 보안 필터가 먼저 함
        registry.addHandler(dashboardHub, "/ws/dashboard");
    }
}
