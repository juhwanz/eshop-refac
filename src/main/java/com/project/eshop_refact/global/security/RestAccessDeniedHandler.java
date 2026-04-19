package com.project.eshop_refact.global.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * REST API 환경에 맞춘 커스텀 인가 예외(403) 핸들러
 * 인증된 사용자가 권한이 없는 자원에 접근할 때, Spring Security의 기본 에러 페이지 대신
 * 클라이언트가 예외를 명확히 처리할 수 있도록 구조화된 JSON 응답을 반환합니다.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"FORBIDDEN_ACCESS\",\"message\":\"해당 자원에 접근할 권한이 없습니다.\"}");
    }
}