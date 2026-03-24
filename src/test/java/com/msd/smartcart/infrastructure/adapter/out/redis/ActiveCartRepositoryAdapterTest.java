package com.msd.smartcart.infrastructure.adapter.out.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msd.smartcart.domain.model.Cart;
import com.msd.smartcart.domain.model.CartItem;
import com.msd.smartcart.shared.exception.InfrastructureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActiveCartRepositoryAdapterTest {

    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ObjectMapper objectMapper;
    @Mock private ValueOperations<String, String> valueOps;

    private ActiveCartRepositoryAdapter adapter;

    private static final String KEY_PREFIX = "cart:active:";

    @BeforeEach
    void setUp() {
        adapter = new ActiveCartRepositoryAdapter(redisTemplate, objectMapper);
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private Cart cart() {
        return Cart.createFor("user-123")
                .add(new CartItem("prod-001", "Laptop", 1, new BigDecimal("1299.99")));
    }

    // =========================================================================
    // save — happy path
    // =========================================================================

    @Test
    void should_serializeAndSaveToRedis_when_saveCalledWithValidCart() throws JsonProcessingException {
        when(objectMapper.writeValueAsString(any(Cart.class))).thenReturn("{\"cartId\":\"abc\"}");
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        adapter.save(cart());

        verify(valueOps).set(eq(KEY_PREFIX + "user-123"), eq("{\"cartId\":\"abc\"}"), any());
    }

    // =========================================================================
    // save — fallos de infraestructura
    // =========================================================================

    @Test
    void should_throwInfrastructureException_when_serializationFails() throws JsonProcessingException {
        when(objectMapper.writeValueAsString(any())).thenThrow(mock(JsonProcessingException.class));

        assertThatThrownBy(() -> adapter.save(cart()))
                .isInstanceOf(InfrastructureException.class)
                .hasMessageContaining("cart.serialization.failed");
    }

    @Test
    void should_throwInfrastructureException_when_redisConnectionFailsOnSave() throws JsonProcessingException {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        doThrow(new RedisConnectionFailureException("Connection refused"))
                .when(valueOps).set(any(), any(), any());

        assertThatThrownBy(() -> adapter.save(cart()))
                .isInstanceOf(InfrastructureException.class)
                .hasMessageContaining("cart.redis.unavailable");
    }

    @Test
    void should_throwInfrastructureException_when_unexpectedErrorOnSave() throws JsonProcessingException {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        doThrow(new RuntimeException("Unexpected Redis error"))
                .when(valueOps).set(any(), any(), any());

        assertThatThrownBy(() -> adapter.save(cart()))
                .isInstanceOf(InfrastructureException.class)
                .hasMessageContaining("cart.redis.save.failed");
    }

    // =========================================================================
    // findByUserId — happy path
    // =========================================================================

    @Test
    void should_returnEmpty_when_keyNotFoundInRedis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(KEY_PREFIX + "user-123")).thenReturn(null);

        Optional<Cart> result = adapter.findByUserId("user-123");

        assertThat(result).isEmpty();
    }

    @Test
    void should_returnDeserializedCart_when_keyExistsInRedis() throws JsonProcessingException {
        Cart expected = cart();
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(KEY_PREFIX + "user-123")).thenReturn("{\"cartId\":\"abc\"}");
        when(objectMapper.readValue("{\"cartId\":\"abc\"}", Cart.class)).thenReturn(expected);

        Optional<Cart> result = adapter.findByUserId("user-123");

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(expected);
    }

    // =========================================================================
    // findByUserId — fallos de infraestructura
    // =========================================================================

    @Test
    void should_throwInfrastructureException_when_deserializationFails() throws JsonProcessingException {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(KEY_PREFIX + "user-123")).thenReturn("{corrupted}");
        when(objectMapper.readValue(anyString(), eq(Cart.class))).thenThrow(mock(JsonProcessingException.class));

        assertThatThrownBy(() -> adapter.findByUserId("user-123"))
                .isInstanceOf(InfrastructureException.class)
                .hasMessageContaining("cart.deserialization.failed");
    }

    @Test
    void should_throwInfrastructureException_when_redisConnectionFailsOnFind() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(any())).thenThrow(new RedisConnectionFailureException("Connection refused"));

        assertThatThrownBy(() -> adapter.findByUserId("user-123"))
                .isInstanceOf(InfrastructureException.class)
                .hasMessageContaining("cart.redis.unavailable");
    }

    @Test
    void should_throwInfrastructureException_when_unexpectedErrorOnFind() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(any())).thenThrow(new RuntimeException("Unexpected error"));

        assertThatThrownBy(() -> adapter.findByUserId("user-123"))
                .isInstanceOf(InfrastructureException.class)
                .hasMessageContaining("cart.redis.find.failed");
    }

    // =========================================================================
    // deleteByUserId — happy path
    // =========================================================================

    @Test
    void should_deleteKeyFromRedis_when_deleteByUserIdCalled() {
        adapter.deleteByUserId("user-123");

        verify(redisTemplate).delete(KEY_PREFIX + "user-123");
    }

    // =========================================================================
    // deleteByUserId — fallos de infraestructura
    // =========================================================================

    @Test
    void should_throwInfrastructureException_when_redisConnectionFailsOnDelete() {
        when(redisTemplate.delete(anyString()))
                .thenThrow(new RedisConnectionFailureException("Connection refused"));

        assertThatThrownBy(() -> adapter.deleteByUserId("user-123"))
                .isInstanceOf(InfrastructureException.class)
                .hasMessageContaining("cart.redis.unavailable");
    }

    @Test
    void should_throwInfrastructureException_when_unexpectedErrorOnDelete() {
        when(redisTemplate.delete(anyString()))
                .thenThrow(new RuntimeException("Unexpected error"));

        assertThatThrownBy(() -> adapter.deleteByUserId("user-123"))
                .isInstanceOf(InfrastructureException.class)
                .hasMessageContaining("cart.redis.delete.failed");
    }

    // =========================================================================
    // buildKey — contrato del prefijo
    // =========================================================================

    @Test
    void should_useCorrectKeyPrefix_when_savingCart() throws JsonProcessingException {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        adapter.save(cart());

        verify(valueOps).set(startsWith(KEY_PREFIX), any(), any());
    }
}