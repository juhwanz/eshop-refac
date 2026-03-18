package com.project.eshop_refact.config;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class QueryDslConfig {

    @PersistenceContext
    private EntityManager entityManager;

    // JPQL의 보완: 컴파일 시점의 Type-Safety 확보 및 동적 쿼리 처리
    @Bean
    public JPAQueryFactory jpaQueryFactory(){
        return new JPAQueryFactory(entityManager);
    }
}
