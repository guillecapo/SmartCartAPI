package com.msd.smartcart.infrastructure.adapter.in.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyStore {

    private static final String KEY_PREFIX = "idempotency:";
    private static final Duration TTL = Duration.ofHours(24);

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public <T> T get(String key, Class<T> type) {
        String json = redisTemplate.opsForValue().get(KEY_PREFIX + key);
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("Failed to deserialize idempotency cache [key={}]", key);
            return null;
        }
    }

    public void save(String key, Object value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(KEY_PREFIX + key, json, TTL);
        } catch (Exception e) {
            log.warn("Failed to save idempotency cache [key={}]", key);
        }
    }
}