package com.project.eshop_refact.global.security;

import com.project.eshop_refact.domain.user.UserRoleEnum;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Base64;
import java.util.Date;

/**
 * JWT 생성, 파싱 및 유효성 검증을 담당하는 유틸리티 클래스
 * Access Token과 Refresh Token의 발급과 생명주기를 관리합니다.
 */
@Slf4j
@Component
public class JwtUtil {

    private final long expirationTime;
    private final String secretKeyString;
    private final long refreshTokenTime;
    private Key key;

    public JwtUtil(
            @Value("${jwt.expiration-time}") long expirationTime,
            @Value("${jwt.secret}") String secretKeyString,
            @Value("${jwt.refresh-expiration-time:1209600000}") long refreshTokenTIme
    ) {
        this.expirationTime = expirationTime;
        this.secretKeyString = secretKeyString;
        this.refreshTokenTime = refreshTokenTIme;
    }

    @PostConstruct
    public void init() {
        byte[] keyBytes = Base64.getDecoder().decode(secretKeyString);
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    // API 인가(Authorization) 처리를 위한 Access Token 발급
    public String createToken(String username, UserRoleEnum role){
        return createJwt(username, role.name(), "ACCESS", expirationTime);
    }

    // Access Token 갱신을 위한 Refresh Token 발급 (권한 Claim 제외)
    public String createRefreshToken(String username){
        return createJwt(username, null, "REFRESH", refreshTokenTime);
    }

    private String createJwt(String username, String role, String type, long expTime){
        Date now = new Date();
        JwtBuilder builder = Jwts.builder()
                .setSubject(username)
                .claim("type", type)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + expTime))
                .signWith(key);
        if(role != null) builder.claim("auth", role);
        return builder.compact();
    }

    /**
     * 토큰 서명 검증 및 Claims 추출
     * 예외 발생 시 로깅만 수행하고 null을 반환하여, 호출부(Filter)가 예외 처리가 아닌 흐름 제어에 집중하도록 설계했습니다.
     */
    public Claims getClaimsIfValid(String token){
        try {
            return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
        } catch (io.jsonwebtoken.security.SecurityException | MalformedJwtException e) {
            log.error("잘못된 JWT 서명입니다. (위조 가능성) {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            // 토큰 만료는 정상적인 인증 흐름의 일부이므로 WARN 레벨로 로깅하여 에러 트래킹 노이즈를 방지합니다.
            log.warn("만료된 JWT 토큰입니다. {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.error("지원되지 않는 JWT 토큰입니다. {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("JWT 토큰이 잘못되었습니다. (빈 값, 공백 등) {}", e.getMessage());
        } catch (io.jsonwebtoken.io.DecodingException e) {
            log.error("JWT 디코딩 실패 (형식 오류): {}", e.getMessage());
        }
        return null;
    }

    public long getExpiration(String token){
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getExpiration()
                .getTime();
    }
}
