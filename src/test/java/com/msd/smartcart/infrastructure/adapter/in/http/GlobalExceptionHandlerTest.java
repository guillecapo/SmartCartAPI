package com.msd.smartcart.infrastructure.adapter.in.http;

import com.msd.smartcart.shared.AppError;
import com.msd.smartcart.shared.exception.InfrastructureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void should_return503_when_infrastructureExceptionIsThrown() {
        InfrastructureException ex = new InfrastructureException("redis.unavailable");

        ResponseEntity<AppError> response = handler.handleInfrastructureException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void should_returnServiceUnavailableErrorCode_when_infrastructureExceptionIsThrown() {
        ResponseEntity<AppError> response =
                handler.handleInfrastructureException(new InfrastructureException("any"));

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("service.unavailable");
    }

    @Test
    void should_return500_when_unexpectedExceptionIsThrown() {
        ResponseEntity<AppError> response =
                handler.handleUnexpectedException(new RuntimeException("Unexpected"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void should_returnInternalErrorCode_when_unexpectedExceptionIsThrown() {
        ResponseEntity<AppError> response =
                handler.handleUnexpectedException(new RuntimeException("Unexpected"));

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("internal.error");
    }
}