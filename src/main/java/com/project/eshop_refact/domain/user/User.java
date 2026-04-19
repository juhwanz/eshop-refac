package com.project.eshop_refact.domain.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자(User) 도메인 엔티티
 */
@Entity
@Getter
@NoArgsConstructor
@Table(name = "users")
public class User {

    @Enumerated(EnumType.STRING)
    private UserStatus status = UserStatus.ACTIVE;


    @Id     // PK
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false, name = "user_role")
    @Enumerated(value = EnumType.STRING)
    private UserRoleEnum role;

    @Column(nullable = false)
    private int loginFailCount = 0;

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

    /**
     * 로그인 실패 처리
     * 연속 5회 이상 로그인 실패 시, 사용자 상태를 잠금(LOCKED)으로 전환하여 보안을 유지합니다.
     */
    public void handleLoginFailure(){
        this.loginFailCount++;
        if(this.loginFailCount >= 5){
            this.status = UserStatus.LOCKED;
        }
    }

    /**
     * 로그인 성공 시 누적된 실패 횟수를 초기화합니다.
     */
    public void resetLoginFailCount(){
        this.loginFailCount = 0;
    }

}
