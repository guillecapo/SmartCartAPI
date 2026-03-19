package com.msd.smartcart.infrastructure.adapter.out.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msd.smartcart.domain.model.Cart;
import com.msd.smartcart.domain.port.out.ActiveCartRepository;
import com.msd.smartcart.shared.annotation.PersistenceAdapter;
import com.msd.smartcart.shared.exception.InfrastructureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
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
            // Error de serialización — el modelo cambió o tiene campos no serializables
            log.error("Failed to serialize cart [userId={}] — {}", cart.userId(), e.getMessage(), e);
            throw new InfrastructureException("cart.serialization.failed"); // ✅ contrato cumplido
        } catch (RedisConnectionFailureException e) {
            // Redis no disponible
            log.error("Redis unavailable on save [userId={}] — {}", cart.userId(), e.getMessage(), e);
            throw new InfrastructureException("cart.redis.unavailable"); // ✅ contrato cumplido
        } catch (Exception e) {
            // Cualquier otra falla de infraestructura — nunca llega cruda al servicio
            log.error("Unexpected Redis failure on save [userId={}] — {}", cart.userId(), e.getMessage(), e);
            throw new InfrastructureException("cart.redis.save.failed"); // ✅ contrato cumplido
        }
    }

    @Override
    public Optional<Cart> findByUserId(String userId) {
        String key = buildKey(userId);
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, Cart.class));
        } catch (JsonProcessingException e) {
            // Dato corrupto en Redis — mejor tratar como ausente que explotar
            log.error("Failed to deserialize cart [userId={}] — {}", userId, e.getMessage(), e);
            throw new InfrastructureException("cart.deserialization.failed"); // ✅ contrato cumplido
        } catch (RedisConnectionFailureException e) {
            log.error("Redis unavailable on find [userId={}] — {}", userId, e.getMessage(), e);
            throw new InfrastructureException("cart.redis.unavailable"); // ✅ contrato cumplido
        } catch (Exception e) {
            log.error("Unexpected Redis failure on find [userId={}] — {}", userId, e.getMessage(), e);
            throw new InfrastructureException("cart.redis.find.failed"); // ✅ contrato cumplido
        }
    }

    @Override
    public void deleteByUserId(String userId) {
        String key = buildKey(userId);
        try {
            redisTemplate.delete(key);
            log.debug("Cart deleted from Redis [userId={}]", userId);
        } catch (RedisConnectionFailureException e) {
            log.error("Redis unavailable on delete [userId={}] — {}", userId, e.getMessage(), e);
            throw new InfrastructureException("cart.redis.unavailable"); // ✅ contrato cumplido
        } catch (Exception e) {
            log.error("Unexpected Redis failure on delete [userId={}] — {}", userId, e.getMessage(), e);
            throw new InfrastructureException("cart.redis.delete.failed"); // ✅ contrato cumplido
        }
    }

    private String buildKey(String userId) {
        return KEY_PREFIX + userId;
    }
}