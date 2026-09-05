package com.project.eshop_refact.integration;

import com.project.eshop_refact.integration.support.MariaDbRedisIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TestcontainersInfrastructureIntegrationTest extends MariaDbRedisIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @Test
    @DisplayName("MariaDB schema와 Redis 연결을 컨테이너 환경에서 검증한다")
    void verifiesMariaDbSchemaAndRedisConnection() throws Exception {
        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("MariaDB");
        }

        Set<String> tables = Set.copyOf(jdbcTemplate.queryForList(
                "select table_name from information_schema.tables where table_schema = database()",
                String.class
        ));
        assertThat(tables).contains("users", "products", "orders", "order_item");

        Long foreignKeyCount = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.referential_constraints where constraint_schema = database()",
                Long.class
        );
        assertThat(foreignKeyCount).isNotNull().isGreaterThanOrEqualTo(3L);

        try (var connection = redisConnectionFactory.getConnection()) {
            assertThat(connection.ping()).isEqualTo("PONG");
        }
    }
}
