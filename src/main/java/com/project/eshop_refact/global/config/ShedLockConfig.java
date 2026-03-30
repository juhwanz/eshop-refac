package com.project.eshop_refact.global.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "3m") // 기본적으로 최대 3분간 락을 유지하도록 안전장치 설정
public class ShedLockConfig {

    @Bean
    public LockProvider lockProvider(RedisConnectionFactory connectionFactory) {
        // 기존에 사용 중인 Redis 환경을 설정 주입받아 ShedLock의 저장소로 지정
        return new RedisLockProvider(connectionFactory, "eshop_lock"); // Prefix
    }
}