package com.project.eshop_refact.repository;

import com.project.eshop_refact.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    // Null Safety를 위해 Optional 반환
    Optional<User> findByEmail(String email);
}
