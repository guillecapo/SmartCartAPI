package com.msd.smartcart.shared.exception;

public class InfrastructureException extends RuntimeException {
    public InfrastructureException(String code) {
        super("Infrastructure failure: " + code);
    }
}