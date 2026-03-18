package com.project.eshop_refact.config;

import com.project.eshop_refact.service.UserDetailsServiceImpl;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;

// 요청당 1회 실행을 보장하여 불필요한 필터 중복 수행 방지
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsServiceImpl;

    // 공개 경로는 필터 건너뛰기
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request){
        String[] excludePath = {"/api/users/signup", "/api/users/login", "/v3/api-docs", "/swagger-ui"};
        String path = request.getRequestURI();
        return Arrays.stream(excludePath).anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException{
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            Claims claims = jwtUtil.getClaimsIfValid(token);

            // Access Token 타입 검증 (Refresh Token 사용 차단)
            if (claims != null && "ACCESS".equals(claims.get("type"))) {
                String email = claims.getSubject();
                UserDetails userDetails = userDetailsServiceImpl.loadUserByUsername(email);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

                // 인증 세부 정보(IP, 세션 ID 등) 세팅
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }
}
    /*
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if(header == null || !header.startsWith("Bearer ")){
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);

        // 2. 토큰 유효성 검증
        if(jwtUtil.validateToken(token)){

            String email = jwtUtil.getUsernameFromToken(token);

            // 3. DB에서 최신 사용자 정보 조회 (권한 변경, 계정 정지 등 즉각 반영을 위해 DB 조회 선택)
            UserDetails userDetails = userDetailsServiceImpl.loadUserByUsername(email);

            // 4. 인증 객체 생성 (Authentication)
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

            // 5. SecurityContext에 인증 객체 저장 (ThreadLocal에 저장해 전역적 참조 가능)
            SecurityContextHolder.getContext().setAuthentication(authentication);

        }

        // 다음 필터로 진행
        filterChain.doFilter(request, response);
    }
}
*/
