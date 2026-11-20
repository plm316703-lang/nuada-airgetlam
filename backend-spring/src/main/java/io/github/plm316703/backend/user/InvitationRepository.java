package io.github.plm316703.backend.user;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {

    // 링크로 들어왔을 때: 토큰 해시로 찾고, 대상 계정도 함께 (트랜잭션 밖 지연 로딩 오류 방지)
    @EntityGraph(attributePaths = "user")
    Optional<Invitation> findByTokenHash(String tokenHash);

    // 다시 초대할 때: 그 계정의 이전 링크들을 무효로 만들려고
    List<Invitation> findAllByUser(User user);

    // 초대를 받았지만 아직 가입하지 않은 계정인가 판단용
    boolean existsByUser(User user);
    boolean existsByUserAndUsedAtIsNotNull(User user);
}
