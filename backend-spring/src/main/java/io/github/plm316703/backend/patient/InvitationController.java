package io.github.plm316703.backend.patient;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 초대 링크를 받은 본인용 API (로그인 전이라 누구나 부를 수 있음 → SecurityConfig에서 permitAll).
 * 링크 비밀값이 곧 출입증이다. 비밀값은 주소(GET 경로)가 아니라 요청 본문(POST)으로 받는다:
 * 주소에 넣으면 서버 접속 기록·브라우저 기록에 남을 수 있기 때문.
 */
@RestController
@RequiredArgsConstructor
public class InvitationController {

    private final InvitationService invitationService;

    public record TokenRequest(@NotBlank String token) {}

    public record AcceptRequest(@NotBlank String token, @NotBlank String password) {}

    @PostMapping("/api/invitations/preview")
    public InvitationService.Preview preview(@Valid @RequestBody TokenRequest req) {
        return invitationService.preview(req.token());
    }

    /** 가입 완료 → 로그인 화면에서 이메일을 채워 주려고 이메일만 돌려준다 (자동 로그인은 하지 않음) */
    @PostMapping("/api/invitations/accept")
    public Map<String, String> accept(@Valid @RequestBody AcceptRequest req, HttpServletRequest http) {
        return Map.of("email", invitationService.accept(req.token(), req.password(), http.getRemoteAddr()).getEmail());
    }
}
