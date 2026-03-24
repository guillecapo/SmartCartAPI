package com.msd.smartcart.domain.model;

import com.msd.smartcart.shared.annotation.ExcludeFromCoverage;

@ExcludeFromCoverage
public record User(
        String userId,
        String email,
        String fullName
) {}
