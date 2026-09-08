package com.project.eshop_refact.domain.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;

/**
 * 사용자(User) 도메인 엔티티
 */
@Entity
@Getter
@NoArgsConstructor
@Table(name = "users")
public class User {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private UserStatus status = UserStatus.ACTIVE;


    @Id     // PK
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(nullable = false, name = "user_role", length = 50)
    @Enumerated(value = EnumType.STRING)
    private UserRoleEnum role;

    @Column(nullable = false)
    private int loginFailCount = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    public User(String email, String password, String username, UserRoleEnum role) {
        if(email == null || email.isBlank()){
            throw new IllegalArgumentException("이메일은 필수입니다.");
        }
        if(password == null || password.isBlank()){
            throw new IllegalArgumentException("비밀번호는 필수입니다.");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("사용자 이름은 필수입니다.");
        }
        if (role == null) {
            throw new IllegalArgumentException("권한 역할은 필수입니다.");
        }
        this.email = email;
        this.password = password;
        this.username = username;
        this.role = role;
    }

    public boolean applyLoginAttempt(boolean passwordMatches, Instant now,
                                     int maxFailures, Duration lockDuration) {
        if (now == null || lockDuration == null || maxFailures <= 0 || lockDuration.isNegative()
                || lockDuration.isZero()) {
            throw new IllegalArgumentException("로그인 잠금 정책이 올바르지 않습니다.");
        }
        if (status != UserStatus.ACTIVE) {
            return false;
        }
        if (isTemporarilyLocked(now)) {
            return false;
        }

        if (lockedUntil != null) {
            resetLoginFailureState();
        }

        if (!passwordMatches) {
            loginFailCount++;
            if (loginFailCount >= maxFailures) {
                loginFailCount = maxFailures;
                lockedUntil = now.plus(lockDuration);
            }
            return false;
        }

        resetLoginFailureState();
        return true;
    }

    public boolean isTemporarilyLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    private void resetLoginFailureState() {
        loginFailCount = 0;
        lockedUntil = null;
    }

}
