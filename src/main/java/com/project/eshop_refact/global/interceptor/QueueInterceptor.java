package com.project.eshop_refact.global.interceptor;

import com.project.eshop_refact.global.exception.BusinessException;
import com.project.eshop_refact.global.exception.ErrorCode;
import com.project.eshop_refact.global.security.UserDetailsImpl;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 주문 진입 시 인증 정보를 확인합니다.
 * 대기열 권한은 완료된 요청을 복구한 뒤 주문 파사드에서 검증합니다.
 */
@Component
public class QueueInterceptor implements HandlerInterceptor {

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

        // 신규 주문의 대기열 검사는 완료 요청 복구 이후 상품 락 안에서 수행합니다.
        // 이 위치에서 차단하면 주문 완료로 권한이 제거된 사용자가 결과를 재조회할 수 없습니다.
        return true;
    }
}
