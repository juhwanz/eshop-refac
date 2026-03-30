package com.project.eshop_refact.domain.queue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Service;

import java.util.Set;


// [Traffic Throttling Service]
@Slf4j
@Service
@RequiredArgsConstructor
public class WaitingQueueService {

    private final StringRedisTemplate redisTemplate;

    private static final String WAITING_KEY = "waiting_queue";
    private static final String ACTIVE_KEY_PREFIX = "active_user:";

    private static final long CHUNK_SIZE = 1000L;
    private static final long ACTIVE_TTL_SECONDS = 600L;

    public Long registerQueue(Long userId){
        long unixTimestamp = System.currentTimeMillis();
        // Scoring: Unix Timestamp를 Score로 사용하여 FIFO 정렬 보장
        redisTemplate.opsForZSet().add(WAITING_KEY, userId.toString(), unixTimestamp);

        return getRank(userId);
    }

    public Long getRank(Long userId){
        Long rank = redisTemplate.opsForZSet().rank(WAITING_KEY, userId.toString());
        if(rank == null){
            return -1L;
        }
        return rank + 1;
    }

    // Optimization: Bulk Operation 성능 최적화 (Network RTT & Memory Safety)
    public void allowUsers(long count) {
        long processed = 0;

        // Heap Memory Safety:  OOM 방지를 위해 Chunk 단위 처리
        while(processed < count){
            long fetchCount = Math.min(CHUNK_SIZE, count - processed);
            Set<String> users = redisTemplate.opsForZSet().range(WAITING_KEY, 0 , fetchCount - 1);

            if(users == null || users.isEmpty()){
                break;
            }

            // Pipeline: 다수의 Redis 명령(ZRem, SetEx)을 단일 네트워크 패킷으로 전송하여 RTT 최소화
            redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                RedisSerializer<String> stringSerializer = redisTemplate.getStringSerializer();
                byte[] keyWaiting = stringSerializer.serialize(WAITING_KEY);
                byte[] valueTrue = stringSerializer.serialize("true");

                for(String userId : users){
                    byte[] keyActive = stringSerializer.serialize(ACTIVE_KEY_PREFIX + userId);
                    byte[] valueId = stringSerializer.serialize(userId);
                    // 1. 개별 유저 키에 TTL 600초(10분) 설정하여 발급
                    connection.setEx(keyActive, ACTIVE_TTL_SECONDS, valueTrue);
                    // 2. 대기열에서 제거
                    connection.zRem(keyWaiting, valueId);
                }
                return null;
            });

            processed += users.size();
            log.info("Processed chunk: {} users allowed. (Total processed: {}/{})", users.size(), processed, count);
        }
    }

    // Interceptor에서 매 요청마다 호출되므로 O(1) 복잡도 필수
    // 수정: Set 조회가 아닌 개별 키 존재 여부(hasKey)로 확인
    public boolean isAllowed(Long userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(ACTIVE_KEY_PREFIX + userId));
    }

    // 수정: Set에서 제거가 아닌 개별 키 삭제(delete)로 처리
    public void removeUser(Long userId) {
        redisTemplate.delete(ACTIVE_KEY_PREFIX + userId);
    }
}