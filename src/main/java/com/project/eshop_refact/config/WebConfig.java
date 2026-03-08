package com.project.eshop_refact.config;

import com.project.eshop_refact.interceptor.QueueInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final QueueInterceptor queueInterceptor;

    // Traffic Throttling: 대규모 트래픽 유입 시 시스템 과부하 방지를 위한 유량 제어(Flow Control) 적용
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(queueInterceptor)
                .addPathPatterns("/api/orders"); //오직 '주문 생성' 요청만 검사
        // .excludePathPatterns("/api/orders/queue/**"); // (나중에 대기열 확인 API는 제외해야 함)
    }
}
