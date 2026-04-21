package com.project.eshop_refact;

import com.project.eshop_refact.domain.order.OrderDto;
import com.project.eshop_refact.domain.order.OrderIdempotencyService;
import com.project.eshop_refact.domain.product.Product;
import com.project.eshop_refact.domain.product.ProductRepository;
import com.project.eshop_refact.domain.user.User;
import com.project.eshop_refact.domain.user.UserRepository;
import com.project.eshop_refact.domain.user.UserRoleEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

/**
 * 주문 생성 멱등성(Idempotency) 통합 테스트
 * Redis를 활용하여 동일한 멱등성 키(Idempotency-Key)를 가진 중복 요청이
 * 중복으로 처리되지 않고 1회만 안전하게 처리됨을 검증합니다.
 */
@SpringBootTest
public class OrderIdempotencyTest {

    @Autowired
    private OrderIdempotencyService orderIdempotencyService;
    @Autowired private RedisTemplate<String, String> redisTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;

    @BeforeEach
    void setup(){
        // 독립적인 테스트 환경 구성을 위한 Redis 초기화
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("멱등성 검증: 동일한 멱등성 키로 중복 요청 시 새로운 주문을 생성하지 않고 기존 응답을 반환한다")
    void idempotencyKey_prevents_duplicate_orders() throws Exception {
        User user = userRepository.save(new User("test@test.com", "1234", "tester", UserRoleEnum.USER));
        Product product = productRepository.save(new Product("테스트상품", 10000, 100));

        // Given
        String idempotencyKey = UUID.randomUUID().toString();
        Long userId = user.getId();
        Long productId = product.getId();
        int count = 1;

        // When: 최초 주문 요청 수행
        OrderDto.CreateResponse firstResponse = orderIdempotencyService.processOrderWithIdempotency(idempotencyKey, userId, productId, count);

        // When: 동일한 멱등성 키를 사용한 중복 주문 요청 수행
        OrderDto.CreateResponse secondResponse = orderIdempotencyService.processOrderWithIdempotency(idempotencyKey, userId, productId, count);

        // Then: 중복 요청 시 주문이 추가로 생성되지 않고, 최초 요청의 응답 결과와 동일함을 검증
        assertThat(firstResponse.getOrderId()).isEqualTo(secondResponse.getOrderId());

        // Redis 내부의 멱등성 키 저장 상태 검증
        String redisKey = "idempotency:order:" + userId + ":" + idempotencyKey;
        assertThat(redisTemplate.hasKey(redisKey)).isTrue();
    }
}
