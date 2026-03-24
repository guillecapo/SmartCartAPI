package com.msd.smartcart.domain.model;

import com.msd.smartcart.shared.annotation.ExcludeFromCoverage;

@ExcludeFromCoverage
public record UserData(
        String userId,
        String email,
        String fullName,
        String encodedPassword,
        String role
) {}