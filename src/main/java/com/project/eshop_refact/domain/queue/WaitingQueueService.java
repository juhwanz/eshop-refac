package com.project.eshop_refact.domain.queue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Service;

import java.util.Set;


/**
 * 대기열 트래픽 제어 서비스
 * Redis를 활용하여 트래픽 병목을 제어하고 사용자 진입 순서를 관리합니다.
 */
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
        // Unix Timestamp를 Score로 사용하여 대기열의 FIFO(First-In-First-Out) 정렬을 보장합니다.
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

    /**
     * 대기열 사용자 진입 허용 처리
     * 대규모 트래픽 환경에서 애플리케이션 메모리 부하(OOM)를 방지하기 위해 Chunk 단위로 분할 처리하며,
     * Redis 파이프라인(Pipelining)을 적용하여 다중 명령어로 인한 네트워크 RTT를 최소화합니다.
     */
    public void allowUsers(long count) {
        long processed = 0;

        while(processed < count){
            long fetchCount = Math.min(CHUNK_SIZE, count - processed);
            Set<String> users = redisTemplate.opsForZSet().range(WAITING_KEY, 0 , fetchCount - 1);

            if(users == null || users.isEmpty()){
                break;
            }

            redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                RedisSerializer<String> stringSerializer = redisTemplate.getStringSerializer();
                byte[] keyWaiting = stringSerializer.serialize(WAITING_KEY);
                byte[] valueTrue = stringSerializer.serialize("true");

                for(String userId : users){
                    byte[] keyActive = stringSerializer.serialize(ACTIVE_KEY_PREFIX + userId);
                    byte[] valueId = stringSerializer.serialize(userId);
                    connection.setEx(keyActive, ACTIVE_TTL_SECONDS, valueTrue);
                    connection.zRem(keyWaiting, valueId);
                }
                return null;
            });

            processed += users.size();
            log.info("Processed chunk: {} users allowed. (Total processed: {}/{})", users.size(), processed, count);
        }
    }

    // Interceptor에서 매 API 요청마다 호출되므로, 시스템 부하를 막기 위해 O(1) 시간 복잡도의 단일 키 조회 방식을 사용합니다.
    public boolean isAllowed(Long userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(ACTIVE_KEY_PREFIX + userId));
    }

    public void removeUser(Long userId) {
        redisTemplate.delete(ACTIVE_KEY_PREFIX + userId);
    }
}