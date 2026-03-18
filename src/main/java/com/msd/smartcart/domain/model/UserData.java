package com.msd.smartcart.domain.model;

public record UserData(
        String userId,
        String email,
        String fullName,
        String encodedPassword,
        String role
) {}