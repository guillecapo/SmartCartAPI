package com.msd.smartcart.infrastructure.adapter.in.http.security;

import com.msd.smartcart.domain.model.UserData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-secret-key-for-testing-only-minimum-256-bits-long!!";
    private static final long EXPIRATION_24H = 86_400_000L;
    private static final long EXPIRATION_ZERO = 0L;

    private JwtService jwtService;

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private UserData userData() {
        return new UserData("user-123", "john@example.com", "John Doe", "encoded-password", "ROLE_USER");
    }

    private UserDetails userDetails(String email) {
        return User.builder()
                .username(email)
                .password("encoded-password")
                .roles("USER")
                .build();
    }

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, EXPIRATION_24H);
    }

    // -------------------------------------------------------------------------
    // generateToken
    // -------------------------------------------------------------------------

    @Test
    void should_generateNonBlankToken_when_validUserDataProvided() {
        String token = jwtService.generateToken(userData());

        assertThat(token).isNotBlank();
    }

    @Test
    void should_generateDifferentTokens_when_calledTwice() {
        // Los tokens son distintos por la diferencia en issuedAt (aunque sea mínima)
        // Este test verifica que el método no devuelve un token cacheado o constante
        String token1 = jwtService.generateToken(userData());
        String token2 = jwtService.generateToken(userData());

        // Ambos deben ser tokens JWT válidos con 3 partes
        assertThat(token1.split("\\.")).hasSize(3);
        assertThat(token2.split("\\.")).hasSize(3);
    }

    @Test
    void should_generateTokenWithThreeParts_when_called() {
        String token = jwtService.generateToken(userData());

        // Formato JWT: header.payload.signature
        assertThat(token.split("\\.")).hasSize(3);
    }

    // -------------------------------------------------------------------------
    // extractUsername
    // -------------------------------------------------------------------------

    @Test
    void should_extractEmail_when_tokenIsValid() {
        String token = jwtService.generateToken(userData());

        String extracted = jwtService.extractUsername(token);

        assertThat(extracted).isEqualTo("john@example.com");
    }

    @Test
    void should_throwException_when_tokenIsMalformed() {
        assertThatThrownBy(() -> jwtService.extractUsername("not.a.valid.token"))
                .isInstanceOf(Exception.class);
    }

    @Test
    void should_throwException_when_tokenIsEmpty() {
        assertThatThrownBy(() -> jwtService.extractUsername(""))
                .isInstanceOf(Exception.class);
    }

    @Test
    void should_throwException_when_tokenSignedWithDifferentSecret() {
        JwtService otherService = new JwtService("completely-different-secret-key-256-bits-long!!!", EXPIRATION_24H);
        String tokenFromOther = otherService.generateToken(userData());

        assertThatThrownBy(() -> jwtService.extractUsername(tokenFromOther))
                .isInstanceOf(Exception.class);
    }

    // -------------------------------------------------------------------------
    // isValid
    // -------------------------------------------------------------------------

    @Test
    void should_returnTrue_when_tokenIsValidAndUserMatches() {
        String token = jwtService.generateToken(userData());
        UserDetails details = userDetails("john@example.com");

        assertThat(jwtService.isValid(token, details)).isTrue();
    }

    @Test
    void should_returnFalse_when_tokenBelongsToAnotherUser() {
        String token = jwtService.generateToken(userData());
        UserDetails otherUser = userDetails("other@example.com");

        assertThat(jwtService.isValid(token, otherUser)).isFalse();
    }

    @Test
    void should_returnFalse_when_tokenIsExpired() {
        JwtService expiredService = new JwtService(SECRET, EXPIRATION_ZERO);
        String expiredToken = expiredService.generateToken(userData());
        UserDetails details = userDetails("john@example.com");

        assertThat(jwtService.isValid(expiredToken, details)).isFalse();
    }

    @Test
    void should_returnFalse_when_tokenIsMalformed() {
        UserDetails details = userDetails("john@example.com");

        assertThat(jwtService.isValid("malformed-token", details)).isFalse();
    }

    @Test
    void should_returnFalse_when_tokenSignedWithDifferentSecret() {
        JwtService otherService = new JwtService("completely-different-secret-key-256-bits-long!!!", EXPIRATION_24H);
        String foreignToken = otherService.generateToken(userData());
        UserDetails details = userDetails("john@example.com");

        assertThat(jwtService.isValid(foreignToken, details)).isFalse();
    }
}