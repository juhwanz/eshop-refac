package com.project.eshop_refact.domain.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.eshop_refact.global.exception.BusinessException;
import com.project.eshop_refact.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class OrderIdempotencyService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final RedissonLockStockFacade redissonLockStockFacade;

    public OrderDto.CreateResponse processOrderWithIdempotency(String idempotencyKey, Long userId, Long productId, int count) throws Exception {
        String redisKey = "idempotency:order:" + userId + ":" + idempotencyKey;

        // 1. PROCESSING 상태로 키 등록 시도 (setIfAbsent를 통한 동시성 완벽 방어)
        Boolean isNewRequest = redisTemplate.opsForValue()
                .setIfAbsent(redisKey, "PROCESSING", 3, TimeUnit.MINUTES);

        // 2. 이미 존재하는 키인 경우 (중복 요청)
        if (Boolean.FALSE.equals(isNewRequest)) {
            String currentState = redisTemplate.opsForValue().get(redisKey);
            if ("PROCESSING".equals(currentState)) {
                throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE);
            }
            // 이미 SUCCESS인 경우 기존 응답(JSON) 파싱 후 반환
            return objectMapper.readValue(currentState, OrderDto.CreateResponse.class);
        }

        try {
            // 3. 실제 주문 로직 실행 (분산 락 파사드 호출)
            Long orderId = redissonLockStockFacade.order(userId, productId, count);

            // 4. 성공 시: 상태를 결과 데이터로 업데이트 후 TTL 24시간 연장
            OrderDto.CreateResponse response = new OrderDto.CreateResponse(orderId);
            String responseJson = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(redisKey, responseJson, 24, TimeUnit.HOURS);

            return response;

        } catch (Exception e) {
            // 5. 실패 시: 클라이언트의 재시도를 위해 멱등성 키 삭제
            redisTemplate.delete(redisKey);
            throw e;
        }
    }
}
