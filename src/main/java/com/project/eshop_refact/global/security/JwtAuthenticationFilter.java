package com.project.eshop_refact.global.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;

/**
 * JWT 기반 커스텀 인증 필터
 * HTTP 요청의 Authorization 헤더에서 JWT를 추출하여 유효성, 토큰 타입,
 * 블랙리스트 등록 여부를 검증한 후 SecurityContext에 인증 정보를 저장합니다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsServiceImpl;
    private final RedisTemplate<String, String> redisTemplate;

    /**
     * 토큰 검증이 불필요한 화이트리스트 경로에 대해 필터 로직을 생략합니다.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request){
        String[] excludePath = {"/api/users/signup", "/api/users/login", "/api/users/reissue", "/v3/api-docs", "/swagger-ui", "/actuator"};
        String path = request.getRequestURI();
        return Arrays.stream(excludePath).anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException{
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            Claims claims = jwtUtil.getClaimsIfValid(token);

            // Refresh Token이 인증(API 접근) 용도로 오용되는 것을 방지하기 위해 Access Token을 명시적으로 확인합니다.
            if (claims != null && "ACCESS".equals(claims.get("type"))) {

                // Redis 블랙리스트를 조회하여 로그아웃 처리된 토큰의 탈취 및 재사용을 원천 차단합니다.
                String isLogout = redisTemplate.opsForValue().get("BLACKLIST:" + token);
                if(StringUtils.hasText(isLogout)){
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "로그아웃된 토큰입니다");
                    return;
                }

                String email = claims.getSubject();
                UserDetails userDetails = userDetailsServiceImpl.loadUserByUsername(email);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }

}