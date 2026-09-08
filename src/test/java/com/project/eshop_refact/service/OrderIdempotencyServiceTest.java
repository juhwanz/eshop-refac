package com.project.eshop_refact.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.eshop_refact.domain.order.OrderIdempotencyService;
import com.project.eshop_refact.domain.order.OrderRequestIdentity;
import com.project.eshop_refact.domain.order.OrderService;
import com.project.eshop_refact.domain.order.RedissonLockStockFacade;
import com.project.eshop_refact.global.exception.BusinessException;
import com.project.eshop_refact.global.exception.ErrorCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderIdempotencyServiceTest {
    @Mock RedisTemplate<String, String> redis;
    @Mock ValueOperations<String, String> values;
    @Mock RedissonLockStockFacade facade;
    @Mock OrderService orders;
    OrderIdempotencyService service;
    OrderRequestIdentity identity = OrderRequestIdentity.of("key", 2L, 1);

    @BeforeEach
    void prepare() {
        service = new OrderIdempotencyService(redis, new ObjectMapper(), facade, new SimpleMeterRegistry(), orders);
    }

    @Test
    void invalidKeysNeverReachStorage() {
        for (String key : new String[]{"", " ", "key ", "한글", "a".repeat(129)}) {
            assertThatThrownBy(() -> service.processOrderWithIdempotency(key, 1L, 2L, 1))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
        }
        verifyNoInteractions(redis, orders, facade);
    }

    @Test
    void redisReadFailureRecoversCommittedOrder() {
        when(redis.opsForValue()).thenThrow(new IllegalStateException("Redis unavailable"));
        when(orders.findCompletedOrder(1L, identity)).thenReturn(Optional.of(3L));
        assertThat(service.processOrderWithIdempotency("key", 1L, 2L, 1).getOrderId()).isEqualTo(3L);
        verifyNoInteractions(facade);
    }

    @Test
    void legacyResponseRequiresDatabaseVerification() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn("{\"orderId\":999}");
        when(orders.findCompletedOrder(1L, identity)).thenReturn(Optional.of(3L));
        assertThat(service.processOrderWithIdempotency("key", 1L, 2L, 1).getOrderId()).isEqualTo(3L);
        verifyNoInteractions(facade);
    }

    @Test
    void unrelatedIntegrityFailureIsNotConvertedIntoSuccess() {
        when(redis.opsForValue()).thenReturn(values);
        when(orders.findCompletedOrder(1L, identity)).thenReturn(Optional.empty());
        when(values.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class))).thenReturn(false);
        var failure = new DataIntegrityViolationException("different constraint");
        when(facade.order(1L, 2L, 1, identity)).thenThrow(failure);
        assertThatThrownBy(() -> service.processOrderWithIdempotency("key", 1L, 2L, 1)).isSameAs(failure);
    }
}
