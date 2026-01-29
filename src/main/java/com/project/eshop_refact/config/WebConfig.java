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

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(queueInterceptor)
                .addPathPatterns("/api/orders"); // 오직 '주문 생성' 요청만 검사
        // .excludePathPatterns("/api/orders/queue/**"); // (나중에 대기열 확인 API는 제외해야 함)
    }
}
