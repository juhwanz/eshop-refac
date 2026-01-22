package com.project.eshop_refact.config;

import com.project.eshop_refact.domain.UserRoleEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        String secret = "c2lsdmVybmluZS10ZWNoLXNwcmluZy1ib290LWp3dC10dXRvcmlhbC1zZWNyZXQtc2lsdmVybmluZS10ZWNoLXNwcmluZy1ib290LWp3dC10dXRvcmlhbC1zZWNyZXQK";
        long expiration = 3600000;

        jwtUtil = new JwtUtil(expiration, secret);
        jwtUtil.init();
    }

    @Test
    @DisplayName("토큰 생성 및 검증")
    void createAndValidateToken() {

        String email = "test@test.com";
        UserRoleEnum role = UserRoleEnum.USER;

        String token = jwtUtil.createToken(email, role);

        assertThat(token).isNotNull();
        assertThat(jwtUtil.validateToken(token)).isTrue();
        assertThat(jwtUtil.getUsernameFromToken(token)).isEqualTo(email);
    }
}