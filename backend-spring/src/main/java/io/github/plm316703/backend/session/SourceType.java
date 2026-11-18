package io.github.plm316703.backend.session;

/**
 * 뇌파 데이터가 어디서 왔는지 (프로젝트 개요의 devices.source_type 결정).
 * 우리 시연은 실제 공개 데이터를 재생하므로 PLAYBACK. 가짜(더미)와 구분한다.
 */
public enum SourceType {
    SYNTHETIC,  // 만들어 낸 가짜 신호
    PLAYBACK,   // 실제 측정된 공개 데이터를 실시간처럼 재생 (BCI IV 2a)
    PHYSICAL    // 실제 뇌파 장비 (발표 이후)
}
