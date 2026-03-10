package com.project.eshop_refact.config;

import com.project.eshop_refact.domain.UserRoleEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Ref;

import static org.assertj.core.api.Assertions.assertThat;

// Unit Test : 스프링 컨텍스트 없이 빠르게 시도 가능.
class JwtUtilTest {
    //@Spring Boot Test 띄우지 않고, new JwtUtil(..)로 객체 생성 테스트 -> 속도 빠름
    // 원본 코드의 @Value는 스프링이 해석, 자바 문법적으로 무시. -> 테스트 코드에서  new ~가 가능.

    private JwtUtil jwtUtil;

    private static final String TEST_SECRET_KEY = "c2lsdmVybmluZS10ZWNoLXNwcmluZy1ib290LWp3dC10dXRvcmlhbC1zZWNyZXQtMQ=="; // Base64 Encoded
    private static final long ONE_HOUR_ExpirationTime = 3600000L;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(ONE_HOUR_ExpirationTime, TEST_SECRET_KEY); // expirationTime, 키
        jwtUtil.init(); //@PostConstruct 수동 호출. // 키 암호화.
    }

    @Test
    @DisplayName("토큰 생성 및 검증")
    void createAndValidateToken() {
        //Given
        String email = "test@test.com";
        UserRoleEnum role = UserRoleEnum.USER;

        //When
        String token = jwtUtil.createToken(email, role);

        //Then
        assertThat(token).isNotBlank();
        // 토큰 유효성 검증.
        assertThat(jwtUtil.validateToken(token)).isTrue();
        // 토큰 email.
        assertThat(jwtUtil.getUsernameFromToken(token)).isEqualTo(email);
    }

    @Test
    @DisplayName("실패 : 만료된 토큰")
    void validateExpiredToken() {
        // Given : 유효시간 0 -> 즉시 만료
        JwtUtil expiredJwtUtil = new JwtUtil(0L, TEST_SECRET_KEY);
        expiredJwtUtil.init();

        String email = "expired@test.com";
        UserRoleEnum role = UserRoleEnum.USER;
        String expiredToken = expiredJwtUtil.createToken(email, role);

        //when
        //Then : false여야 함.
        assertThat(expiredJwtUtil.validateToken(expiredToken)).isFalse();
    }

    @Test
    @DisplayName("실패 : 위조된 토큰 검증")
    void validateTamperedToken(){
        // Given : 정상 토큰
        String token = jwtUtil.createToken("test@test.com", UserRoleEnum.USER);

        // 해커
        String Hack_secretKeyString = "hacker-secret-key-must-be-long-enough-for-hmac-sha-algorithms-minimum-bytes";
        String hackSecretBase64 = java.util.Base64.getEncoder().encodeToString(Hack_secretKeyString.getBytes());

        JwtUtil hackJwtUtil = new JwtUtil(ONE_HOUR_ExpirationTime, hackSecretBase64);
        hackJwtUtil.init();

        String hackerToken = hackJwtUtil.createToken("hacker@test.com", UserRoleEnum.USER);

        // When: 해커의 키로 만든 토큰이거나, 내 키로 서명 안 된 토큰 검증
        // (여기서는 상황을 반대로, 내 서버(jwtUtil)에 해커가 만든 토큰을 던졌을 때를 가정)
        //서명이 다르므로 실패해야 함
        // Then
        assertThat(jwtUtil.validateToken(hackerToken)).isFalse();
    }

    @Test
    @DisplayName("실패 : 잘못된 형식 토큰")
    void failFormToken(){
        // Given
        String garbageToken = "Bearer invalid.token.structure"; // 중간 띄어쓰기 밑 Bearer 그대로 넣음 -> 형식 에러 [MalformedJwtException)

        //Then
        assertThat(jwtUtil.validateToken(garbageToken)).isFalse();
    }

    @Test
    @DisplayName("성공 : Refresh Token 생성 및 검증")
    void createValidRefreshToken(){
        //Given
        String RefreshToken = jwtUtil.createRefreshToken("test@test.com");

        assertThat(RefreshToken).isNotBlank();
        assertThat(jwtUtil.validateToken(RefreshToken)).isTrue();
        assertThat(jwtUtil.getUsernameFromToken(RefreshToken)).isEqualTo("test@test.com");
    }
}