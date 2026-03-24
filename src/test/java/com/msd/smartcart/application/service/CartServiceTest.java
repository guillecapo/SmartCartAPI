package com.msd.smartcart.application.service;

import com.msd.smartcart.domain.model.*;
import com.msd.smartcart.domain.port.out.*;
import com.msd.smartcart.shared.AppError;
import com.msd.smartcart.shared.Result;
import com.msd.smartcart.shared.exception.InfrastructureException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock private ActiveCartRepository activeCartRepository;
    @Mock private CartBackupRepository cartBackupRepository;
    @Mock private ProductRepository productRepository;
    @Mock private AiRecommenderPort aiRecommenderPort;

    @InjectMocks
    private CartService cartService;

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private Product laptop() {
        return new Product("prod-001", "Laptop Dell XPS 15", "Desc", new BigDecimal("1299.99"), 10);
    }

    private Cart emptyCart() {
        return Cart.createFor("user-123");
    }

    private Cart cartWithLaptop() {
        return emptyCart().add(new CartItem("prod-001", "Laptop Dell XPS 15", 1, new BigDecimal("1299.99")));
    }

    // =========================================================================
    // addItem — validación de entrada
    // =========================================================================

    @Test
    void should_returnFailure_when_quantityIsZero() {
        Result<Cart, AppError> result = cartService.addItem("user-123", "prod-001", 0);

        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError().code()).isEqualTo("cart.invalid_quantity");
        verifyNoInteractions(activeCartRepository);
    }

    @Test
    void should_returnFailure_when_quantityIsNegative() {
        Result<Cart, AppError> result = cartService.addItem("user-123", "prod-001", -1);

        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError().code()).isEqualTo("cart.invalid_quantity");
        verifyNoInteractions(activeCartRepository);
    }

    // =========================================================================
    // addItem — creación y actualización de carrito
    // =========================================================================

    @Test
    void should_createNewCart_when_noActiveCartExists() {
        when(activeCartRepository.findByUserId("user-123")).thenReturn(Optional.empty());
        when(productRepository.findById("prod-001")).thenReturn(Optional.of(laptop()));

        Result<Cart, AppError> result = cartService.addItem("user-123", "prod-001", 1);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getValue().items()).hasSize(1);
        verify(activeCartRepository).save(any(Cart.class));
    }

    @Test
    void should_incrementQuantity_when_productAlreadyInCart() {
        when(activeCartRepository.findByUserId("user-123")).thenReturn(Optional.of(cartWithLaptop()));

        Result<Cart, AppError> result = cartService.addItem("user-123", "prod-001", 2);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getValue().items().get(0).quantity()).isEqualTo(3);
        // Producto ya en carrito: no se debe consultar el repositorio de productos
        verify(productRepository, never()).findById(any());
    }

    @Test
    void should_returnFailure_when_productNotFound() {
        when(activeCartRepository.findByUserId("user-123")).thenReturn(Optional.empty());
        when(productRepository.findById("prod-999")).thenReturn(Optional.empty());

        Result<Cart, AppError> result = cartService.addItem("user-123", "prod-999", 1);

        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError().code()).isEqualTo("cart.product_not_found");
    }

    @Test
    void should_returnFailure_when_cartIsAtMaxCapacity() {
        Cart fullCart = emptyCart();
        for (int i = 0; i < 50; i++) {
            fullCart = fullCart.add(new CartItem("prod-" + i, "Product " + i, 1, BigDecimal.ONE));
        }
        when(activeCartRepository.findByUserId("user-123")).thenReturn(Optional.of(fullCart));

        Result<Cart, AppError> result = cartService.addItem("user-123", "prod-nuevo", 1);

        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError().code()).isEqualTo("cart.overflow");
    }

    // =========================================================================
    // addItem — backup threshold: valor (> $500)
    // =========================================================================

    @Test
    void should_triggerBackup_when_cartTotalExceedsValueThreshold() {
        Cart expensiveCart = emptyCart().add(
                new CartItem("prod-001", "Laptop", 1, new BigDecimal("600.00")));
        when(activeCartRepository.findByUserId("user-123")).thenReturn(Optional.of(expensiveCart));

        Result<Cart, AppError> result = cartService.addItem("user-123", "prod-001", 1);

        assertThat(result.isSuccess()).isTrue();
        verify(cartBackupRepository).saveOrUpdate(any(Cart.class));
    }

    @Test
    void should_notTriggerBackup_when_cartTotalBelowValueThreshold() {
        when(activeCartRepository.findByUserId("user-123")).thenReturn(Optional.empty());
        when(productRepository.findById("prod-001")).thenReturn(Optional.of(
                new Product("prod-001", "Mouse", "Desc", new BigDecimal("79.99"), 10)));

        cartService.addItem("user-123", "prod-001", 1);

        verify(cartBackupRepository, never()).saveOrUpdate(any());
    }

    // =========================================================================
    // addItem — backup threshold: cantidad de items (> 20)
    // =========================================================================

    @Test
    void should_triggerBackup_when_itemCountExceedsThreshold() {
        // 21 items distintos con precio $1 → valor $21 (bajo), pero count > 20 → dispara backup
        Cart cartWith21Items = emptyCart();
        for (int i = 0; i < 21; i++) {
            cartWith21Items = cartWith21Items.add(
                    new CartItem("prod-" + i, "Product " + i, 1, new BigDecimal("1.00")));
        }
        when(activeCartRepository.findByUserId("user-123")).thenReturn(Optional.of(cartWith21Items));

        Result<Cart, AppError> result = cartService.addItem("user-123", "prod-0", 1);

        assertThat(result.isSuccess()).isTrue();
        verify(cartBackupRepository).saveOrUpdate(any(Cart.class));
    }

    @Test
    void should_notTriggerBackup_when_itemCountIsExactly20() {
        // Exactamente 20 items con $1 → no supera ni valor ni count
        Cart cartWith20Items = emptyCart();
        for (int i = 0; i < 20; i++) {
            cartWith20Items = cartWith20Items.add(
                    new CartItem("prod-" + i, "Product " + i, 1, new BigDecimal("1.00")));
        }
        when(activeCartRepository.findByUserId("user-123")).thenReturn(Optional.of(cartWith20Items));

        cartService.addItem("user-123", "prod-0", 1);

        verify(cartBackupRepository, never()).saveOrUpdate(any());
    }

    @Test
    void should_continueFlow_when_backupSaveFails() {
        Cart expensiveCart = emptyCart().add(
                new CartItem("prod-001", "Laptop", 1, new BigDecimal("600.00")));
        when(activeCartRepository.findByUserId("user-123")).thenReturn(Optional.of(expensiveCart));
        doThrow(new RuntimeException("MongoDB down"))
                .when(cartBackupRepository).saveOrUpdate(any(Cart.class));

        Result<Cart, AppError> result = cartService.addItem("user-123", "prod-001", 1);

        assertThat(result.isSuccess()).isTrue();
    }

    // =========================================================================
    // addItem — infraestructura: findByUserId
    // =========================================================================

    @Test
    void should_returnFailure_when_findByUserIdThrowsInfrastructureException_onAddItem() {
        when(activeCartRepository.findByUserId("user-123"))
                .thenThrow(new InfrastructureException("cart.redis.unavailable"));

        Result<Cart, AppError> result = cartService.addItem("user-123", "prod-001", 1);

        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError().code()).isEqualTo("active.cart.infra.failed");
    }

    @Test
    void should_rethrow_when_findByUserIdThrowsUnexpectedException_onAddItem() {
        when(activeCartRepository.findByUserId("user-123"))
                .thenThrow(new RuntimeException("Unexpected error"));

        assertThatThrownBy(() -> cartService.addItem("user-123", "prod-001", 1))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Unexpected error");
    }

    // =========================================================================
    // addItem — infraestructura: productRepository.findById
    // =========================================================================

    @Test
    void should_returnFailure_when_productRepositoryThrowsInfrastructureException() {
        when(activeCartRepository.findByUserId("user-123")).thenReturn(Optional.empty());
        when(productRepository.findById("prod-001"))
                .thenThrow(new InfrastructureException("product.infra.failed"));

        Result<Cart, AppError> result = cartService.addItem("user-123", "prod-001", 1);

        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError().code()).isEqualTo("product.infra.failed");
    }

    @Test
    void should_rethrow_when_productRepositoryThrowsUnexpectedException() {
        when(activeCartRepository.findByUserId("user-123")).thenReturn(Optional.empty());
        when(productRepository.findById("prod-001"))
                .thenThrow(new RuntimeException("Unexpected DB error"));

        assertThatThrownBy(() -> cartService.addItem("user-123", "prod-001", 1))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Unexpected DB error");
    }

    // =========================================================================
    // addItem — infraestructura: save
    // =========================================================================

    @Test
    void should_returnFailure_when_saveThrowsInfrastructureException_onAddItem() {
        when(activeCartRepository.findByUserId("user-123")).thenReturn(Optional.empty());
        when(productRepository.findById("prod-001")).thenReturn(Optional.of(laptop()));
        doThrow(new InfrastructureException("cart.redis.unavailable"))
                .when(activeCartRepository).save(any(Cart.class));

        Result<Cart, AppError> result = cartService.addItem("user-123", "prod-001", 1);

        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError().code()).isEqualTo("active.cart.infra.failed");
    }

    @Test
    void should_rethrow_when_saveThrowsUnexpectedException_onAddItem() {
        when(activeCartRepository.findByUserId("user-123")).thenReturn(Optional.empty());
        when(productRepository.findById("prod-001")).thenReturn(Optional.of(laptop()));
        doThrow(new RuntimeException("Unexpected save error"))
                .when(activeCartRepository).save(any(Cart.class));

        assertThatThrownBy(() -> cartService.addItem("user-123", "prod-001", 1))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Unexpected save error");
    }

    // =========================================================================
    // addItem — AI suggestions
    // =========================================================================

    @Test
    void should_callAiRecommender_when_itemAddedSuccessfully() {
        when(activeCartRepository.findByUserId("user-123")).thenReturn(Optional.empty());
        when(productRepository.findById("prod-001")).thenReturn(Optional.of(laptop()));

        cartService.addItem("user-123", "prod-001", 1);

        verify(aiRecommenderPort).suggest(eq("user-123"), argThat(ids ->
                ids.size() == 1 && ids.contains("prod-001")));
    }

    @Test
    void should_continueFlow_when_aiRecommenderFails() {
        when(activeCartRepository.findByUserId("user-123")).thenReturn(Optional.empty());
        when(productRepository.findById("prod-001")).thenReturn(Optional.of(laptop()));
        doThrow(new RuntimeException("AI service down"))
                .when(aiRecommenderPort).suggest(any(), any());

        Result<Cart, AppError> result = cartService.addItem("user-123", "prod-001", 1);

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void should_passAllProductIdsToAi_when_cartHasMultipleItems() {
        Cart cartWithTwo = emptyCart()
                .add(new CartItem("prod-001", "Laptop", 1, new BigDecimal("1299.99")))
                .add(new CartItem("prod-002", "Mouse", 1, new BigDecimal("79.99")));
        when(activeCartRepository.findByUserId("user-123")).thenReturn(Optional.of(cartWithTwo));

        cartService.addItem("user-123", "prod-001", 1);

        verify(aiRecommenderPort).suggest(eq("user-123"), argThat(ids ->
                ids.size() == 2 && ids.contains("prod-001") && ids.contains("prod-002")));
    }

    // =========================================================================
    // removeItem — lógica de negocio
    // =========================================================================

    @Test
    void should_returnFailure_when_cartNotFoundOnRemove() {
        when(activeCartRepository.findByUserId("user-123")).thenReturn(Optional.empty());

        Result<Cart, AppError> result = cartService.removeItem("user-123", "prod-001", 1);

        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError().code()).isEqualTo("cart.not_found");
    }

    @Test
    void should_removeItem_when_cartExists() {
        when(activeCartRepository.findByUserId("user-123")).thenReturn(Optional.of(cartWithLaptop()));

        Result<Cart, AppError> result = cartService.removeItem("user-123", "prod-001", 1);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getValue().items()).isEmpty();
        verify(activeCartRepository).save(any(Cart.class));
    }

    // =========================================================================
    // removeItem — infraestructura: findByUserId
    // =========================================================================

    @Test
    void should_returnFailure_when_findByUserIdThrowsInfrastructureException_onRemoveItem() {
        when(activeCartRepository.findByUserId("user-123"))
                .thenThrow(new InfrastructureException("cart.redis.unavailable"));

        Result<Cart, AppError> result = cartService.removeItem("user-123", "prod-001", 1);

        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError().code()).isEqualTo("active.cart.infra.failed");
    }

    @Test
    void should_rethrow_when_findByUserIdThrowsUnexpectedException_onRemoveItem() {
        when(activeCartRepository.findByUserId("user-123"))
                .thenThrow(new RuntimeException("Unexpected error"));

        assertThatThrownBy(() -> cartService.removeItem("user-123", "prod-001", 1))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Unexpected error");
    }

    // =========================================================================
    // removeItem — infraestructura: save
    // =========================================================================

    @Test
    void should_returnFailure_when_saveThrowsInfrastructureException_onRemoveItem() {
        when(activeCartRepository.findByUserId("user-123")).thenReturn(Optional.of(cartWithLaptop()));
        doThrow(new InfrastructureException("cart.redis.unavailable"))
                .when(activeCartRepository).save(any(Cart.class));

        Result<Cart, AppError> result = cartService.removeItem("user-123", "prod-001", 1);

        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError().code()).isEqualTo("active.cart.infra.failed");
    }

    @Test
    void should_rethrow_when_saveThrowsUnexpectedException_onRemoveItem() {
        when(activeCartRepository.findByUserId("user-123")).thenReturn(Optional.of(cartWithLaptop()));
        doThrow(new RuntimeException("Unexpected save error"))
                .when(activeCartRepository).save(any(Cart.class));

        assertThatThrownBy(() -> cartService.removeItem("user-123", "prod-001", 1))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Unexpected save error");
    }

    // =========================================================================
    // getCart
    // =========================================================================

    @Test
    void should_returnCart_when_activeCartExists() {
        when(activeCartRepository.findByUserId("user-123")).thenReturn(Optional.of(cartWithLaptop()));

        Result<Cart, AppError> result = cartService.getCart("user-123");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getValue().items()).hasSize(1);
    }

    @Test
    void should_returnFailure_when_noActiveCartExists() {
        when(activeCartRepository.findByUserId("user-123")).thenReturn(Optional.empty());

        Result<Cart, AppError> result = cartService.getCart("user-123");

        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError().code()).isEqualTo("cart.not_found");
    }

    @Test
    void should_returnFailure_when_findByUserIdThrowsInfrastructureException_onGetCart() {
        when(activeCartRepository.findByUserId("user-123"))
                .thenThrow(new InfrastructureException("cart.redis.unavailable"));

        Result<Cart, AppError> result = cartService.getCart("user-123");

        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError().code()).isEqualTo("active.cart.infra.failed");
    }

    @Test
    void should_rethrow_when_findByUserIdThrowsUnexpectedException_onGetCart() {
        when(activeCartRepository.findByUserId("user-123"))
                .thenThrow(new RuntimeException("Unexpected error"));

        assertThatThrownBy(() -> cartService.getCart("user-123"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Unexpected error");
    }

    // =========================================================================
    // clearCart — lógica de negocio
    // =========================================================================

    @Test
    void should_returnFailure_when_cartNotFoundOnClear() {
        when(activeCartRepository.findByUserId("user-123")).thenReturn(Optional.empty());

        Result<Void, AppError> result = cartService.clearCart("user-123");

        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError().code()).isEqualTo("cart.not_found");
    }

    @Test
    void should_clearCart_when_cartExists() {
        when(activeCartRepository.findByUserId("user-123")).thenReturn(Optional.of(cartWithLaptop()));

        Result<Void, AppError> result = cartService.clearCart("user-123");

        assertThat(result.isSuccess()).isTrue();
        verify(activeCartRepository).deleteByUserId("user-123");
    }

    @Test
    void should_alsoDeleteBackup_when_clearCartSucceeds() {
        when(activeCartRepository.findByUserId("user-123")).thenReturn(Optional.of(cartWithLaptop()));

        cartService.clearCart("user-123");

        verify(cartBackupRepository).deleteByUserId("user-123");
    }

    @Test
    void should_continueFlow_when_backupDeleteFails() {
        when(activeCartRepository.findByUserId("user-123")).thenReturn(Optional.of(cartWithLaptop()));
        doThrow(new RuntimeException("MongoDB down"))
                .when(cartBackupRepository).deleteByUserId(any());

        Result<Void, AppError> result = cartService.clearCart("user-123");

        assertThat(result.isSuccess()).isTrue();
    }

    // =========================================================================
    // clearCart — infraestructura: findByUserId
    // =========================================================================

    @Test
    void should_returnFailure_when_findByUserIdThrowsInfrastructureException_onClearCart() {
        when(activeCartRepository.findByUserId("user-123"))
                .thenThrow(new InfrastructureException("cart.redis.unavailable"));

        Result<Void, AppError> result = cartService.clearCart("user-123");

        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError().code()).isEqualTo("active.cart.infra.failed");
    }

    @Test
    void should_rethrow_when_findByUserIdThrowsUnexpectedException_onClearCart() {
        when(activeCartRepository.findByUserId("user-123"))
                .thenThrow(new RuntimeException("Unexpected error"));

        assertThatThrownBy(() -> cartService.clearCart("user-123"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Unexpected error");
    }

    // =========================================================================
    // clearCart — infraestructura: deleteByUserId
    // =========================================================================

    @Test
    void should_returnFailure_when_deleteThrowsInfrastructureException_onClearCart() {
        when(activeCartRepository.findByUserId("user-123")).thenReturn(Optional.of(cartWithLaptop()));
        doThrow(new InfrastructureException("cart.redis.unavailable"))
                .when(activeCartRepository).deleteByUserId("user-123");

        Result<Void, AppError> result = cartService.clearCart("user-123");

        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError().code()).isEqualTo("active.cart.infra.failed");
    }

    @Test
    void should_rethrow_when_deleteThrowsUnexpectedException_onClearCart() {
        when(activeCartRepository.findByUserId("user-123")).thenReturn(Optional.of(cartWithLaptop()));
        doThrow(new RuntimeException("Unexpected delete error"))
                .when(activeCartRepository).deleteByUserId("user-123");

        assertThatThrownBy(() -> cartService.clearCart("user-123"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Unexpected delete error");
    }
}