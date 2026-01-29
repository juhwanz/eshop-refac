package com.project.eshop_refact.service.queue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

// ZSet(Sorted Set) 이용 : ZADD로 넣을 시 시간을 점수로. -> 자동으로 시간 순 정렬
// -> ZRANK하나로 빠르게 앞에 몇명있는지 알아냄.
// Set(Active Queue) 분리 : "입장 가능한가?"를 매번 무거운 ZSet에서 찾지 않고, 가벼운 Set(SISMEMBER)에서 **O(1)**로 확인합니다.
//
//"트래픽 필터링 성능
@Slf4j
@Service
@RequiredArgsConstructor
public class WaitingQueueService {

    private final StringRedisTemplate redisTemplate;

    private static final String WAITING_KEY = "waiting_queue";
    private static final String ACTIVE_KEY = "active_queue";

    public Long registerQueue(Long userId){
        long unixTimestamp = System.currentTimeMillis();
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

    public void allowUsers(long count) {
        Set<String> users = redisTemplate.opsForZSet().range(WAITING_KEY, 0, count - 1);

        if (users == null || users.isEmpty()) {
            return;
        }


        for (String userId : users) {
            redisTemplate.opsForSet().add(ACTIVE_KEY, userId);
            redisTemplate.opsForZSet().remove(WAITING_KEY, userId);
        }

        log.info("Moved {} users from Waiting to Active queue.", users.size());
    }

    public boolean isAllowed(Long userId) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(ACTIVE_KEY, userId.toString()));
    }

    public void removeUser(Long userId) {
        redisTemplate.opsForSet().remove(ACTIVE_KEY, userId.toString());
    }
}

