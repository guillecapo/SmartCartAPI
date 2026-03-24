package com.msd.smartcart.infrastructure.adapter.in.http;

import com.msd.smartcart.application.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock private AuthService authService;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(authService);
    }

    @Test
    void should_return200_when_loginSucceeds() {
        when(authService.login(any())).thenReturn(new AuthService.AuthResponse("jwt-token"));

        ResponseEntity<?> response = controller.login(
                new AuthService.LoginRequest("john@example.com", "pass"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void should_return401_when_loginFails() {
        when(authService.login(any())).thenThrow(new RuntimeException("Bad credentials"));

        ResponseEntity<?> response = controller.login(
                new AuthService.LoginRequest("john@example.com", "wrong"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void should_return201_when_registerSucceeds() {
        when(authService.register(any())).thenReturn(new AuthService.AuthResponse("jwt-token"));

        ResponseEntity<?> response = controller.register(
                new AuthService.RegisterRequest("john@example.com", "pass", "John"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void should_return409_when_emailAlreadyRegistered() {
        when(authService.register(any()))
                .thenThrow(new IllegalArgumentException("Email already registered"));

        ResponseEntity<?> response = controller.register(
                new AuthService.RegisterRequest("john@example.com", "pass", "John"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}