package com.project.eshop_refact.global.security;

import com.project.eshop_refact.domain.user.User;
import com.project.eshop_refact.domain.user.UserRoleEnum;
import com.project.eshop_refact.domain.user.UserStatus;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * Spring Security 인증 객체 어댑터
 * 핵심 도메인인 User 엔티티를 Spring Security의 UserDetails 규격에 맞춰 래핑하며,
 * 회원의 권한 정보와 계정 활성화 상태를 프레임워크에 전달합니다.
 */
@Getter
public class UserDetailsImpl implements UserDetails {

    private final User user;

    public UserDetailsImpl(User user){
        this.user = user;
    }


    @Override
    public Collection<? extends GrantedAuthority> getAuthorities(){
        UserRoleEnum role = user.getRole();
        String authority = role.name();

        // Spring Security의 권한 검증(hasRole 등) 기본 컨벤션을 충족하기 위해 'ROLE_' 접두사를 추가합니다.
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + authority));
    }

    @Override
    public String getPassword(){
        return user.getPassword();
    }

    @Override
    public String getUsername(){
        return user.getEmail();
    }

    @Override
    public boolean isAccountNonExpired(){return this.user.getStatus() != UserStatus.LOCKED;} // LOCKED이면 false

    @Override
    public boolean isAccountNonLocked(){return this.user.getStatus() != UserStatus.LOCKED;}
    @Override
    public boolean isCredentialsNonExpired(){return this.user.getStatus() == UserStatus.ACTIVE;}
    @Override
    public boolean isEnabled(){return this.user.getStatus() == UserStatus.ACTIVE;} // ACTIVE만 통과, 탈퇴, 정지 모두 false

}
