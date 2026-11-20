package io.github.plm316703.backend.user;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 초대 — invitations 테이블 (11/20 R4).
 * 환자·보호자 계정은 직원이 등록하고, 본인이 초대 링크로 들어와 직접 비밀번호를 정한다.
 *
 * 링크 속 비밀값(token)은 저장하지 않고 SHA-256 해시만 저장한다:
 *  - DB가 새어 나가도 해시로는 링크를 만들 수 없다
 *  - 비밀번호처럼 BCrypt를 쓰지 않는 이유: 토큰은 사람이 정한 값이 아니라 32바이트 난수라 추측이 불가능하고,
 *    링크로 들어왔을 때 "해시로 바로 찾기"가 필요하다 (BCrypt는 매번 결과가 달라 검색할 수 없음)
 */
@Entity
@Table(name = "invitations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Invitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime usedAt;      // 한 번 쓰면 끝 (1회용)
    private LocalDateTime revokedAt;   // 다시 초대하면 이전 링크는 무효

    @Column(length = 100)
    private String createdBy;          // 초대한 직원 이메일 (기록용)

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public Invitation(User user, String tokenHash, LocalDateTime expiresAt, String createdBy) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdBy = createdBy;
        this.createdAt = LocalDateTime.now();
    }

    /** 지금 쓸 수 있는 링크인가. 안 되면 이유를 문장으로 (화면에 그대로 보여 줌) */
    public String unusableReason(LocalDateTime now) {
        if (usedAt != null) return "이미 사용한 초대 링크입니다. 비밀번호를 잊었다면 관리자에게 임시 비밀번호를 요청하세요.";
        if (revokedAt != null) return "더 이상 쓸 수 없는 초대 링크입니다. 가장 최근에 받은 링크를 사용하세요.";
        if (now.isAfter(expiresAt)) return "초대 링크가 만료되었습니다. 연구책임자에게 다시 초대를 요청하세요.";
        return null;
    }

    public void markUsed(LocalDateTime now) { this.usedAt = now; }

    public void revoke(LocalDateTime now) {
        if (usedAt == null && revokedAt == null) this.revokedAt = now;
    }
}
