package com.project.eshop_refact.global.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.eshop_refact.global.security.UserDetailsImpl;
import com.project.eshop_refact.domain.queue.WaitingQueueService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueInterceptor implements HandlerInterceptor {

    private final WaitingQueueService waitingQueueService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. [Scope 축소] 주문 관련 API에만 대기열 검증 적용
        // 로그인(/login)이나 회원가입(/users)은 대기열 없이 통과해야 함
        String requestURI = request.getRequestURI();
        if (!requestURI.startsWith("/api/orders")) {
            return true;
        }

        // 2. 조회(GET) 요청은 통과
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return true;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // 3. [Security Fix] 인증되지 않은 사용자 차단
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetailsImpl)) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "로그인이 필요합니다.");
            return false;
        }

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        Long userId = userDetails.getUser().getId();

        // 4. 대기열 통과 여부 확인 (Active Queue에 있는지)
        if (waitingQueueService.isAllowed(userId)) {
            return true;
        }

        log.warn("[진입 차단] 대기열 통과 못한 유저 접속 시도 - userId: {}", userId);

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value()); // 429 Error
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String errorResponse = objectMapper.writeValueAsString(Map.of(
                "code", "QUEUE_WAITING",
                "message", "현재 접속량이 많아 대기 중입니다. 잠시 후 다시 시도해주세요.",
                "userId", userId
        ));

        response.getWriter().write(errorResponse);

        return false;
    }
}