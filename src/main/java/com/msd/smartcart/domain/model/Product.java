package com.msd.smartcart.domain.model;

import java.math.BigDecimal;

public record Product(
        String productId,
        String name,
        String description,
        BigDecimal unitPrice,
        int stock
) {}