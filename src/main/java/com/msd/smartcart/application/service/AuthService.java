package com.msd.smartcart.application.service;

import com.msd.smartcart.domain.model.UserData;
import com.msd.smartcart.domain.port.out.UserRepository;
import com.msd.smartcart.infrastructure.adapter.in.http.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public record LoginRequest(String email, String password) {}
    public record RegisterRequest(String email, String password, String fullName) {}
    public record AuthResponse(String token) {}

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );
        UserData userData = userRepository.findByEmail(request.email()).orElseThrow();
        String token = jwtService.generateToken(userData);
        log.info("Login successful [email={}]", request.email());
        return new AuthResponse(token);
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already registered");
        }

        UserData newUser = new UserData(
                null,
                request.email(),
                request.fullName(),
                passwordEncoder.encode(request.password()),
                "ROLE_USER"
        );

        userRepository.save(newUser);
        log.info("User registered [email={}]", request.email());

        UserData saved = userRepository.findByEmail(request.email()).orElseThrow();
        String token = jwtService.generateToken(saved);
        return new AuthResponse(token);
    }
}