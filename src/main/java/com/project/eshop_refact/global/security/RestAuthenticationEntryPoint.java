package com.project.eshop_refact.global.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * REST API 환경에 맞춘 커스텀 인증 예외(401) 진입점
 * 유효한 인증 정보 없이 보호된 자원에 접근할 때, Spring Security의 기본 동작(로그인 폼 리다이렉트 등)을 대체하고
 * 클라이언트가 예외를 명확히 파싱할 수 있도록 구조화된 JSON 응답을 반환합니다.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"INVALID_TOKEN\",\"message\":\"유효한 인증 정보가 없습니다.\"}");
    }
}
