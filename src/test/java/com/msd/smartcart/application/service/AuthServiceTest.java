package com.msd.smartcart.application.service;

import com.msd.smartcart.domain.model.UserData;
import com.msd.smartcart.domain.port.out.UserRepository;
import com.msd.smartcart.infrastructure.adapter.in.http.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private UserRepository userRepository;
    @Mock private JwtService jwtService;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private UserData savedUser() {
        return new UserData("user-123", "john@example.com", "John Doe", "encoded-pass", "ROLE_USER");
    }

    private AuthService.LoginRequest loginRequest() {
        return new AuthService.LoginRequest("john@example.com", "plain-password");
    }

    private AuthService.RegisterRequest registerRequest() {
        return new AuthService.RegisterRequest("john@example.com", "plain-password", "John Doe");
    }

    // -------------------------------------------------------------------------
    // login — happy path
    // -------------------------------------------------------------------------

    @Test
    void should_returnToken_when_credentialsAreValid() {
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(savedUser()));
        when(jwtService.generateToken(any(UserData.class))).thenReturn("jwt-token-abc");

        AuthService.AuthResponse response = authService.login(loginRequest());

        assertThat(response.token()).isEqualTo("jwt-token-abc");
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void should_authenticateWithCorrectCredentials_when_loginCalled() {
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(savedUser()));
        when(jwtService.generateToken(any())).thenReturn("token");

        authService.login(loginRequest());

        verify(authenticationManager).authenticate(
                argThat(auth -> {
                    UsernamePasswordAuthenticationToken token = (UsernamePasswordAuthenticationToken) auth;
                    return token.getPrincipal().equals("john@example.com")
                            && token.getCredentials().equals("plain-password");
                })
        );
    }

    @Test
    void should_generateTokenWithCorrectUser_when_loginSucceeds() {
        UserData user = savedUser();
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(user)).thenReturn("jwt-token-xyz");

        AuthService.AuthResponse response = authService.login(loginRequest());

        verify(jwtService).generateToken(user);
        assertThat(response.token()).isEqualTo("jwt-token-xyz");
    }

    // -------------------------------------------------------------------------
    // login — fallos
    // -------------------------------------------------------------------------

    @Test
    void should_throwException_when_credentialsAreInvalid() {
        doThrow(new BadCredentialsException("Bad credentials"))
                .when(authenticationManager).authenticate(any());

        assertThatThrownBy(() -> authService.login(loginRequest()))
                .isInstanceOf(BadCredentialsException.class);

        verifyNoInteractions(userRepository);
        verifyNoInteractions(jwtService);
    }

    @Test
    void should_throwException_when_userNotFoundAfterAuthentication() {
        // Auth pasa pero el usuario fue borrado entre authenticate y findByEmail
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(loginRequest()))
                .isInstanceOf(Exception.class);
    }

    // -------------------------------------------------------------------------
    // register — happy path
    // -------------------------------------------------------------------------

    @Test
    void should_returnToken_when_registrationIsSuccessful() {
        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(passwordEncoder.encode("plain-password")).thenReturn("encoded-pass");
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(savedUser()));
        when(jwtService.generateToken(any())).thenReturn("jwt-new-token");

        AuthService.AuthResponse response = authService.register(registerRequest());

        assertThat(response.token()).isEqualTo("jwt-new-token");
    }

    @Test
    void should_saveUserWithEncodedPassword_when_registering() {
        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(passwordEncoder.encode("plain-password")).thenReturn("encoded-pass");
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(savedUser()));
        when(jwtService.generateToken(any())).thenReturn("token");

        authService.register(registerRequest());

        verify(userRepository).save(argThat(user ->
                user.email().equals("john@example.com")
                        && user.fullName().equals("John Doe")
                        && user.encodedPassword().equals("encoded-pass")
                        && user.role().equals("ROLE_USER")
        ));
    }

    @Test
    void should_neverStoreRawPassword_when_registering() {
        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(passwordEncoder.encode("plain-password")).thenReturn("encoded-pass");
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(savedUser()));
        when(jwtService.generateToken(any())).thenReturn("token");

        authService.register(registerRequest());

        verify(userRepository).save(argThat(user ->
                !user.encodedPassword().equals("plain-password")
        ));
    }

    // -------------------------------------------------------------------------
    // register — email duplicado
    // -------------------------------------------------------------------------

    @Test
    void should_throwIllegalArgumentException_when_emailAlreadyRegistered() {
        when(userRepository.existsByEmail("john@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already registered");

        verify(userRepository, never()).save(any());
        verifyNoInteractions(jwtService);
    }

    @Test
    void should_notEncodePassword_when_emailIsAlreadyTaken() {
        when(userRepository.existsByEmail("john@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest()))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(passwordEncoder);
    }
}