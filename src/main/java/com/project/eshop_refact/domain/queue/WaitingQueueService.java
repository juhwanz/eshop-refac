package com.project.eshop_refact.domain.queue;

import com.project.eshop_refact.domain.product.ProductRepository;
import com.project.eshop_refact.global.exception.BusinessException;
import com.project.eshop_refact.global.exception.ErrorCode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class WaitingQueueService {

    private static final String WAITING_PRODUCTS_KEY = "queue:waiting-products";
    private static final DefaultRedisScript<List> REGISTER_SCRIPT = script("redis/queue-register.lua");
    private static final DefaultRedisScript<List> STATUS_SCRIPT = script("redis/queue-status.lua");
    private static final DefaultRedisScript<List> PROMOTE_SCRIPT = script("redis/queue-promote.lua");

    private final StringRedisTemplate redisTemplate;
    private final ProductRepository productRepository;
    private final QueueProperties properties;

    public WaitingQueueService(
            StringRedisTemplate redisTemplate,
            ProductRepository productRepository,
            QueueProperties properties
    ) {
        this.redisTemplate = redisTemplate;
        this.productRepository = productRepository;
        this.properties = properties;
    }

    public Registration register(Long productId, Long userId) {
        validateProduct(productId);
        List<Long> result = execute(
                REGISTER_SCRIPT,
                List.of(waitingKey(productId), sequenceKey(productId), activeKey(productId), WAITING_PRODUCTS_KEY),
                userId.toString(),
                productId.toString()
        );
        return new Registration(toResponse(result), result.get(2) == 1L);
    }

    public QueueDto.Response getStatus(Long productId, Long userId) {
        validateProduct(productId);
        return getStatusWithoutProductLookup(productId, userId);
    }

    public boolean isAllowed(Long userId, Long productId) {
        return getStatusWithoutProductLookup(productId, userId).getStatus() == QueueStatus.ACTIVE;
    }

    public void removeUser(Long userId, Long productId) {
        redisTemplate.opsForZSet().remove(activeKey(productId), userId.toString());
    }

    public long allowWaitingProducts() {
        Set<String> productIds = redisTemplate.opsForZSet()
                .range(WAITING_PRODUCTS_KEY, 0, properties.getProductScanLimit() - 1);
        if (productIds == null || productIds.isEmpty()) {
            return 0;
        }

        long promoted = 0;
        for (String productIdValue : productIds) {
            Long productId = Long.valueOf(productIdValue);
            List<Long> result = execute(
                    PROMOTE_SCRIPT,
                    List.of(waitingKey(productId), activeKey(productId), WAITING_PRODUCTS_KEY),
                    productIdValue,
                    Long.toString(properties.getPromotionSizePerProduct()),
                    Long.toString(properties.getActiveTtl().toMillis())
            );
            promoted += result.getFirst();
        }
        return promoted;
    }

    private QueueDto.Response getStatusWithoutProductLookup(Long productId, Long userId) {
        List<Long> result = execute(
                STATUS_SCRIPT,
                List.of(waitingKey(productId), activeKey(productId)),
                userId.toString()
        );
        return toResponse(result);
    }

    private QueueDto.Response toResponse(List<Long> result) {
        QueueStatus status = switch (result.getFirst().intValue()) {
            case 1 -> QueueStatus.WAITING;
            case 2 -> QueueStatus.ACTIVE;
            default -> QueueStatus.NOT_REGISTERED;
        };
        Long rank = status == QueueStatus.WAITING ? result.get(1) : null;
        return new QueueDto.Response(status, rank);
    }

    @SuppressWarnings("unchecked")
    private List<Long> execute(DefaultRedisScript<List> script, List<String> keys, String... arguments) {
        List<Long> result = redisTemplate.execute(script, keys, (Object[]) arguments);
        if (result == null) {
            throw new IllegalStateException("Redis 대기열 스크립트 결과가 없습니다.");
        }
        return result;
    }

    private void validateProduct(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
    }

    private static String waitingKey(Long productId) {
        return "queue:{" + productId + "}:waiting";
    }

    private static String sequenceKey(Long productId) {
        return "queue:{" + productId + "}:sequence";
    }

    private static String activeKey(Long productId) {
        return "queue:{" + productId + "}:active";
    }

    private static DefaultRedisScript<List> script(String path) {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(List.class);
        return script;
    }

    public record Registration(QueueDto.Response response, boolean created) {
    }
}
