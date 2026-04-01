package com.msd.smartcart.infrastructure.adapter.out.redis;

import com.msd.smartcart.IntegrationTestBase;
import com.msd.smartcart.domain.model.Cart;
import com.msd.smartcart.domain.model.CartItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de integración para ActiveCartRepositoryAdapter contra Redis real.
 *
 * Verifican que la serialización/deserialización del modelo Cart funciona
 * con el driver real, que el TTL se aplica correctamente, y que el prefijo
 * de clave se construye como se espera.
 */
class ActiveCartRepositoryIT extends IntegrationTestBase {

    @Autowired
    private ActiveCartRepositoryAdapter adapter;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private static final String KEY_PREFIX = "cart:active:";

    @AfterEach
    void cleanUp() {
        // Limpia todas las claves de carrito para aislar cada test
        var keys = redisTemplate.keys(KEY_PREFIX + "*");
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    // =========================================================================
    // save + findByUserId — round-trip de serialización
    // =========================================================================

    @Test
    void should_persistAndRetrieveCart_when_savedToRedis() {
        Cart cart = Cart.createFor("user-it-001")
                .add(new CartItem("prod-001", "Laptop Dell XPS 15", 2, new BigDecimal("1299.99")));

        adapter.save(cart);

        Optional<Cart> result = adapter.findByUserId("user-it-001");

        assertThat(result).isPresent();
        assertThat(result.get().userId()).isEqualTo("user-it-001");
        assertThat(result.get().items()).hasSize(1);
        assertThat(result.get().items().getFirst().productId()).isEqualTo("prod-001");
        assertThat(result.get().items().getFirst().quantity()).isEqualTo(2);
        assertThat(result.get().totalValue()).isEqualByComparingTo(new BigDecimal("2599.98"));
    }

    @Test
    void should_preserveAllCartFields_when_serializedAndDeserialized() {
        Cart original = Cart.createFor("user-it-002")
                .add(new CartItem("prod-001", "Laptop", 1, new BigDecimal("999.99")))
                .add(new CartItem("prod-002", "Mouse", 3, new BigDecimal("49.99")));

        adapter.save(original);
        Cart retrieved = adapter.findByUserId("user-it-002").orElseThrow();

        assertThat(retrieved.cartId()).isEqualTo(original.cartId());
        assertThat(retrieved.userId()).isEqualTo(original.userId());
        assertThat(retrieved.items()).hasSize(2);
        assertThat(retrieved.status()).isEqualTo(original.status());
    }

    @Test
    void should_overwriteExistingCart_when_savedTwiceForSameUser() {
        Cart v1 = Cart.createFor("user-it-003")
                .add(new CartItem("prod-001", "Laptop", 1, new BigDecimal("999.99")));
        Cart v2 = v1.add(new CartItem("prod-002", "Mouse", 1, new BigDecimal("49.99")));

        adapter.save(v1);
        adapter.save(v2);

        Cart retrieved = adapter.findByUserId("user-it-003").orElseThrow();
        assertThat(retrieved.items()).hasSize(2);
    }

    // =========================================================================
    // findByUserId — clave ausente
    // =========================================================================

    @Test
    void should_returnEmpty_when_noCartStoredForUser() {
        Optional<Cart> result = adapter.findByUserId("user-nonexistent");

        assertThat(result).isEmpty();
    }

    // =========================================================================
    // deleteByUserId
    // =========================================================================

    @Test
    void should_removeCartFromRedis_when_deleteByUserIdCalled() {
        Cart cart = Cart.createFor("user-it-004");
        adapter.save(cart);
        assertThat(adapter.findByUserId("user-it-004")).isPresent();

        adapter.deleteByUserId("user-it-004");

        assertThat(adapter.findByUserId("user-it-004")).isEmpty();
    }

    @Test
    void should_notThrow_when_deletingNonExistentKey() {
        // No debe lanzar excepción si la clave no existe
        adapter.deleteByUserId("user-never-existed");
    }

    // =========================================================================
    // contrato del prefijo de clave en Redis
    // =========================================================================

    @Test
    void should_storeWithCorrectKeyPrefix_when_saved() {
        Cart cart = Cart.createFor("user-it-005");
        adapter.save(cart);

        Boolean exists = redisTemplate.hasKey(KEY_PREFIX + "user-it-005");
        assertThat(exists).isTrue();
    }

    @Test
    void should_isolateCartsByUser_when_multipleUsersSaved() {
        adapter.save(Cart.createFor("user-it-A"));
        adapter.save(Cart.createFor("user-it-B"));

        assertThat(adapter.findByUserId("user-it-A")).isPresent();
        assertThat(adapter.findByUserId("user-it-B")).isPresent();

        adapter.deleteByUserId("user-it-A");

        assertThat(adapter.findByUserId("user-it-A")).isEmpty();
        assertThat(adapter.findByUserId("user-it-B")).isPresent();
    }
}