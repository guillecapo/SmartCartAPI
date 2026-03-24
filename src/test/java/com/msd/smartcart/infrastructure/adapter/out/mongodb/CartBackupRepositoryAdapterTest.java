package com.msd.smartcart.infrastructure.adapter.out.mongodb;

import com.msd.smartcart.domain.model.Cart;
import com.msd.smartcart.domain.model.CartItem;
import com.msd.smartcart.infrastructure.adapter.out.mongodb.document.CartBackupDocument;
import com.msd.smartcart.infrastructure.adapter.out.mongodb.document.CartHashProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndReplaceOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartBackupRepositoryAdapterTest {

    @Mock private MongoTemplate mongoTemplate;

    private CartBackupRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new CartBackupRepositoryAdapter(mongoTemplate);
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private Cart cart() {
        return Cart.createFor("user-123")
                .add(new CartItem("prod-001", "Laptop", 1, new BigDecimal("1299.99")));
    }

    private Cart cartWithTwoItems() {
        return Cart.createFor("user-123")
                .add(new CartItem("prod-001", "Laptop", 1, new BigDecimal("1299.99")))
                .add(new CartItem("prod-002", "Mouse", 2, new BigDecimal("79.99")));
    }

    // =========================================================================
    // saveOrUpdate — primer guardado (sin proyección existente)
    // =========================================================================

    @Test
    void should_saveDocument_when_noExistingBackupFound() {
        when(mongoTemplate.findOne(any(Query.class), eq(CartHashProjection.class), eq("saved_carts")))
                .thenReturn(null);

        adapter.saveOrUpdate(cart());

        verify(mongoTemplate).findAndReplace(
                any(Query.class),
                any(CartBackupDocument.class),
                any(FindAndReplaceOptions.class),
                eq(CartBackupDocument.class),
                eq("saved_carts")
        );
    }

    // =========================================================================
    // saveOrUpdate — hash idéntico → skip
    // =========================================================================

    @Test
    void should_skipSave_when_hashIsUnchanged() {
        // Calculamos el hash que produciría el adapter para "prod-001 qty=1"
        // El hash depende de: productId + quantity, ordenado por productId
        // Usamos un projection con el mismo hash que el cart
        Cart singleItemCart = cart();

        // Obtenemos la proyección con hash coincidente simulando
        // el mismo cómputo interno del adapter
        CartHashProjection projection = new CartHashProjection();
        // Hash MD5 de "prod-0011" (productId="prod-001", quantity=1)
        projection.setItemsHash(org.springframework.util.DigestUtils
                .md5DigestAsHex("prod-0011".getBytes()));

        when(mongoTemplate.findOne(any(Query.class), eq(CartHashProjection.class), eq("saved_carts")))
                .thenReturn(projection);

        adapter.saveOrUpdate(singleItemCart);

        // No debe llamar a findAndReplace porque el hash no cambió
        verify(mongoTemplate, never()).findAndReplace(
                any(), any(), any(), any(), any(String.class));
    }

    // =========================================================================
    // saveOrUpdate — hash diferente → guarda
    // =========================================================================

    @Test
    void should_saveDocument_when_hashHasChanged() {
        CartHashProjection projection = new CartHashProjection();
        projection.setItemsHash("old-hash-that-does-not-match");

        when(mongoTemplate.findOne(any(Query.class), eq(CartHashProjection.class), eq("saved_carts")))
                .thenReturn(projection);

        adapter.saveOrUpdate(cart());

        verify(mongoTemplate).findAndReplace(
                any(Query.class),
                any(CartBackupDocument.class),
                any(FindAndReplaceOptions.class),
                eq(CartBackupDocument.class),
                eq("saved_carts")
        );
    }

    // =========================================================================
    // saveOrUpdate — hash es determinístico y ordena items
    // =========================================================================

    @Test
    void should_produceConsistentHash_regardless_of_itemInsertionOrder() {
        // Dos carritos con los mismos items en distinto orden → mismo hash → mismo skip
        Cart cartAB = Cart.createFor("user-123")
                .add(new CartItem("prod-A", "A", 1, BigDecimal.ONE))
                .add(new CartItem("prod-B", "B", 2, BigDecimal.TEN));

        Cart cartBA = Cart.createFor("user-123")
                .add(new CartItem("prod-B", "B", 2, BigDecimal.TEN))
                .add(new CartItem("prod-A", "A", 1, BigDecimal.ONE));

        // Hash de "prod-A1|prod-B2" (ordenado por productId)
        String expectedHash = org.springframework.util.DigestUtils
                .md5DigestAsHex("prod-A1|prod-B2".getBytes());

        CartHashProjection projectionAB = new CartHashProjection();
        projectionAB.setItemsHash(expectedHash);

        CartHashProjection projectionBA = new CartHashProjection();
        projectionBA.setItemsHash(expectedHash);

        when(mongoTemplate.findOne(any(Query.class), eq(CartHashProjection.class), eq("saved_carts")))
                .thenReturn(projectionAB)
                .thenReturn(projectionBA);

        adapter.saveOrUpdate(cartAB);
        adapter.saveOrUpdate(cartBA);

        // Ambos deben hacer skip porque el hash es consistente
        verify(mongoTemplate, never()).findAndReplace(
                any(), any(), any(), any(), any(String.class));
    }

    // =========================================================================
    // findLatestByUserId
    // =========================================================================

    @Test
    void should_returnEmpty_when_noBackupExists() {
        when(mongoTemplate.findById("user-123", CartBackupDocument.class, "saved_carts"))
                .thenReturn(null);

        Optional<Cart> result = adapter.findLatestByUserId("user-123");

        assertThat(result).isEmpty();
    }

    @Test
    void should_returnMappedCart_when_backupDocumentExists() {
        Cart originalCart = cart();
        CartBackupDocument doc = CartBackupDocument.from(originalCart, "some-hash");

        when(mongoTemplate.findById("user-123", CartBackupDocument.class, "saved_carts"))
                .thenReturn(doc);

        Optional<Cart> result = adapter.findLatestByUserId("user-123");

        assertThat(result).isPresent();
        assertThat(result.get().userId()).isEqualTo("user-123");
        assertThat(result.get().items()).hasSize(1);
    }

    // =========================================================================
    // deleteByUserId
    // =========================================================================

    @Test
    void should_removeDocumentFromMongo_when_deleteByUserIdCalled() {
        adapter.deleteByUserId("user-123");

        verify(mongoTemplate).remove(
                any(Query.class),
                eq(CartBackupDocument.class),
                eq("saved_carts")
        );
    }
}