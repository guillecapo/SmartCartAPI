package com.msd.smartcart.shared.exception;

public class DuplicateOrderException extends RuntimeException {
    public DuplicateOrderException(String orderId) {
        super("Duplicate order: " + orderId);
    }
}
