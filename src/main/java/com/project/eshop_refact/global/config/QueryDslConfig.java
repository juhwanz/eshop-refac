package com.project.eshop_refact.global.config;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * QueryDSL 설정
 * 프로젝트 전역에서 동적 쿼리를 안전하고 편리하게 작성할 수 있도록
 * JPAQueryFactory를 스프링 빈(Bean)으로 등록합니다.
 */
@Configuration
public class QueryDslConfig {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * EntityManager를 주입받아 JPAQueryFactory 빈을 생성 및 등록합니다.
     */
    @Bean
    public JPAQueryFactory jpaQueryFactory(){
        return new JPAQueryFactory(entityManager);
    }
}
