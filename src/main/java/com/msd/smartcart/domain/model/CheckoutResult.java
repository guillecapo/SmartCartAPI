package com.msd.smartcart.domain.model;

import java.util.List;

public sealed interface CheckoutResult
        permits CheckoutResult.Confirmed,
        CheckoutResult.Rejected,
        CheckoutResult.OutOfStock,
        CheckoutResult.InfrastructureFailure {

    record Confirmed(Order order) implements CheckoutResult {}

    record Rejected(String reason) implements CheckoutResult {}

    record OutOfStock(
            List<OutOfStockItem> products
    ) implements CheckoutResult {}

    record OutOfStockItem(
            String productId,
            String productName,
            int requestedQuantity,
            int availableStock
    ) {}

    record InfrastructureFailure(String cause) implements CheckoutResult {}
}