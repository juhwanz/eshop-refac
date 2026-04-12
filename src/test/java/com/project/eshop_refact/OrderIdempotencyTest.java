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

@SpringBootTest
public class OrderIdempotencyTest {

    @Autowired
    private OrderIdempotencyService orderIdempotencyService;
    @Autowired private RedisTemplate<String, String> redisTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;

    @BeforeEach
    void setup(){
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("멱등성 키가 같으면 동일한 요청을 여러 번 보내도 주문은 1건만 처리된다.")
    void idempotencyKey_prevents_duplicate_orders() throws Exception {
        User user = userRepository.save(new User("test@test.com", "1234", "tester", UserRoleEnum.USER));
        Product product = productRepository.save(new Product("테스트상품", 10000, 100));

        // Given
        String idempotencyKey = UUID.randomUUID().toString();
        Long userId = user.getId();
        Long productId = product.getId();
        int count = 1;

        // When: 첫 번째 정상 요청 (실제 주문 생성 로직)
        OrderDto.CreateResponse firstResponse = orderIdempotencyService.processOrderWithIdempotency(idempotencyKey, userId, productId, count);

        // When: 두 번째 중복 요청 (비즈니스 로직 타지 않고 Redis에서 바로 캐시된 응답 반환)
        OrderDto.CreateResponse secondResponse = orderIdempotencyService.processOrderWithIdempotency(idempotencyKey, userId, productId, count);

        // Then: 두 응답의 주문 ID가 완벽히 동일해야 함 (새로운 주문이 생성되지 않음)
        assertThat(firstResponse.getOrderId()).isEqualTo(secondResponse.getOrderId());

        // Redis에 저장된 키 확인
        String redisKey = "idempotency:order:" + userId + ":" + idempotencyKey;
        assertThat(redisTemplate.hasKey(redisKey)).isTrue();
    }
}
