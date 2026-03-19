package com.project.eshop_refact.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor      // JPA 리플렉션을 위한 기본 생성자 (Protected 권장)
@Table(name = "users")  // DB 예약어 충돌 방지
public class User {


    @Id     // PK
    @GeneratedValue(strategy = GenerationType.IDENTITY) // MySQL의 Auto Increment 기능 위임해 사용.
    private Long id;

    @Column(nullable = false, unique = true) // 애플리케이션 레벨 + DB 스키마에도 유니크 제약조건 반영.
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false, name = "user_role")
    @Enumerated(value = EnumType.STRING)    // 순서 변경에 안전한 String 저장 방식 채택
    private UserRoleEnum role;

    // 생성자는 Protected로 제한 (JPA용, 외부 직접 호출 방지)
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
}
