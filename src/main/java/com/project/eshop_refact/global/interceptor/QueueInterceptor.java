package com.project.eshop_refact.global.interceptor;

import com.project.eshop_refact.domain.queue.WaitingQueueService;
import com.project.eshop_refact.global.exception.BusinessException;
import com.project.eshop_refact.global.exception.ErrorCode;
import com.project.eshop_refact.global.security.UserDetailsImpl;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 주문 도메인 진입 대기열(Queue) 검증 인터셉터
 * 대규모 트래픽 발생 시, 활성 대기열(Active Queue)에 정상적으로 진입한 사용자만 주문 로직을 수행할 수 있도록 제어합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueueInterceptor implements HandlerInterceptor {

    private final WaitingQueueService waitingQueueService;
    // 💡 핵심 1: ObjectMapper 의존성이 완전히 제거되었습니다! (가벼워진 빈)

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        // 1. 방어적 검증: 주문 관련 API 라우팅에만 동작
        String requestURI = request.getRequestURI();
        if (!requestURI.startsWith("/api/orders")) {
            return true;
        }

        // 2. 서버 상태를 변경하지 않는 단순 조회(GET) 요청은 통과
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return true;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // 3. 인증 정보 검증 실패 시 예외 던지기
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetailsImpl)) {
            // response.sendError(...) 대신 우리가 만든 예외 규격 사용
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        Long userId = userDetails.getUser().getId();

        // 4. 활성 큐(Active Queue) 진입 여부 검증 (통과 시 true)
        if (waitingQueueService.isAllowed(userId)) {
            return true;
        }

        log.warn("[진입 차단] 대기열 미통과 사용자 접근 시도 - userId: {}", userId);

        // 인터셉터에서 던진 예외는 DispatcherServlet을 거쳐 @RestControllerAdvice가 낚아챕니다.
        throw new BusinessException(ErrorCode.QUEUE_WAITING);
    }
}