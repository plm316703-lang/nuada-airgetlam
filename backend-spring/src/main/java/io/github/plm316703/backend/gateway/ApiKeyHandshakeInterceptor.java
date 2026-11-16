package io.github.plm316703.backend.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;


/**
 * 문지기: WebSocket 연결이 만들어지기 직전에 공유 비밀키를 확인한다.
 * 헤더 "X-API-Key"의 값이 설정의 gateway.api-key와 같을 때만 연결을 허락한다.
 */
@Slf4j
@Component
public class ApiKeyHandshakeInterceptor implements HandshakeInterceptor {

    private static  final String HEADER = "X-API-Key";  //python이 키를 담아 보낼 헤더

    private final byte[] expectedKey;       //정답 키 (바이트로 보관)

    /**
     * @Value("${gateway.api-key}") = 설정 파일의 gateway.api-key 값을 생성자에 넣어 준다.
     * → 이제 이 값을 "실제로 읽는 코드"가 생겼으므로, 환경 변수가 없으면 Spring이 시작하지 못한다.
     */
    public ApiKeyHandshakeInterceptor(@Value("${gateway.api-key}") String apiKey){
        // 빈 값("")으로 설정된 경우도 막는다. 빈 키 = 잠금이 없는 것과 같음
        if(apiKey == null || apiKey.isBlank()){
            throw new IllegalStateException("gateway.api-key가 비어 있습니다. 환경 변수 GATEWAY_API_KEY를 설정하세요.");
        }
        this.expectedKey = apiKey.getBytes(StandardCharsets.UTF_8);
    }
    /** 연결 직전에 호출. true를 돌려주면 연결 허락, false면 거절. */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                  WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String givenKey = request.getHeaders().getFirst(HEADER);   //요청에 담겨 온 키 (없으면 null)
        // MessageDigest.isEqual = 두 값을 "항상 같은 시간에" 비교한다.
        // 보통의 equals()는 앞 글자부터 비교하다 다르면 바로 멈춰서,
        // 응답 시간 차이로 키를 한 글자씩 알아내는 공격(타이밍 공격)에 쓰일 수 있다.
        boolean ok = givenKey !=null
                && MessageDigest.isEqual(expectedKey, givenKey.getBytes(StandardCharsets.UTF_8));

        if(!ok){
            // ⚠ 키 값 자체는 절대 로그에 남기지 않는다. "보냈는지 안 보냈는지"만 남긴다
            log.warn("[인증 실패] 주소={}, 키 전달 여부={}", request.getRemoteAddress(), givenKey != null);
            response.setStatusCode(HttpStatus.UNAUTHORIZED);        // 401: 인증 안 됨
            return false;
        }
        log.info("[인증 성공] 주소= {}", request.getRemoteAddress());
        return true;
    }
    /** 연결이 만들어진 뒤 호출. 지금은 할 일이 없다. */
    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }

}
