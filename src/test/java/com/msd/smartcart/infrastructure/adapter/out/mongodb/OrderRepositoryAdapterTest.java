package com.msd.smartcart.infrastructure.adapter.out.mongodb;

import com.msd.smartcart.domain.model.Cart;
import com.msd.smartcart.domain.model.CartItem;
import com.msd.smartcart.domain.model.Order;
import com.msd.smartcart.infrastructure.adapter.out.mongodb.document.OrderDocument;
import com.msd.smartcart.shared.exception.DuplicateOrderException;
import com.msd.smartcart.shared.exception.InfrastructureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderRepositoryAdapterTest {

    @Mock private MongoTemplate mongoTemplate;

    private OrderRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new OrderRepositoryAdapter(mongoTemplate);
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private Order order() {
        Cart cart = Cart.createFor("user-123")
                .add(new CartItem("prod-001", "Laptop", 1, new BigDecimal("1299.99")));
        return Order.createFor(cart);
    }

    private OrderDocument orderDocument(Order order) {
        return OrderDocument.from(order);
    }

    // =========================================================================
    // save — happy path
    // =========================================================================

    @Test
    void should_delegateToMongoTemplate_when_saveCalledWithValidOrder() {
        Order o = order();

        adapter.save(o);

        verify(mongoTemplate).save(any(OrderDocument.class));
    }

    // =========================================================================
    // save — fallos de infraestructura
    // =========================================================================

    @Test
    void should_throwDuplicateOrderException_when_duplicateKeyExceptionOccurs() {
        Order o = order();
        when(mongoTemplate.save(any(OrderDocument.class)))
                .thenThrow(mock(DuplicateKeyException.class));

        assertThatThrownBy(() -> adapter.save(o))
                .isInstanceOf(DuplicateOrderException.class);
    }

    @Test
    void should_throwInfrastructureException_when_mongoUnavailableOnSave() {
        Order o = order();
        when(mongoTemplate.save(any(OrderDocument.class)))
                .thenThrow(mock(DataAccessResourceFailureException.class));

        assertThatThrownBy(() -> adapter.save(o))
                .isInstanceOf(InfrastructureException.class)
                .hasMessageContaining("order.infra.failed");
    }

    // =========================================================================
    // findById
    // =========================================================================

    @Test
    void should_returnEmpty_when_orderNotFound() {
        when(mongoTemplate.findById(any(), eq(OrderDocument.class))).thenReturn(null);

        Optional<Order> result = adapter.findById("non-existent-id");

        assertThat(result).isEmpty();
    }

    @Test
    void should_returnMappedOrder_when_documentFound() {
        Order o = order();
        OrderDocument doc = orderDocument(o);

        when(mongoTemplate.findById(eq(o.orderId()), eq(OrderDocument.class))).thenReturn(doc);

        Optional<Order> result = adapter.findById(o.orderId());

        assertThat(result).isPresent();
        assertThat(result.get().userId()).isEqualTo("user-123");
        assertThat(result.get().totalAmount()).isEqualByComparingTo(new BigDecimal("1299.99"));
    }

    // =========================================================================
    // findAllByUserId
    // =========================================================================

    @Test
    void should_returnEmptyList_when_noOrdersExistForUser() {
        when(mongoTemplate.find(any(Query.class), eq(OrderDocument.class))).thenReturn(List.of());

        List<Order> result = adapter.findAllByUserId("user-123");

        assertThat(result).isEmpty();
    }

    @Test
    void should_returnMappedOrders_when_ordersExistForUser() {
        Order o = order();
        OrderDocument doc = orderDocument(o);

        when(mongoTemplate.find(any(Query.class), eq(OrderDocument.class)))
                .thenReturn(List.of(doc));

        List<Order> result = adapter.findAllByUserId("user-123");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).userId()).isEqualTo("user-123");
    }

    @Test
    void should_returnMultipleOrders_when_multipleOrdersExist() {
        Order o1 = order();
        Order o2 = order();
        OrderDocument doc1 = orderDocument(o1);
        OrderDocument doc2 = orderDocument(o2);

        when(mongoTemplate.find(any(Query.class), eq(OrderDocument.class)))
                .thenReturn(List.of(doc1, doc2));

        List<Order> result = adapter.findAllByUserId("user-123");

        assertThat(result).hasSize(2);
    }
}