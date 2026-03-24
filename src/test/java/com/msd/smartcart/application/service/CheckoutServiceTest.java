// src/test/java/com/msd/smartcart/application/service/CheckoutServiceTest.java

package com.msd.smartcart.application.service;

import com.msd.smartcart.domain.error.CartError;
import com.msd.smartcart.domain.model.*;
import com.msd.smartcart.domain.port.in.CartUseCase;
import com.msd.smartcart.domain.port.out.*;
import com.msd.smartcart.shared.AppError;
import com.msd.smartcart.shared.Result;
import com.msd.smartcart.shared.exception.DuplicateOrderException;
import com.msd.smartcart.shared.exception.InfrastructureException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheckoutServiceTest {

    @Mock private CartUseCase cartUseCase;
    @Mock private OrderRepository orderRepository;
    @Mock private NotificationPublisher notificationPublisher;
    @Mock private ProductRepository productRepository;

    @InjectMocks
    private CheckoutService checkoutService;

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private Cart cartWithLaptop() {
        return Cart.createFor("user-123")
                .add(new CartItem("prod-001", "Laptop Dell XPS 15", 1, new BigDecimal("1299.99")));
    }

    private Product laptopInStock() {
        return new Product("prod-001", "Laptop Dell XPS 15", "Desc", new BigDecimal("1299.99"), 10);
    }

    private Product laptopOutOfStock() {
        return new Product("prod-001", "Laptop Dell XPS 15", "Desc", new BigDecimal("1299.99"), 0);
    }

    // -------------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------------

    @Test
    void should_confirmOrder_when_cartIsValidAndStockAvailable() {
        when(cartUseCase.getCart("user-123")).thenReturn(Result.success(cartWithLaptop()));
        when(productRepository.findAllByIds(any())).thenReturn(List.of(laptopInStock()));

        Result<CheckoutResult, AppError> result = checkoutService.checkout("user-123");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getValue()).isInstanceOf(CheckoutResult.Confirmed.class);

        CheckoutResult.Confirmed confirmed = (CheckoutResult.Confirmed) result.getValue();
        assertThat(confirmed.order().userId()).isEqualTo("user-123");
        assertThat(confirmed.order().totalAmount()).isEqualByComparingTo(new BigDecimal("1299.99"));

        verify(orderRepository, times(1)).save(any(Order.class));
        verify(cartUseCase, times(1)).clearCart("user-123");
        verify(notificationPublisher, times(1)).publish(any(OrderConfirmedEvent.class));
    }

    // -------------------------------------------------------------------------
    // Carrito vacío
    // -------------------------------------------------------------------------

    @Test
    void should_returnFailure_when_cartNotFound() {
        when(cartUseCase.getCart("user-123"))
                .thenReturn(Result.failure(CartError.NotFound.of("user-123")));

        Result<CheckoutResult, AppError> result = checkoutService.checkout("user-123");

        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError().code()).isEqualTo("cart.not_found");
        verifyNoInteractions(orderRepository);
    }

    @Test
    void should_returnFailure_when_cartIsEmpty() {
        when(cartUseCase.getCart("user-123"))
                .thenReturn(Result.success(Cart.createFor("user-123")));

        Result<CheckoutResult, AppError> result = checkoutService.checkout("user-123");

        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError().code()).isEqualTo("cart.empty");
        verifyNoInteractions(orderRepository);
    }

    // -------------------------------------------------------------------------
    // Stock insuficiente — autocorrección
    // -------------------------------------------------------------------------

    @Test
    void should_returnOutOfStock_when_productHasInsufficientStock() {
        when(cartUseCase.getCart("user-123")).thenReturn(Result.success(cartWithLaptop()));
        when(productRepository.findAllByIds(any())).thenReturn(List.of(laptopOutOfStock()));
        when(cartUseCase.removeItem(any(), any(), anyInt()))
                .thenReturn(Result.success(Cart.createFor("user-123")));
        when(cartUseCase.getCart("user-123"))
                .thenReturn(Result.success(cartWithLaptop()))  // primera llamada
                .thenReturn(Result.failure(CartError.NotFound.of("user-123"))); // carrito vacío post-corrección

        Result<CheckoutResult, AppError> result = checkoutService.checkout("user-123");

        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError().code()).isEqualTo("cart.empty");
        verifyNoInteractions(orderRepository);
    }

    @Test
    void should_returnOutOfStockResult_when_someItemsRemovedButCartStillHasItems() {
        Cart cartWithTwoItems = Cart.createFor("user-123")
                .add(new CartItem("prod-001", "Laptop", 1, new BigDecimal("1299.99")))
                .add(new CartItem("prod-002", "Mouse", 2, new BigDecimal("79.99")));

        Product mouseInStock = new Product("prod-002", "Mouse", "Desc", new BigDecimal("79.99"), 10);

        when(cartUseCase.getCart("user-123"))
                .thenReturn(Result.success(cartWithTwoItems))
                .thenReturn(Result.success(cartWithLaptop())); // carrito con items restantes
        when(productRepository.findAllByIds(any()))
                .thenReturn(List.of(laptopOutOfStock(), mouseInStock));
        when(cartUseCase.removeItem(any(), any(), anyInt()))
                .thenReturn(Result.success(cartWithLaptop()));

        Result<CheckoutResult, AppError> result = checkoutService.checkout("user-123");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getValue()).isInstanceOf(CheckoutResult.OutOfStock.class);

        CheckoutResult.OutOfStock outOfStock = (CheckoutResult.OutOfStock) result.getValue();
        assertThat(outOfStock.products()).hasSize(1);
        assertThat(outOfStock.products().get(0).productId()).isEqualTo("prod-001");

        verifyNoInteractions(orderRepository);
    }

    // -------------------------------------------------------------------------
    // Fallo al persistir la orden
    // -------------------------------------------------------------------------

    @Test
    void should_returnInfrastructureFailure_when_orderPersistenceFails() {
        when(cartUseCase.getCart("user-123")).thenReturn(Result.success(cartWithLaptop()));
        when(productRepository.findAllByIds(any())).thenReturn(List.of(laptopInStock()));
        doThrow(new InfrastructureException("order.infra.failed"))
                .when(orderRepository).save(any(Order.class));

        Result<CheckoutResult, AppError> result = checkoutService.checkout("user-123");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getValue()).isInstanceOf(CheckoutResult.InfrastructureFailure.class);
        verify(cartUseCase, never()).clearCart(any());
    }

    @Test
    void should_returnInternalError_when_duplicateOrder() {
        when(cartUseCase.getCart("user-123")).thenReturn(Result.success(cartWithLaptop()));
        when(productRepository.findAllByIds(any())).thenReturn(List.of(laptopInStock()));
        doThrow(new DuplicateOrderException("order-123"))
                .when(orderRepository).save(any(Order.class));

        Result<CheckoutResult, AppError> result = checkoutService.checkout("user-123");

        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError().code()).isEqualTo("order.duplicate");
        verify(cartUseCase, never()).clearCart(any());
    }

    // -------------------------------------------------------------------------
    // Limpieza del carrito
    // -------------------------------------------------------------------------

    @Test
    void should_clearCart_when_orderConfirmed() {
        when(cartUseCase.getCart("user-123")).thenReturn(Result.success(cartWithLaptop()));
        when(productRepository.findAllByIds(any())).thenReturn(List.of(laptopInStock()));

        checkoutService.checkout("user-123");

        verify(cartUseCase, times(1)).clearCart("user-123");
    }

    @Test
    void should_notClearCart_when_orderPersistenceFails() {
        when(cartUseCase.getCart("user-123")).thenReturn(Result.success(cartWithLaptop()));
        when(productRepository.findAllByIds(any())).thenReturn(List.of(laptopInStock()));
        doThrow(new InfrastructureException("order.infra.failed"))
                .when(orderRepository).save(any(Order.class));

        checkoutService.checkout("user-123");

        verify(cartUseCase, never()).clearCart(any());
    }

    // -------------------------------------------------------------------------
    // Secundarios — fallos de infraestructura
    // -------------------------------------------------------------------------

    @Test
    void should_returnInfrastructureFailure_when_stockValidationFails() {
        when(cartUseCase.getCart("user-123")).thenReturn(Result.success(cartWithLaptop()));
        when(productRepository.findAllByIds(any()))
                .thenThrow(new InfrastructureException("products.infra.failed"));

        Result<CheckoutResult, AppError> result = checkoutService.checkout("user-123");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getValue()).isInstanceOf(CheckoutResult.InfrastructureFailure.class);
        verifyNoInteractions(orderRepository);
    }

    // -------------------------------------------------------------------------
    // Secundarios — fallo al publicar notificación
    // -------------------------------------------------------------------------

    @Test
    void should_confirmOrder_when_notificationFails() {
        when(cartUseCase.getCart("user-123")).thenReturn(Result.success(cartWithLaptop()));
        when(productRepository.findAllByIds(any())).thenReturn(List.of(laptopInStock()));
        doThrow(new RuntimeException("RabbitMQ down"))
                .when(notificationPublisher).publish(any());

        Result<CheckoutResult, AppError> result = checkoutService.checkout("user-123");

        // best-effort — la orden se confirma aunque falle la notificación
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getValue()).isInstanceOf(CheckoutResult.Confirmed.class);
        verify(orderRepository, times(1)).save(any(Order.class));
        verify(cartUseCase, times(1)).clearCart("user-123");
    }

    @Test
    void should_returnInfrastructureFailure_when_unexpectedErrorOnSave() {
        when(cartUseCase.getCart("user-123")).thenReturn(Result.success(cartWithLaptop()));
        when(productRepository.findAllByIds(any())).thenReturn(List.of(laptopInStock()));
        doThrow(new RuntimeException("Unexpected DB error"))
                .when(orderRepository).save(any(Order.class));

        Result<CheckoutResult, AppError> result = checkoutService.checkout("user-123");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getValue()).isInstanceOf(CheckoutResult.InfrastructureFailure.class);
        CheckoutResult.InfrastructureFailure failure = (CheckoutResult.InfrastructureFailure) result.getValue();
        assertThat(failure.cause()).isEqualTo("order.save.failed");
    }

    @Test
    void should_markAsOutOfStock_when_productNoLongerExistsInCatalog() {
        when(cartUseCase.getCart("user-123")).thenReturn(Result.success(cartWithLaptop()));
        when(productRepository.findAllByIds(any())).thenReturn(List.of()); // producto no existe
        when(cartUseCase.removeItem(any(), any(), anyInt()))
                .thenReturn(Result.success(Cart.createFor("user-123")));
        when(cartUseCase.getCart("user-123"))
                .thenReturn(Result.success(cartWithLaptop()))
                .thenReturn(Result.failure(CartError.NotFound.of("user-123")));

        Result<CheckoutResult, AppError> result = checkoutService.checkout("user-123");

        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError().code()).isEqualTo("cart.empty");
    }

    @Test
    void should_returnInfrastructureFailure_when_unexpectedErrorOnStockValidation() {
        when(cartUseCase.getCart("user-123")).thenReturn(Result.success(cartWithLaptop()));
        when(productRepository.findAllByIds(any()))
                .thenThrow(new RuntimeException("Unexpected error"));

        Result<CheckoutResult, AppError> result = checkoutService.checkout("user-123");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getValue()).isInstanceOf(CheckoutResult.InfrastructureFailure.class);
        CheckoutResult.InfrastructureFailure failure = (CheckoutResult.InfrastructureFailure) result.getValue();
        assertThat(failure.cause()).isEqualTo("products.get.failed");
    }

    @Test
    void should_returnEmptyCart_when_correctedCartIsEmpty() {
        when(cartUseCase.getCart("user-123"))
                .thenReturn(Result.success(cartWithLaptop()))
                .thenReturn(Result.success(Cart.createFor("user-123"))); // carrito vacío post-corrección
        when(productRepository.findAllByIds(any())).thenReturn(List.of(laptopOutOfStock()));
        when(cartUseCase.removeItem(any(), any(), anyInt()))
                .thenReturn(Result.success(Cart.createFor("user-123")));

        Result<CheckoutResult, AppError> result = checkoutService.checkout("user-123");

        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError().code()).isEqualTo("cart.empty");
    }
}