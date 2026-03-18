package com.msd.smartcart.infrastructure.adapter.out.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msd.smartcart.domain.model.Cart;
import com.msd.smartcart.domain.port.out.ActiveCartRepository;
import com.msd.smartcart.shared.annotation.PersistenceAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@PersistenceAdapter
@RequiredArgsConstructor
public class ActiveCartRepositoryAdapter implements ActiveCartRepository {

    private static final String KEY_PREFIX = "cart:active:";
    private static final Duration TTL = Duration.ofHours(24);

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void save(Cart cart) {
        String key = buildKey(cart.userId());
        try {
            String json = objectMapper.writeValueAsString(cart);
            redisTemplate.opsForValue().set(key, json, TTL);
            log.debug("Cart saved to Redis [userId={}, cartId={}]", cart.userId(), cart.cartId());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize cart [userId={}] — {}", cart.userId(), e.getMessage(), e);
            throw new RuntimeException("Cart serialization failed", e);
        }
    }

    @Override
    public Optional<Cart> findByUserId(String userId) {
        String key = buildKey(userId);
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, Cart.class));
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize cart [userId={}] — {}", userId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    @Override
    public void deleteByUserId(String userId) {
        String key = buildKey(userId);
        redisTemplate.delete(key);
        log.debug("Cart deleted from Redis [userId={}]", userId);
    }

    private String buildKey(String userId) {
        return KEY_PREFIX + userId;
    }
}