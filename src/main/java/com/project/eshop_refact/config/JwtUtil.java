package com.project.eshop_refact.config;

import com.project.eshop_refact.domain.UserRoleEnum;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Base64;
import java.util.Date;

@Slf4j
@Component
public class JwtUtil {

    private final long expirationTime;
    private final String secretKeyString;
    private final long refreshTokenTIme;
    private Key key;

    public JwtUtil(
            @Value("${jwt.expiration-time}") long expirationTime,
            @Value("${jwt.secret}") String secretKeyString,
            @Value("${jwt.refresh-expiration-time:1209600000}") long refreshTokenTIme
    ) {
        this.expirationTime = expirationTime;
        this.secretKeyString = secretKeyString;
        this.refreshTokenTIme = refreshTokenTIme;
    }

    @PostConstruct
    public void init() {
        byte[] keyBytes = Base64.getDecoder().decode(secretKeyString);
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    // Access Token 생성
    public String createToken(String username, UserRoleEnum role){
        return createJwt(username, role.name(), "ACCESS", expirationTime);
    }

    // Refresh Token 생성
    public String createRefreshToken(String username){
        return createJwt(username, null, "REFRESH", refreshTokenTIme);
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

    // 검증 및 Claims 한 번에 추출
    public Claims getClaimsIfValid(String token){
        try {
            return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
        } catch (io.jsonwebtoken.security.SecurityException | MalformedJwtException e) {
            log.error("잘못된 JWT 서명입니다. (위조 가능성) {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.warn("만료된 JWT 토큰입니다. {}", e.getMessage()); // 만료는 흔한 일이므로 Warn 레벨
        } catch (UnsupportedJwtException e) {
            log.error("지원되지 않는 JWT 토큰입니다. {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("JWT 토큰이 잘못되었습니다. (빈 값, 공백 등) {}", e.getMessage());
        } catch (io.jsonwebtoken.io.DecodingException e) {
            log.error("JWT 디코딩 실패 (형식 오류): {}", e.getMessage());
        }
        return null; // 검증 실패 시 null 반환
    }
    /*public String getUsernameFromToken(String token){
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    // 운영 관제 효율성을 위해 예외 유형별(위조 vs 만료) 로그 레벨 분리
    public boolean validateToken(String token){
        try{
            Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (io.jsonwebtoken.security.SecurityException | MalformedJwtException e) {
            log.error("잘못된 JWT 서명입니다. (위조 가능성) {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.warn("만료된 JWT 토큰입니다. {}", e.getMessage()); // 만료는 흔한 일이므로 Warn 레벨
        } catch (UnsupportedJwtException e) {
            log.error("지원되지 않는 JWT 토큰입니다. {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("JWT 토큰이 잘못되었습니다. (빈 값, 공백 등) {}", e.getMessage());
        } catch (io.jsonwebtoken.io.DecodingException e) {
            log.error("JWT 디코딩 실패 (형식 오류): {}", e.getMessage());
        }
        return false;
    }
    */

}
