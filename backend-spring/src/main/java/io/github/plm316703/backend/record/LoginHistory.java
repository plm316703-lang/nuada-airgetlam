package io.github.plm316703.backend.record;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 로그인·계정 관리 기록 — login_history 테이블 (11/20 R3, 감사 기록).
 * "누가 언제 로그인했고, 몇 번 실패했고, 누가 계정을 건드렸나"를 한곳에서 본다.
 *
 * 사람을 외래 키(users.id) 대신 이메일 글자로 남기는 이유:
 *  - 없는 이메일로 시도한 공격도 기록해야 하는데, 그 사람은 users에 없다
 *  - 기록은 나중에 계정이 바뀌어도 "그때 무슨 일이 있었는지" 그대로 남아야 한다
 * 보관 기간: 1년 (문서상 방침, 자동 삭제는 아직 없음). IP도 개인정보로 볼 수 있기 때문.
 */
@Entity
@Table(name = "login_history", indexes = @Index(name = "idx_login_history_event", columnList = "event"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoginHistory {

    public enum Event {
        LOGIN_SUCCESS,
        LOGIN_FAILED,          // 이유는 failReason
        ACCOUNT_LOCKED,        // 연속 실패로 지금 막 잠김
        ACCOUNT_UNLOCKED,      // 관리자가 잠금 해제
        ACCOUNT_CREATED,       // 관리자가 계정 발급
        TEMP_PASSWORD_ISSUED,  // 관리자가 임시 비밀번호 재발급
        PASSWORD_CHANGED,      // 본인이 비밀번호 변경
        INVITATION_CREATED,    // 연구책임자가 환자·보호자 초대 (다시 초대 포함)
        INVITATION_ACCEPTED    // 본인이 초대 링크로 비밀번호를 정함
    }

    public enum FailReason { BAD_CREDENTIALS, LOCKED, TEMP_EXPIRED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    /*
     * @JdbcTypeCode(VARCHAR): Hibernate는 MySQL에서 enum을 MySQL 전용 enum('A','B') 타입으로 만든다.
     * 그러면 Java에 새 종류(예: INVITATION_CREATED)를 추가해도 ddl-auto: update가 칸 정의를 바꾸지 않아 저장이 실패한다
     * (11/20 R4에서 발견). 글자 칸(varchar)으로 두면 종류를 늘려도 DB를 고칠 필요가 없다.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private Event event;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 20)
    private FailReason failReason;

    /** 대상 계정 이메일 (로그인 시도라면 입력된 이메일 그대로, 길면 자름) */
    @Column(length = 100)
    private String email;

    /** 처리한 사람 (관리자 동작일 때). 본인 로그인·변경이면 null */
    @Column(length = 100)
    private String actorEmail;

    /** IPv6 최대 45자 */
    @Column(length = 45)
    private String ip;

    public LoginHistory(Event event, FailReason failReason, String email, String actorEmail, String ip) {
        this.occurredAt = LocalDateTime.now();
        this.event = event;
        this.failReason = failReason;
        // 관문 기록과 같은 원칙: 이상한 입력(엄청 긴 이메일)이 와도 저장이 실패하지 않게 잘라서 남긴다
        this.email = cut(email, 100);
        this.actorEmail = cut(actorEmail, 100);
        this.ip = cut(ip, 45);
    }

    private static String cut(String s, int max) {
        return s == null ? null : s.substring(0, Math.min(s.length(), max));
    }
}
