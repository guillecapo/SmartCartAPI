package com.msd.smartcart.domain.error;

import com.msd.smartcart.shared.AppError;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CartErrorTest {

    @Test
    void should_createNotFoundError_when_notFoundOfCalled() {
        AppError error = CartError.NotFound.of("user-123");

        assertThat(error.code()).isEqualTo("cart.not_found");
        assertThat(error.context()).isEqualTo("user-123");
    }

    @Test
    void should_createOutOfStockError_when_outOfStockOfCalled() {
        AppError error = CartError.OutOfStock.of("prod-001");

        assertThat(error.code()).isEqualTo("cart.out_of_stock");
        assertThat(error.context()).isEqualTo("prod-001");
    }

    @Test
    void should_createInvalidQuantityError_when_invalidQuantityOfCalled() {
        AppError error = CartError.InvalidQuantity.of(0);

        assertThat(error.code()).isEqualTo("cart.invalid_quantity");
        assertThat(error.context()).isEqualTo("0");
    }

    @Test
    void should_createEmptyCartError_when_emptyCartOfCalled() {
        AppError error = CartError.EmptyCart.of("user-123");

        assertThat(error.code()).isEqualTo("cart.empty");
        assertThat(error.context()).isEqualTo("user-123");
    }

    @Test
    void should_createUnavailableProductsError_when_unavailableProductsOfCalled() {
        AppError error = CartError.UnavailableProducts.of("user-123");

        assertThat(error.code()).isEqualTo("cart.unavailable_products");
        assertThat(error.context()).isEqualTo("user-123");
    }

    @Test
    void should_createProductNotFoundError_when_productNotFoundOfCalled() {
        AppError error = CartError.ProductNotFound.of("prod-001");

        assertThat(error.code()).isEqualTo("cart.product_not_found");
        assertThat(error.context()).isEqualTo("prod-001");
    }

    @Test
    void should_createCartOverflowError_when_cartOverflowOfCalled() {
        AppError error = CartError.CartOverflow.of("user-123");

        assertThat(error.code()).isEqualTo("cart.overflow");
        assertThat(error.context()).isEqualTo("user-123");
    }
}