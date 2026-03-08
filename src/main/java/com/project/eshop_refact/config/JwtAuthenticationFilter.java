package com.project.eshop_refact.config;

import com.project.eshop_refact.service.UserDetailsServiceImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// 요청당 1회 실행을 보장하여 불필요한 필터 중복 수행 방지
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsServiceImpl;

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

