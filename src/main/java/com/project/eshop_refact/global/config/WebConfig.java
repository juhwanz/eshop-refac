package com.project.eshop_refact.global.config;

import com.project.eshop_refact.global.interceptor.QueueInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 웹 애플리케이션 인터셉터 설정
 * 트래픽 제어 및 보안 등 공통 관심사(Cross-Cutting Concerns)를 전역적으로 관리합니다.
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final QueueInterceptor queueInterceptor;

    /**
     * 대기열 인터셉터 등록
     * 시스템 부하가 가장 큰 주문(Order) 도메인으로 인입되는 트래픽을 제어하여 서버 다운을 방지합니다.
     * 단, 사용자가 대기열에 진입하고 자신의 순번을 확인하는 API(/api/orders/queue)는
     * 인터셉터 검증에서 제외하여 정상적인 대기열 사이클이 동작하도록 구성합니다.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
      // registry.addInterceptor(queueInterceptor)
      //          .addPathPatterns("/api/orders/**")
        //          .excludePathPatterns("/api/orders/queue");
    }
}
