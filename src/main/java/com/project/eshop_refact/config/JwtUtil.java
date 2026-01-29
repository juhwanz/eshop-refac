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
    private Key key;

    private final long REFRESH_TOKEN_TIME = 14 * 24 * 60 * 60 * 1000L;


    public JwtUtil(
            @Value("${jwt.expiration-time}") long expirationTime,
            @Value("${jwt.secret}") String secretKeyString
    ) {
        this.expirationTime = expirationTime;
        this.secretKeyString = secretKeyString;
    }

    @PostConstruct
    public void init() {
        byte[] keyBytes = Base64.getDecoder().decode(secretKeyString);
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public String createToken(String username, UserRoleEnum role) {
        Claims claims = Jwts.claims().setSubject(username);
        claims.put("auth", role.name());

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationTime);

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(key)
                .compact();
    }

    public String createRefreshToken(String username){
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + REFRESH_TOKEN_TIME);

        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(key)
                .compact();
    }

    public String getUsernameFromToken(String token){
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

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


}
