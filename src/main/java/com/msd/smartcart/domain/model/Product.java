package com.msd.smartcart.domain.model;

import com.msd.smartcart.shared.annotation.ExcludeFromCoverage;

import java.math.BigDecimal;

@ExcludeFromCoverage
public record Product(
        String productId,
        String name,
        String description,
        BigDecimal unitPrice,
        int stock
) {}