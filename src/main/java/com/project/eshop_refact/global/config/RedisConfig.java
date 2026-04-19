package com.project.eshop_refact.global.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis 인프라 설정
 * Spring Cache 추상화(@Cacheable)를 위한 CacheManager와
 * 수동 제어(토큰, 대기열 등)를 위한 RedisTemplate을 분리하여 구성합니다.
 */
@Configuration
@EnableCaching
public class RedisConfig {

    /**
     * Spring Cache 환경설정
     * 외부 GUI 툴에서 캐시 데이터 확인이 용이하도록 JSON 직렬화를 적용하며,
     * 데이터 정합성(Stale Data) 문제를 최소화하기 위해 기본 TTL을 1분으로 제한합니다.
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {

        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(1))
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())) // Key : 일반 문자열로
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer())); // V : JSON

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }

    /**
     * 범용 문자열 기반 Redis 템플릿
     * JWT Refresh Token 저장 및 대기열(Waiting Queue) 관리 등
     * 명시적인 TTL과 세밀한 키 제어가 필요한 로직에서 직접 주입받아 사용합니다.
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory redisConnectionFactory){
        return new StringRedisTemplate(redisConnectionFactory);
    }
}