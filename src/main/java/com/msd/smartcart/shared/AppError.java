package com.msd.smartcart.shared;

public record AppError(
        String code,
        String message,
        String context
) {

    public static AppError persistence(String code, String context) {
        return new AppError(code, "A persistence error occurred", context);
    }

    public static AppError unauthorized(String context) {
        return new AppError("unauthorized", "Unauthorized access", context);
    }

    public static AppError notFound(String code, String context) {
        return new AppError(code, "Resource not found", context);
    }

    public static AppError internal(String code, String context) {
        return new AppError(code, "An internal error occurred", context);
    }
}