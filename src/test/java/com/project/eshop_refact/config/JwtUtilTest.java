package com.project.eshop_refact.config;

import com.project.eshop_refact.domain.UserRoleEnum;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Ref;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;

// Unit Test : 스프링 컨텍스트 없이 빠르게 시도 가능.
class JwtUtilTest {
    //@Spring Boot Test 띄우지 않고, new JwtUtil(..)로 객체 생성 테스트 -> 속도 빠름
    // 원본 코드의 @Value는 스프링이 해석, 자바 문법적으로 무시. -> 테스트 코드에서  new ~가 가능.

    private JwtUtil jwtUtil;

    private static final String TEST_SECRET_KEY = "c2lsdmVybmluZS10ZWNoLXNwcmluZy1ib290LWp3dC10dXRvcmlhbC1zZWNyZXQtMQ=="; // Base64 Encoded
    private static final long ONE_HOUR_EXPIRATION_TIME = 3600000L;
    private static final long TWO_WEEKS_EXPIRATION_TIME = 1209600000L; // 14일

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(ONE_HOUR_EXPIRATION_TIME, TEST_SECRET_KEY, TWO_WEEKS_EXPIRATION_TIME);
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
        Claims claims = jwtUtil.getClaimsIfValid(token);

        //Then
        assertThat(token).isNotBlank();
        // 토큰 유효성 검증.
        assertThat(claims).isNotNull();
        assertThat(claims.getSubject()).isEqualTo(email);
        assertThat(claims.get("type")).isEqualTo("ACCESS");
        assertThat(claims.get("auth")).isEqualTo("USER");
    }

    @Test
    @DisplayName("실패 : 만료된 토큰")
    void validateExpiredToken() {
        // Given : 유효시간 0 -> 즉시 만료
        JwtUtil expiredJwtUtil = new JwtUtil(0L, TEST_SECRET_KEY, 0L);
        expiredJwtUtil.init();

        String email = "expired@test.com";
        UserRoleEnum role = UserRoleEnum.USER;
        String expiredToken = expiredJwtUtil.createToken(email, role);

        //when
        Claims claims = expiredJwtUtil.getClaimsIfValid(expiredToken);

        //Then : 만료 => null 반환
        assertThat(claims).isNull();
    }

    @Test
    @DisplayName("실패 : 위조된 토큰 검증")
    void validateTamperedToken(){
        // Given : 해커 객체
        String hackSecretBase64 = "aGFja2VyLWtleS1tdXN0LWJlLXNlY3JldC1hbmQtbG9uZy1lbm91Z2gtMjU2Yml0cw==";

        JwtUtil hackJwtUtil = new JwtUtil(ONE_HOUR_EXPIRATION_TIME, hackSecretBase64, TWO_WEEKS_EXPIRATION_TIME);
        hackJwtUtil.init();

        String hackerToken = hackJwtUtil.createToken("hacker@test.com", UserRoleEnum.USER);

        // When: 정상 서버(jwtUtil)에서 해커의 토큰 검증
        Claims claims = jwtUtil.getClaimsIfValid(hackerToken);

        // Then: 서명이 다르므로 null 반환
        assertThat(claims).isNull();
    }

    @Test
    @DisplayName("실패 : 잘못된 형식 토큰")
    void failFormToken(){
        // Given
        String garbageToken = "Bearer invalid.token.structure"; // 중간 띄어쓰기 밑 Bearer 그대로 넣음 -> 형식 에러 [MalformedJwtException)

        //When
        Claims claims = jwtUtil.getClaimsIfValid(garbageToken);

        // Then: 파싱 에러로 null 반환
        assertThat(claims).isNull();
    }

    @Test
    @DisplayName("성공 : Refresh Token 생성 및 검증")
    void createValidRefreshToken(){
        //Given
        String email = "test@test.com";

        //When
        String refreshToken = jwtUtil.createRefreshToken("test@test.com");
        Claims claims = jwtUtil.getClaimsIfValid(refreshToken);

        // Then
        assertThat(refreshToken).isNotBlank();
        assertThat(claims).isNotNull();
        assertThat(claims.getSubject()).isEqualTo(email);
        assertThat(claims.get("type")).isEqualTo("REFRESH"); // 타입 확인
        assertThat(claims.get("auth")).isNull(); // Refresh Token은 권한 정보를 담지 않음
    }
}