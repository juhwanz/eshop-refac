package com.project.eshop_refact.domain.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.eshop_refact.global.exception.BusinessException;
import com.project.eshop_refact.global.exception.ErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 주문 멱등성 보장 서비스
 * Redis를 활용하여 네트워크 지연이나 클라이언트 재시도로 인한 중복 주문을 방지합니다.
 */
@Service
@RequiredArgsConstructor
public class OrderIdempotencyService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final RedissonLockStockFacade redissonLockStockFacade;

    private final MeterRegistry meterRegistry;

    public OrderDto.CreateResponse processOrderWithIdempotency(String idempotencyKey, Long userId, Long productId, int count) throws Exception {
        String redisKey = "idempotency:order:" + userId + ":" + idempotencyKey;

        // 원자적 연산(setIfAbsent)을 통해 중복 요청의 동시 처리를 방지하고 상태를 점유합니다.
        Boolean isNewRequest = redisTemplate.opsForValue()
                .setIfAbsent(redisKey, "PROCESSING", 3, TimeUnit.MINUTES);

        // 이미 처리 중이거나 완료된 요청에 대한 방어 로직
        if (Boolean.FALSE.equals(isNewRequest)) {
            String currentState = redisTemplate.opsForValue().get(redisKey);
            if ("PROCESSING".equals(currentState)) {
                throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
            }
            // 처리가 완료된 요청은 기존 응답을 반환하여 멱등성을 유지합니다.
            return objectMapper.readValue(currentState, OrderDto.CreateResponse.class);
        }

        try {
            Long orderId = redissonLockStockFacade.order(userId, productId, count);

            // 주문 성공 시 처리 결과를 캐싱하고, TTL을 연장하여 일정 기간 멱등성을 보장합니다.
            OrderDto.CreateResponse response = new OrderDto.CreateResponse(orderId);
            String responseJson = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(redisKey, responseJson, 24, TimeUnit.HOURS);

            meterRegistry.counter("order.success.count").increment();

            return response;

        } catch (Exception e) {
            // 예외 발생 시 클라이언트가 안전하게 재시도할 수 있도록 멱등성 키를 삭제합니다.
            redisTemplate.delete(redisKey);

            if (e instanceof BusinessException) {
                meterRegistry.counter("order.fail.count", "reason", "business_error").increment();
            } else {
                meterRegistry.counter("order.fail.count", "reason", "system_error").increment();
            }
            throw e;
        }
    }
}
