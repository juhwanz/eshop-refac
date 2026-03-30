package com.project.eshop_refact.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;


@Configuration
@EnableJpaAuditing // JPA Auditing 활성화
// @WebMvcTest에서 JPA auditing 로딩 분리 목적
public class JpaConfig {
}