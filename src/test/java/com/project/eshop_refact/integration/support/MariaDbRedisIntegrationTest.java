package com.project.eshop_refact.integration.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

import java.util.stream.Stream;

@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class MariaDbRedisIntegrationTest {

    private static final MariaDBContainer<?> MARIA_DB = new MariaDBContainer<>(
            DockerImageName.parse("mariadb:11.8.6")
    )
            .withDatabaseName("eshop_test")
            .withUsername("eshop")
            .withPassword("eshop");

    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.4.5-alpine")
    ).withExposedPorts(6379);

    static {
        try {
            Startables.deepStart(Stream.of(MARIA_DB, REDIS)).join();
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "통합 테스트용 MariaDB/Redis 컨테이너를 시작하지 못했습니다. Docker 실행 상태와 이미지 다운로드 가능 여부를 확인하세요.",
                    exception
            );
        }
    }

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @DynamicPropertySource
    protected static void registerContainerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MARIA_DB::getJdbcUrl);
        registry.add("spring.datasource.username", MARIA_DB::getUsername);
        registry.add("spring.datasource.password", MARIA_DB::getPassword);
        registry.add("spring.datasource.driver-class-name", MARIA_DB::getDriverClassName);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.docker.compose.enabled", () -> false);
    }

    @BeforeEach
    protected void clearSharedRedisState() {
        if (redisTemplate != null) {
            redisTemplate.execute((RedisCallback<Void>) connection -> {
                connection.serverCommands().flushAll();
                return null;
            });
        }
    }
}
