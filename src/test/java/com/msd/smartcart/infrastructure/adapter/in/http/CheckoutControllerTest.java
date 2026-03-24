package com.msd.smartcart.infrastructure.adapter.in.http;

import com.msd.smartcart.domain.model.*;
import com.msd.smartcart.domain.port.in.CheckoutUseCase;
import com.msd.smartcart.infrastructure.adapter.in.http.response.CheckoutResponse;
import com.msd.smartcart.shared.AppError;
import com.msd.smartcart.shared.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheckoutControllerTest {

    @Mock private CheckoutUseCase checkoutUseCase;
    @Mock private IdempotencyStore idempotencyStore;

    private CheckoutController controller;

    @BeforeEach
    void setUp() {
        controller = new CheckoutController(checkoutUseCase, idempotencyStore);
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private UserDetails user() {
        return User.builder().username("user-123").password("x").roles("USER").build();
    }

    private Order order() {
        Cart cart = Cart.createFor("user-123")
                .add(new CartItem("prod-001", "Laptop", 1, new BigDecimal("1299.99")));
        return Order.createFor(cart);
    }

    // =========================================================================
    // idempotencia
    // =========================================================================

    @Test
    void should_returnCachedResponse_when_idempotencyKeyAlreadyUsed() {
        CheckoutResponse cached = CheckoutResponse.from(order());
        when(idempotencyStore.get("key-1", CheckoutResponse.class)).thenReturn(cached);

        ResponseEntity<?> response = controller.checkout(user(), "key-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(cached);
        verifyNoInteractions(checkoutUseCase);
    }

    // =========================================================================
    // failure del use case
    // =========================================================================

    @Test
    void should_return400_when_checkoutResultIsFailure() {
        when(idempotencyStore.get("key-1", CheckoutResponse.class)).thenReturn(null);
        when(checkoutUseCase.checkout("user-123"))
                .thenReturn(Result.failure(AppError.internal("cart.empty", "user-123")));

        ResponseEntity<?> response = controller.checkout(user(), "key-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // =========================================================================
    // variantes del switch sobre CheckoutResult
    // =========================================================================

    @Test
    void should_return200AndSaveIdempotencyKey_when_orderConfirmed() {
        when(idempotencyStore.get("key-1", CheckoutResponse.class)).thenReturn(null);
        when(checkoutUseCase.checkout("user-123"))
                .thenReturn(Result.success(new CheckoutResult.Confirmed(order())));

        ResponseEntity<?> response = controller.checkout(user(), "key-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(idempotencyStore).save(eq("key-1"), any(CheckoutResponse.class));
    }

    @Test
    void should_return422_when_someItemsOutOfStock() {
        when(idempotencyStore.get("key-1", CheckoutResponse.class)).thenReturn(null);
        List<CheckoutResult.OutOfStockItem> items = List.of(
                new CheckoutResult.OutOfStockItem("prod-001", "Laptop", 2, 0));
        when(checkoutUseCase.checkout("user-123"))
                .thenReturn(Result.success(new CheckoutResult.OutOfStock(items)));

        ResponseEntity<?> response = controller.checkout(user(), "key-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void should_return400_when_checkoutRejected() {
        when(idempotencyStore.get("key-1", CheckoutResponse.class)).thenReturn(null);
        when(checkoutUseCase.checkout("user-123"))
                .thenReturn(Result.success(new CheckoutResult.Rejected("Payment declined")));

        ResponseEntity<?> response = controller.checkout(user(), "key-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void should_return424_when_infrastructureFailure() {
        when(idempotencyStore.get("key-1", CheckoutResponse.class)).thenReturn(null);
        when(checkoutUseCase.checkout("user-123"))
                .thenReturn(Result.success(new CheckoutResult.InfrastructureFailure("db.down")));

        ResponseEntity<?> response = controller.checkout(user(), "key-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FAILED_DEPENDENCY);
    }
}