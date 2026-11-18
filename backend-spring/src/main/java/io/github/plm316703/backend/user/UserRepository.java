package io.github.plm316703.backend.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // 로그인할 때 이메일로 회원 찾기 (없을 수도 있으니 Optional)
    Optional<User> findByEmail(String email);

    // 역할별 회원 목록 (예: 연구책임자가 배정할 연구자 목록)
    List<User> findAllByRole(Role role);
}
