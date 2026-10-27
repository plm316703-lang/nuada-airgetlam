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



}
