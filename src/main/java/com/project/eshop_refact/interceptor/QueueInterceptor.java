package com.project.eshop_refact.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.eshop_refact.config.UserDetailsImpl;
import com.project.eshop_refact.service.queue.WaitingQueueService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetailsImpl)) {
            return true;
        }

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        Long userId = userDetails.getUser().getId();

        if (waitingQueueService.isAllowed(userId)) {
            return true;
        }

        log.warn("[진입 차단] 대기열 통과 못한 유저 접속 시도 - userId: {}", userId);

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value()); // 429 Too Many Requests
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String errorResponse = objectMapper.writeValueAsString(Map.of(
                "code", "QUEUE_WAITING",
                "message", "현재 대기 중입니다. 잠시 후 다시 시도해주세요.",
                "userId", userId
        ));

        response.getWriter().write(errorResponse);

        return false;
    }
}
