package com.project.eshop_refact.service.queue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * [Traffic Throttling Service]
 * Architecture: Dual-Key Strategy (ZSet for Order, Set for Access)
 * - Waiting Queue (ZSet): 시간순 정렬(FIFO) 및 대기 순번 조회 (O(logN))
 * - Active Queue (Set): 진입 허용 유저의 고속 조회 (O(1))를 통한 API Latency 최소화
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WaitingQueueService {

    private final StringRedisTemplate redisTemplate;

    private static final String WAITING_KEY = "waiting_queue";
    private static final String ACTIVE_KEY = "active_queue";

    private static final long CHUNK_SIZE = 1000L;

    public Long registerQueue(Long userId){
        long unixTimestamp = System.currentTimeMillis();
        // Scoring: Unix Timestamp를 Score로 사용하여 입도 높은 FIFO 정렬 보장
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

        // Heap Memory Safety: 대량의 유저 처리 시 OOM 방지를 위해 Chunk 단위 처리
        while(processed < count){
            long fetchCount = Math.min(CHUNK_SIZE, count - processed);
            Set<String> users = redisTemplate.opsForZSet().range(WAITING_KEY, 0 , fetchCount - 1);

            if(users == null || users.isEmpty()){
                break;
            }

            // Pipeline: 다수의 Redis 명령(ZRem, SAdd)을 단일 네트워크 패킷으로 전송하여 RTT 최소화
            redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                RedisSerializer<String> stringSerializer = redisTemplate.getStringSerializer();
                byte[] keyActive = stringSerializer.serialize(ACTIVE_KEY);
                byte[] keyWaiting = stringSerializer.serialize(WAITING_KEY);

                for(String userId : users){
                    byte[] valueId = stringSerializer.serialize(userId);
                    // ACtive 큐에 등록
                    connection.sAdd(keyActive, valueId);
                    // Waiting 큐에서 제거
                    connection.zRem(keyWaiting, valueId);
                }
                return null;
            });

            processed += users.size();
            log.info("Processed chunk: {} users allowed. (Total processed: {}/{})", users.size(), processed, count);
        }
    }

    //Interceptor에서 매 요청마다 호출되므로 O(1) 복잡도 필수
    public boolean isAllowed(Long userId) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(ACTIVE_KEY, userId.toString()));
    }

    public void removeUser(Long userId) {
        redisTemplate.opsForSet().remove(ACTIVE_KEY, userId.toString());
    }
}

