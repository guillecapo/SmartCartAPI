// src/main/java/com/msd/smartcart/infrastructure/adapter/in/http/GlobalExceptionHandler.java

package com.msd.smartcart.infrastructure.adapter.in.http;

import com.msd.smartcart.shared.AppError;
import com.msd.smartcart.shared.exception.InfrastructureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InfrastructureException.class)
    public ResponseEntity<AppError> handleInfrastructureException(InfrastructureException e) {
        log.error("Infrastructure failure reached controller — {}", e.getMessage(), e);
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(AppError.persistence("service.unavailable", "A required service is temporarily unavailable"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<AppError> handleUnexpectedException(Exception e) {
        log.error("Unexpected error reached controller — {}", e.getMessage(), e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(AppError.internal("internal.error", "An unexpected error occurred"));
    }
}