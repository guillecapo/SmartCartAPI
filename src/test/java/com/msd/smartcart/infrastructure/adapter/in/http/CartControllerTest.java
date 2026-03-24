package com.msd.smartcart.infrastructure.adapter.in.http;

import com.msd.smartcart.domain.model.Cart;
import com.msd.smartcart.domain.model.CartItem;
import com.msd.smartcart.domain.port.in.CartUseCase;
import com.msd.smartcart.infrastructure.adapter.in.http.request.AddItemRequest;
import com.msd.smartcart.infrastructure.adapter.in.http.request.RemoveItemRequest;
import com.msd.smartcart.infrastructure.adapter.in.http.response.CartResponse;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartControllerTest {

    @Mock private CartUseCase cartUseCase;
    @Mock private IdempotencyStore idempotencyStore;

    private CartController controller;

    @BeforeEach
    void setUp() {
        controller = new CartController(cartUseCase, idempotencyStore);
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private UserDetails user() {
        return User.builder().username("user-123").password("x").roles("USER").build();
    }

    private Cart cart() {
        return Cart.createFor("user-123")
                .add(new CartItem("prod-001", "Laptop", 1, new BigDecimal("1299.99")));
    }

    private AppError anyError() {
        return AppError.internal("some.error", "ctx");
    }

    // =========================================================================
    // getCart
    // =========================================================================

    @Test
    void should_return200_when_cartFound() {
        when(cartUseCase.getCart("user-123")).thenReturn(Result.success(cart()));

        ResponseEntity<CartResponse> response = controller.getCart(user());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void should_return404_when_cartNotFound() {
        when(cartUseCase.getCart("user-123")).thenReturn(Result.failure(anyError()));

        ResponseEntity<CartResponse> response = controller.getCart(user());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // =========================================================================
    // addItem
    // =========================================================================

    @Test
    void should_returnCachedResponse_when_idempotencyKeyAlreadyUsed() {
        CartResponse cached = CartResponse.from(cart());
        when(idempotencyStore.get("key-1", CartResponse.class)).thenReturn(cached);

        AddItemRequest req = new AddItemRequest();
        req.setProductId("prod-001");
        req.setQuantity(1);

        ResponseEntity<CartResponse> response = controller.addItem(user(), "key-1", req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(cached);
        verifyNoInteractions(cartUseCase);
    }

    @Test
    void should_return200AndSaveIdempotencyKey_when_addItemSucceeds() {
        when(idempotencyStore.get("key-1", CartResponse.class)).thenReturn(null);
        when(cartUseCase.addItem("user-123", "prod-001", 1)).thenReturn(Result.success(cart()));

        AddItemRequest req = new AddItemRequest();
        req.setProductId("prod-001");
        req.setQuantity(1);

        ResponseEntity<CartResponse> response = controller.addItem(user(), "key-1", req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(idempotencyStore).save(eq("key-1"), any(CartResponse.class));
    }

    @Test
    void should_return400_when_addItemFails() {
        when(idempotencyStore.get("key-1", CartResponse.class)).thenReturn(null);
        when(cartUseCase.addItem(any(), any(), anyInt())).thenReturn(Result.failure(anyError()));

        AddItemRequest req = new AddItemRequest();
        req.setProductId("prod-001");
        req.setQuantity(1);

        ResponseEntity<CartResponse> response = controller.addItem(user(), "key-1", req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(idempotencyStore, never()).save(any(), any());
    }

    // =========================================================================
    // removeItem
    // =========================================================================

    @Test
    void should_returnCachedResponse_when_idempotencyKeyAlreadyUsedOnRemove() {
        CartResponse cached = CartResponse.from(cart());
        when(idempotencyStore.get("key-2", CartResponse.class)).thenReturn(cached);

        RemoveItemRequest req = new RemoveItemRequest();
        req.setQuantity(1);

        ResponseEntity<CartResponse> response =
                controller.removeItem(user(), "prod-001", "key-2", req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verifyNoInteractions(cartUseCase);
    }

    @Test
    void should_return200AndSaveIdempotencyKey_when_removeItemSucceeds() {
        when(idempotencyStore.get("key-2", CartResponse.class)).thenReturn(null);
        when(cartUseCase.removeItem("user-123", "prod-001", 1)).thenReturn(Result.success(cart()));

        RemoveItemRequest req = new RemoveItemRequest();
        req.setQuantity(1);

        ResponseEntity<CartResponse> response =
                controller.removeItem(user(), "prod-001", "key-2", req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(idempotencyStore).save(eq("key-2"), any(CartResponse.class));
    }

    @Test
    void should_return400_when_removeItemFails() {
        when(idempotencyStore.get("key-2", CartResponse.class)).thenReturn(null);
        when(cartUseCase.removeItem(any(), any(), anyInt())).thenReturn(Result.failure(anyError()));

        RemoveItemRequest req = new RemoveItemRequest();
        req.setQuantity(1);

        ResponseEntity<CartResponse> response =
                controller.removeItem(user(), "prod-001", "key-2", req);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}