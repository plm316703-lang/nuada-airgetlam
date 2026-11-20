package io.github.plm316703.backend.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;


/**
 * 회원 엔티티 — DB의 users 테이블과 1:1로 대응하는 클래스.
 *
 * JPA는 이 클래스를 보고 테이블을 만들고(ddl-auto: update),
 * 객체를 저장하면 INSERT, 값을 바꾸면 UPDATE SQL을 대신 만들어 줌.
 */

@Entity         // 이클래스는 DB테이블과 연결된다.
@Table(name = "users")  //테이블 이름 지정
@Getter     //Lombok: 모든 필드의 Getter 자동 생성
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// JPA는 객체를 만들 때 "기본 생성자"가 반드시 필요,
// 하지만 아무나 new User()로 빝 객체를 만들면 안되므로 protected로 제한
public class User {

    @Id         //기본키(pk)
    @GeneratedValue(strategy = GenerationType.IDENTITY) //MySQL의 AUTO_INCREMENT 사용
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    // nullable = false → NOT NULL, unique = true → 같은 이메일 중복 가입 방지
    private String email;

    @Column(nullable = false)
    private String password; // W5에서 Spring Security로 암호화해서 저장할 예정

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    // ↑ enum을 DB에 "PATIENT" 같은 문자열로 저장
    //   (기본값인 ORDINAL은 0, 1, 2 숫자로 저장해서, 나중에 enum 순서를 바꾸면 데이터가 뒤섞임)
    @Column(nullable = false, length = 20)
    private Role role;

    @CreationTimestamp      //INSERT 될 떄 현재 시간을 자동으로 넣어줌
    @Column(nullable = false, updatable = false)    //가입 시간은 한번 저장 되면 수정 불가
    private LocalDateTime createdAt;

    /**
     * 비밀번호를 바꿔야 하는 계정인가 (관리자가 임시 비밀번호를 발급한 직후 true).
     * true인 동안은 비밀번호 변경 외의 기능을 쓸 수 없다 → 관리자도 직원의 진짜 비밀번호를 모르게 하려는 것.
     * @ColumnDefault: 이미 있는 계정(시연 계정 등)에 칸이 추가될 때 false로 채워 기존 로그인이 그대로 되게 함
     */
    @ColumnDefault("false")
    @Column(nullable = false)
    private boolean mustChangePassword;

    /** 임시 비밀번호 만료 시각. 이 시각까지 바꾸지 않으면 임시 비밀번호로는 로그인할 수 없다 (재발급 필요) */
    private LocalDateTime tempPasswordExpiresAt;

    /**
     * 회원 생성용 생성자.
     * setter를 열어두는 대신 생성자로만 값을 넣게 해서,
     * "필수 값이 빠진 회원"이 만들어지는 것을 막음.
     */
    public User(String email, String password, String name, Role role) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.role = role;
    }

    /** 임시 비밀번호 발급(새 계정·재발급 공통): 해시만 저장하고, 첫 로그인 때 바꾸도록 표시 */
    public void issueTempPassword(String hash, LocalDateTime expiresAt) {
        this.password = hash;
        this.mustChangePassword = true;
        this.tempPasswordExpiresAt = expiresAt;
    }

    /** 본인이 새 비밀번호로 바꿈 → 임시 상태 해제 */
    public void changePassword(String hash) {
        this.password = hash;
        this.mustChangePassword = false;
        this.tempPasswordExpiresAt = null;
    }

    /** 임시 비밀번호가 만료됐는가 (임시 상태가 아니면 항상 false) */
    public boolean isTempPasswordExpired(LocalDateTime now) {
        return mustChangePassword && tempPasswordExpiresAt != null && now.isAfter(tempPasswordExpiresAt);
    }
}
