package com.msd.smartcart.infrastructure.adapter.in.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyStoreTest {

    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ObjectMapper objectMapper;
    @Mock private ValueOperations<String, String> valueOps;

    private IdempotencyStore store;

    private static final String KEY_PREFIX = "idempotency:";

    @BeforeEach
    void setUp() {
        store = new IdempotencyStore(redisTemplate, objectMapper);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // =========================================================================
    // get — ramas
    // =========================================================================

    @Test
    void should_returnNull_when_keyNotFoundInRedis() {
        when(valueOps.get(KEY_PREFIX + "key-1")).thenReturn(null);

        String result = store.get("key-1", String.class);

        assertThat(result).isNull();
    }

    @Test
    void should_returnDeserializedValue_when_keyExistsInRedis() throws Exception {
        when(valueOps.get(KEY_PREFIX + "key-1")).thenReturn("{\"id\":\"abc\"}");
        when(objectMapper.readValue("{\"id\":\"abc\"}", String.class)).thenReturn("abc");

        String result = store.get("key-1", String.class);

        assertThat(result).isEqualTo("abc");
    }

    @Test
    void should_returnNull_when_deserializationFails() throws Exception {
        when(valueOps.get(KEY_PREFIX + "key-1")).thenReturn("{corrupted}");
        when(objectMapper.readValue(anyString(), eq(String.class)))
                .thenThrow(mock(JsonProcessingException.class));

        String result = store.get("key-1", String.class);

        // Fallo silencioso — devuelve null para forzar re-ejecución
        assertThat(result).isNull();
    }

    // =========================================================================
    // save — ramas
    // =========================================================================

    @Test
    void should_serializeAndStoreInRedis_when_saveCalledWithValidValue() throws Exception {
        doReturn("{\"id\":\"abc\"}").when(objectMapper).writeValueAsString(any());

        store.save("key-1", "value");

        verify(valueOps).set(eq(KEY_PREFIX + "key-1"), eq("{\"id\":\"abc\"}"), any());
    }

    @Test
    @MockitoSettings(strictness = Strictness.LENIENT)
    void should_doNothingAndNotThrow_when_serializationFailsOnSave() throws Exception {
        doThrow(mock(JsonProcessingException.class)).when(objectMapper).writeValueAsString(any());

        // Fallo silencioso — best-effort, no debe propagar excepción
        store.save("key-1", "value");

        verify(valueOps, never()).set(any(), any(), any());
    }

    // =========================================================================
    // prefijo de clave
    // =========================================================================

    @Test
    void should_prependIdempotencyPrefix_when_accessing() {
        when(valueOps.get(KEY_PREFIX + "my-key")).thenReturn(null);

        store.get("my-key", String.class);

        verify(valueOps).get(KEY_PREFIX + "my-key");
    }
}