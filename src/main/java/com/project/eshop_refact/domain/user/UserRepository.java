package com.project.eshop_refact.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    /**
     * 이메일을 통한 사용자 단건 조회
     */
    Optional<User> findByEmail(String email);
}
