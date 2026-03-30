package com.project.eshop_refact.global.security;

import com.project.eshop_refact.domain.user.User;
import com.project.eshop_refact.domain.user.UserRoleEnum;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

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

        // "ROLE_" 접두사는 Spring Security의 hasRole() 등에서 사용하는 컨벤션
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
    public boolean isAccountNonExpired(){return true;}

    @Override
    public boolean isAccountNonLocked(){return true;}
    @Override
    public boolean isCredentialsNonExpired(){return true;}
    @Override
    public boolean isEnabled(){return true;}

}
