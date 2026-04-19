package com.project.eshop_refact.global.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 분산 환경 스케줄러 동시성 제어 설정
 * 다중 서버(Scale-out) 환경에서 동일한 스케줄링 작업이 중복 실행되는 것을 방지하기 위해 ShedLock을 구성합니다.
 */
@Configuration
@EnableScheduling
// 노드 비정상 종료 시 데드락을 방지하기 위해 최대 락 점유 시간 3분 제한
@EnableSchedulerLock(defaultLockAtMostFor = "3m")
public class ShedLockConfig {

    /**
     * Redis를 분산 락(Distributed Lock) 저장소로 사용하기 위한 Provider 빈을 등록합니다.
     */
    @Bean
    public LockProvider lockProvider(RedisConnectionFactory connectionFactory) {
        return new RedisLockProvider(connectionFactory, "eshop_lock");
    }
}