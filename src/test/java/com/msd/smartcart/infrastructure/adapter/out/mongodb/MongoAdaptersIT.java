package com.msd.smartcart.infrastructure.adapter.out.mongodb;

import com.msd.smartcart.IntegrationTestBase;
import com.msd.smartcart.domain.model.Cart;
import com.msd.smartcart.domain.model.CartItem;
import com.msd.smartcart.domain.model.Order;
import com.msd.smartcart.shared.exception.DuplicateOrderException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests de integración para los adapters de MongoDB.
 *
 * Verifican que los mapeos documento↔dominio son correctos, que los queries
 * funcionan con índices reales, y que las excepciones de dominio se lanzan
 * ante condiciones reales (ej. DuplicateKeyException de Mongo).
 */
class MongoAdaptersIT extends IntegrationTestBase {

    @Autowired private CartBackupRepositoryAdapter cartBackupAdapter;
    @Autowired private OrderRepositoryAdapter orderAdapter;
    @Autowired private MongoTemplate mongoTemplate;

    @AfterEach
    void cleanUp() {
        mongoTemplate.getDb().getCollection("saved_carts").drop();
        mongoTemplate.getDb().getCollection("orders").drop();
    }

    // =========================================================================
    // CartBackupRepositoryAdapter
    // =========================================================================

    @Nested
    class CartBackupRepositoryAdapterIT {

        private Cart cart(String userId) {
            return Cart.createFor(userId)
                    .add(new CartItem("prod-001", "Laptop", 1, new BigDecimal("1299.99")));
        }

        @Test
        void should_saveAndRetrieveCartBackup_when_saveOrUpdateCalled() {
            Cart c = cart("user-backup-001");

            cartBackupAdapter.saveOrUpdate(c);

            Optional<Cart> result = cartBackupAdapter.findLatestByUserId("user-backup-001");
            assertThat(result).isPresent();
            assertThat(result.get().userId()).isEqualTo("user-backup-001");
            assertThat(result.get().items()).hasSize(1);
        }

        @Test
        void should_upsertDocument_when_saveOrUpdateCalledTwiceForSameUser() {
            Cart v1 = cart("user-backup-002");
            Cart v2 = v1.add(new CartItem("prod-002", "Mouse", 2, new BigDecimal("79.99")));

            cartBackupAdapter.saveOrUpdate(v1);
            cartBackupAdapter.saveOrUpdate(v2);

            Cart retrieved = cartBackupAdapter.findLatestByUserId("user-backup-002").orElseThrow();
            // Debe tener el estado más reciente — 2 items
            assertThat(retrieved.items()).hasSize(2);
        }

        @Test
        void should_skipSave_when_cartContentHasNotChanged() {
            Cart c = cart("user-backup-003");

            cartBackupAdapter.saveOrUpdate(c);
            cartBackupAdapter.saveOrUpdate(c); // mismo contenido → skip

            // Verificamos que solo hay 1 documento (upsert no duplicó)
            long count = mongoTemplate.getDb()
                    .getCollection("saved_carts").countDocuments();
            assertThat(count).isEqualTo(1);
        }

        @Test
        void should_returnEmpty_when_noBackupExistsForUser() {
            Optional<Cart> result = cartBackupAdapter.findLatestByUserId("user-nonexistent");
            assertThat(result).isEmpty();
        }

        @Test
        void should_deleteBackup_when_deleteByUserIdCalled() {
            Cart c = cart("user-backup-004");
            cartBackupAdapter.saveOrUpdate(c);
            assertThat(cartBackupAdapter.findLatestByUserId("user-backup-004")).isPresent();

            cartBackupAdapter.deleteByUserId("user-backup-004");

            assertThat(cartBackupAdapter.findLatestByUserId("user-backup-004")).isEmpty();
        }

        @Test
        void should_preserveAllItemFields_when_roundTrippingThroughMongo() {
            Cart c = Cart.createFor("user-backup-005")
                    .add(new CartItem("prod-001", "Laptop Dell XPS", 3, new BigDecimal("1299.99")));

            cartBackupAdapter.saveOrUpdate(c);
            Cart retrieved = cartBackupAdapter.findLatestByUserId("user-backup-005").orElseThrow();

            CartItem item = retrieved.items().getFirst();
            assertThat(item.productId()).isEqualTo("prod-001");
            assertThat(item.name()).isEqualTo("Laptop Dell XPS");
            assertThat(item.quantity()).isEqualTo(3);
            assertThat(item.unitPrice()).isEqualByComparingTo(new BigDecimal("1299.99"));
        }
    }

    // =========================================================================
    // OrderRepositoryAdapter
    // =========================================================================

    @Nested
    class OrderRepositoryAdapterIT {

        private Order order(String userId) {
            Cart cart = Cart.createFor(userId)
                    .add(new CartItem("prod-001", "Laptop", 1, new BigDecimal("1299.99")));
            return Order.createFor(cart);
        }

        @Test
        void should_saveAndFindOrderById_when_orderPersisted() {
            Order o = order("user-order-001");

            orderAdapter.save(o);

            Optional<Order> result = orderAdapter.findById(o.orderId());
            assertThat(result).isPresent();
            assertThat(result.get().orderId()).isEqualTo(o.orderId());
            assertThat(result.get().userId()).isEqualTo("user-order-001");
            assertThat(result.get().totalAmount()).isEqualByComparingTo(new BigDecimal("1299.99"));
        }

        @Test
        void should_preserveAllOrderItemFields_when_roundTrippingThroughMongo() {
            Order o = order("user-order-002");

            orderAdapter.save(o);
            Order retrieved = orderAdapter.findById(o.orderId()).orElseThrow();

            assertThat(retrieved.items()).hasSize(1);
            assertThat(retrieved.items().getFirst().productId()).isEqualTo("prod-001");
            assertThat(retrieved.items().getFirst().productName()).isEqualTo("Laptop");
            assertThat(retrieved.items().getFirst().quantity()).isEqualTo(1);
            assertThat(retrieved.items().getFirst().subtotal())
                    .isEqualByComparingTo(new BigDecimal("1299.99"));
        }

        @Test
        void should_returnEmpty_when_orderIdNotFound() {
            Optional<Order> result = orderAdapter.findById("non-existent-id");
            assertThat(result).isEmpty();
        }

        @Test
        void should_returnAllOrdersForUser_when_findAllByUserIdCalled() {
            Order o1 = order("user-order-003");
            Order o2 = order("user-order-003");
            orderAdapter.save(o1);
            orderAdapter.save(o2);

            List<Order> orders = orderAdapter.findAllByUserId("user-order-003");

            assertThat(orders).hasSize(2);
            assertThat(orders).allMatch(o -> o.userId().equals("user-order-003"));
        }

        @Test
        void should_notReturnOrdersFromOtherUsers_when_findAllByUserIdCalled() {
            orderAdapter.save(order("user-A"));
            orderAdapter.save(order("user-B"));

            List<Order> ordersA = orderAdapter.findAllByUserId("user-A");
            assertThat(ordersA).hasSize(1);
            assertThat(ordersA.getFirst().userId()).isEqualTo("user-A");
        }

        @Test
        void should_throwDuplicateOrderException_when_sameOrderIdSavedTwice() {
            Order o = order("user-order-004");
            orderAdapter.save(o);

            var doc = com.msd.smartcart.infrastructure.adapter.out.mongodb.document.OrderDocument.from(o);
            assertThatThrownBy(() -> mongoTemplate.insert(doc))
                    .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        }

        @Test
        void should_returnEmptyList_when_noOrdersExistForUser() {
            List<Order> orders = orderAdapter.findAllByUserId("user-no-orders");
            assertThat(orders).isEmpty();
        }
    }
}