package com.msd.smartcart.domain.model;

public record User(
        String userId,
        String email,
        String fullName
) {}
