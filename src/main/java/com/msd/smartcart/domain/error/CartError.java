package com.msd.smartcart.domain.error;

import com.msd.smartcart.shared.AppError;

public sealed interface CartError permits
        CartError.NotFound,
        CartError.OutOfStock,
        CartError.InvalidQuantity,
        CartError.EmptyCart,
        CartError.UnavailableProducts,
        CartError.ProductNotFound,
        CartError.CartOverflow {

    record NotFound(String userId) implements CartError {
        public static AppError of(String userId) {
            return new AppError("cart.not_found", "Cart not found for user", userId);
        }
    }

    record OutOfStock(String productId) implements CartError {
        public static AppError of(String productId) {
            return new AppError("cart.out_of_stock", "Product is out of stock", productId);
        }
    }

    record InvalidQuantity(int quantity) implements CartError {
        public static AppError of(int quantity) {
            return new AppError("cart.invalid_quantity",
                    "Quantity must be greater than zero", String.valueOf(quantity));
        }
    }

    record EmptyCart(String userId) implements CartError {
        public static AppError of(String userId) {
            return new AppError("cart.empty", "Cart is empty", userId);
        }
    }

    record UnavailableProducts(String userId) implements CartError {
        public static AppError of(String userId) {
            return new AppError("cart.unavailable_products",
                    "Some products are unavailable", userId);
        }
    }

    record ProductNotFound(String productId) implements CartError {
        public static AppError of(String productId) {
            return new AppError("cart.product_not_found",
                    "Product not found in catalog", productId);
        }
    }

    record CartOverflow(String userId) implements CartError {
        public static AppError of(String userId) {
            return new AppError("cart.overflow",
                    "Too many products for this cart", userId);
        }
    }
}