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

    // 생성 시 필수 데이터를 강제하여, '이메일 없는 유저' 같은 불완전한 객체 생성을 원천 차단
    public User(String email, String password, String username, UserRoleEnum role) {
        this.email = email;
        this.password = password;
        this.username = username;
        this.role = role;
    }
}
