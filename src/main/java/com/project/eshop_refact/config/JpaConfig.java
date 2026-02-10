package com.project.eshop_refact.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;


@Configuration
@EnableJpaAuditing // JPA Auditing 활성화
// @WebMvcTest 시 JPA 로딩 문제 방지를 위해 설정 분리
public class JpaConfig {
}