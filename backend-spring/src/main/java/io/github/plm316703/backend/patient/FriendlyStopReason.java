package io.github.plm316703.backend.patient;

import io.github.plm316703.backend.arm.ArmState;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 쉬운 정지 사유 — 환자·보호자 화면용 문장 변환기.
 *
 * 왜 서버에서 바꾸나 (11/20 결정):
 *  - "PACKET_LOSS: 3021ms 동안 메시지 없음" 같은 기술 문구는 관제 담당자용이다.
 *  - 화면 JS에서 바꾸면 개발자 도구로 원문이 보이므로, 아예 환자·보호자 API로 원문을 내보내지 않는다.
 *
 * 입력은 두 군데서 온다 (둘 다 "코드: 설명" 또는 한국어 문장 모양):
 *  - 로봇팔의 현재 정지 사유 (ArmSupervisor.stopReason)
 *  - 관제 동작 기록의 STOP 내용 (ControlEvent.detail)
 * 모르는 모양이 와도 원문을 흘리지 않고 일반 문장으로 대신한다.
 */
public final class FriendlyStopReason {

    private FriendlyStopReason() {}

    // 신호 품질 차단 내용에는 "issue=ELECTRODE_CONTACT" 처럼 원인이 들어 있다 (GateService가 만듦)
    private static final Pattern ISSUE = Pattern.compile("issue=([A-Z_]+)");

    /** 지금 팔 상태를 한 문장으로 (환자·보호자 화면 맨 위 안내문) */
    public static String nowMessage(ArmState state, String rawReason) {
        return switch (state) {
            case RUNNING -> "팔이 정상적으로 움직이고 있어요.";
            case IDLE -> "지금은 측정 세션이 없어요.";
            // 자동 재개가 없다는 설계(사람이 확인 후 재개)를 환자에게도 그대로 알려 준다
            case STOPPED -> reason(rawReason) + " 관제 담당자가 확인한 뒤 다시 움직여요.";
            case RESET_REQUIRED -> "장비 연결이 오래 끊겨 멈췄어요. 관제 담당자가 팔을 처음 자세로 되돌린 뒤 다시 시작해요.";
        };
    }

    /** 정지 사유 원문 → 쉬운 문장 (오늘 기록 목록에도 쓴다) */
    public static String reason(String raw) {
        if (raw == null || raw.isBlank()) return "안전을 위해 잠시 멈췄어요.";

        // 코드가 없는 한국어 사유 (세션 시작·초기화·철회·오래 끊김)
        if (raw.startsWith("세션 시작")) return "측정을 시작했어요. 신호가 안정되면 움직이기 시작해요.";
        if (raw.startsWith("초기화 완료")) return "팔을 처음 자세로 되돌렸어요.";
        if (raw.startsWith("연결 끊김")) return "장비 연결이 오래 끊겨 멈췄어요.";
        if (raw.contains("동의 철회")) return "연구 동의를 철회해서 로봇팔 사용을 멈췄어요.";

        int colon = raw.indexOf(':');
        String code = (colon < 0 ? raw : raw.substring(0, colon)).trim();
        return switch (code) {
            case "SIGNAL_QUALITY" -> signalReason(raw);
            case "PACKET_LOSS" -> "장비 연결이 잠시 끊겨 안전을 위해 멈췄어요.";
            case "STALE_COMMAND" -> "명령이 늦게 도착해서 안전을 위해 멈췄어요.";
            case "DECISION_MISMATCH" -> "움직임 판단을 다시 확인해야 해서 멈췄어요.";
            case "NOT_PARTICIPATING" -> "연구 참여 상태가 확인되지 않아 멈췄어요.";
            case "SUBJECT_MISMATCH" -> "측정 대상 정보가 맞지 않아 멈췄어요.";
            case "INVALID_FORMAT", "INVALID_FIELD", "UNSUPPORTED_VERSION", "OUT_OF_ORDER" ->
                    "장비에서 온 데이터에 문제가 있어 안전을 위해 멈췄어요.";
            default -> "안전을 위해 잠시 멈췄어요.";
        };
    }

    // 원인별로 환자가 직접 할 수 있는 일(가만히 있기, 힘 빼기)이 다르므로 나눠서 안내한다
    private static String signalReason(String raw) {
        Matcher m = ISSUE.matcher(raw);
        String issue = m.find() ? m.group(1) : "";
        return switch (issue) {
            case "ELECTRODE_CONTACT" -> "머리에 붙인 센서(전극)가 잘 닿지 않아 안전을 위해 멈췄어요.";
            case "LINE_NOISE" -> "주변 전기 잡음이 커서 안전을 위해 멈췄어요.";
            case "MUSCLE_ARTIFACT" -> "얼굴이나 목에 힘이 들어가 신호가 흐려져서 멈췄어요. 편하게 힘을 빼 주세요.";
            default -> "뇌파 신호가 깨끗하지 않아 안전을 위해 멈췄어요.";
        };
    }
}
