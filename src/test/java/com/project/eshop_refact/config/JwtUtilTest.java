package com.project.eshop_refact.config;

import com.project.eshop_refact.domain.user.UserRoleEnum;
import com.project.eshop_refact.global.security.JwtUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JwtUtil 단위 테스트
 * 스프링 컨텍스트 로드 없이 순수 자바 객체로 인스턴스를 생성하여,
 * 토큰 발급, 파싱, 그리고 다양한 예외 상황(만료, 서명 위조, 형식 오류)에 대한 검증 로직을 고속으로 테스트합니다.
 */
class JwtUtilTest {

    private JwtUtil jwtUtil;

    private static final String TEST_SECRET_KEY = "c2lsdmVybmluZS10ZWNoLXNwcmluZy1ib290LWp3dC10dXRvcmlhbC1zZWNyZXQtMQ=="; // Base64 Encoded
    private static final long ONE_HOUR_EXPIRATION_TIME = 3600000L;
    private static final long TWO_WEEKS_EXPIRATION_TIME = 1209600000L; // 14일

    @BeforeEach
    void setUp() {
        // 외부 의존성(스프링 컨테이너) 없이 순수 객체로 테스트 환경 구성 및 암호화 키 초기화
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
        assertThat(claims).isNotNull();
        assertThat(claims.getSubject()).isEqualTo(email);
        assertThat(claims.get("type")).isEqualTo("ACCESS");
        assertThat(claims.get("auth")).isEqualTo("USER");
    }

    @Test
    @DisplayName("실패 : 만료된 토큰")
    void validateExpiredToken() {
        // Given: 유효시간을 0으로 설정하여 즉시 만료되는 토큰 환경 구성
        JwtUtil expiredJwtUtil = new JwtUtil(0L, TEST_SECRET_KEY, 0L);
        expiredJwtUtil.init();

        String email = "expired@test.com";
        UserRoleEnum role = UserRoleEnum.USER;
        String expiredToken = expiredJwtUtil.createToken(email, role);

        //when
        Claims claims = expiredJwtUtil.getClaimsIfValid(expiredToken);

        // Then: 만료된 토큰 파싱 시 예외 처리되어 null을 반환하는지 검증
        assertThat(claims).isNull();
    }

    @Test
    @DisplayName("실패 : 위조된 토큰 검증")
    void validateTamperedToken(){
        // Given: 다른 서명 키(Secret Key)를 사용하여 생성된 위조 토큰
        String hackSecretBase64 = "aGFja2VyLWtleS1tdXN0LWJlLXNlY3JldC1hbmQtbG9uZy1lbm91Z2gtMjU2Yml0cw==";

        JwtUtil hackJwtUtil = new JwtUtil(ONE_HOUR_EXPIRATION_TIME, hackSecretBase64, TWO_WEEKS_EXPIRATION_TIME);
        hackJwtUtil.init();

        String hackerToken = hackJwtUtil.createToken("hacker@test.com", UserRoleEnum.USER);

        // When: 정상 서버의 JwtUtil로 위조 토큰 파싱 시도
        Claims claims = jwtUtil.getClaimsIfValid(hackerToken);

        // Then: 서명 불일치로 검증 실패(null 반환)
        assertThat(claims).isNull();
    }

    @Test
    @DisplayName("실패 : 잘못된 형식 토큰")
    void failFormToken(){
        // Given: JWT 구조(Header.Payload.Signature)가 손상된 문자열
        String garbageToken = "Bearer invalid.token.structure"; // 중간 띄어쓰기 밑 Bearer 그대로 넣음 -> 형식 에러 [MalformedJwtException)

        //When
        Claims claims = jwtUtil.getClaimsIfValid(garbageToken);

        // Then: 파싱 에러 발생으로 검증 실패(null 반환)
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
        assertThat(claims.get("type")).isEqualTo("REFRESH");

        // Refresh Token은 인가 용도가 아니므로 권한(auth) Claim을 포함하지 않음을 검증
        assertThat(claims.get("auth")).isNull();
    }
}