package io.github.plm316703.backend.gateway;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Python 판별기가 WebSocket으로 보내는 메시지 한 개를 담는 그릇(DTO). 규격 버전 2.
 * - record: 값을 담기만 하고 바꾸지 않는 클래스를 짧게 쓰는 문법 (Java 16+)
 *           생성자, getter(version()), equals, toString을 자동으로 만들어 줌
 * - 숫자 칸은 Integer/Long/Double(감싼 타입)을 써서,
 *   JSON에 칸이 빠지면 0이 아니라 null이 되게 함 → 12-5b에서 빠진 칸을 잡기 위해
 *
 * 버전 2에서 바뀐 점 (docs/message-spec.md):
 *  - signal(신호 품질) 칸 추가 (필수)  → 관문 규칙 ② 신호 품질
 *  - timestamp에 시간대(+09:00 등) 필수 → 관문 규칙 ④ 오래된 명령 (컨테이너마다 시계 기준이 달라도 안전)
 *  - scenario 칸 추가 (선택) → 오류를 일부러 주입한 시연 메시지 표시
 */
public record ClassifierMessage (
    Integer version,//메시지 규격버전 (지금은 2)
    String type,    // 메시지 종류 ("classification")
    Long seq,   // 순서 번호 0, 1, 2... (12-5c에서 순서 검사에 사용)
    String timestamp,   // Python에서 만든 시각 (ISO-8601 + 시간대, 예: 2026-11-17T20:14:34.604+09:00)
    Integer subject,     // 피험자 번호 (시연은 3번)
    String intent,       // 판별 결과: 어느 손을 상상했는지 (예: "left_hand")
    Double confidence,   // 판별기의 확신도 0.0 ~ 1.0
    Double threshold,    // 이 확신도 이상이어야 MOVE로 보는 기준값 (참고용, 관문은 믿지 않음)
    String decision,     // Python이 내린 판단: "MOVE" 또는 "ABSTAIN"
    Signal signal,       // 신호 품질 (버전 2 필수)
    String scenario,     // 오류 주입 시나리오 이름 (정상 재생이면 null)

    // Python은 true_label(밑줄), Java는 trueLabel(낙타 등) 이름 규칙이 달라서
    // "JSON에서는 true_label이라는 이름이야"라고 Jackson에게 알려 줌
    @JsonProperty("true_label")
    String trueLabel
){
    /**
     * 신호 품질 묶음.
     * quality: 0~1 (1 = 모든 채널 정상), issue: 판별기가 추정한 오류 종류 (없으면 null)
     */
    public record Signal(
            Double quality,
            String issue,
            @JsonProperty("bad_channels") List<String> badChannels
    ) {}
}
