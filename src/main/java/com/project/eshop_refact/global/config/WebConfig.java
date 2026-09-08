package com.project.eshop_refact.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 웹 애플리케이션 인터셉터 설정
 * 트래픽 제어 및 보안 등 공통 관심사(Cross-Cutting Concerns)를 전역적으로 관리합니다.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * 브라우저로 루트 경로에 접근하면 API 문서로 안내합니다.
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/", "/swagger-ui.html");
    }

}
