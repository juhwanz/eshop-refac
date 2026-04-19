package com.project.eshop_refact.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing 설정
 * @EnableJpaAuditing을 메인 애플리케이션 클래스에서 분리하여,
 * @WebMvcTest 등의 슬라이스 테스트(Slice Test) 환경에서 발생하는 JPA 메타모델 로딩 에러를 방지합니다.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}