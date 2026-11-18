package io.github.plm316703.backend.record;

/**
 * 장비·통신 오류의 종류. RAG 매뉴얼 파일 이름(ai-server-python/rag/manuals/*.md)과 같게 맞춘다.
 * 정책 차단(동의 안 함, 세션 없음 등)은 장비 오류가 아니므로 여기에 넣지 않는다.
 */
public enum ErrorType {
    ELECTRODE_CONTACT,   // 전극 접촉 불량 (평탄·튀는 채널)
    LINE_NOISE,          // 전원 잡음 (60Hz)
    MUSCLE_ARTIFACT,     // 근육 움직임 잡음 (고주파)
    SIGNAL_QUALITY_LOW,  // 원인을 특정하지 못한 품질 저하
    PACKET_LOSS,         // 연결 끊김 (워치독)
    STALE_COMMAND,       // 오래된 명령 (지연)
    DECISION_MISMATCH,   // 판별기와 관문의 판단 불일치
    INVALID_MESSAGE      // 형식·칸·버전·순서 위반
}
