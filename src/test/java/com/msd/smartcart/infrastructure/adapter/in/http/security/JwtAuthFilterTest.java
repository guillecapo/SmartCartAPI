package com.msd.smartcart.infrastructure.adapter.in.http.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock private JwtService jwtService;
    @Mock private UserDetailsService userDetailsService;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;

    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthFilter(jwtService, userDetailsService);
        // Limpiamos el contexto de seguridad entre tests
        SecurityContextHolder.clearContext();
    }

    private UserDetails userDetails(String email) {
        return User.builder()
                .username(email)
                .password("encoded")
                .roles("USER")
                .build();
    }

    // =========================================================================
    // Sin header Authorization → pasa al siguiente filtro sin autenticar
    // =========================================================================

    @Test
    void should_continueChain_when_authorizationHeaderIsMissing() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jwtService);
    }

    @Test
    void should_continueChain_when_authorizationHeaderDoesNotStartWithBearer() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jwtService);
    }

    // =========================================================================
    // Token inválido → pasa al siguiente filtro sin autenticar
    // =========================================================================

    @Test
    void should_continueChain_when_tokenExtractionFails() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer invalid-token");
        when(jwtService.extractUsername("invalid-token"))
                .thenThrow(new RuntimeException("Malformed JWT"));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    // =========================================================================
    // Token válido → autentica y continúa la cadena
    // =========================================================================

    @Test
    void should_setAuthentication_when_tokenIsValid() throws Exception {
        UserDetails userDetails = userDetails("john@example.com");

        when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");
        when(jwtService.extractUsername("valid-token")).thenReturn("john@example.com");
        when(userDetailsService.loadUserByUsername("john@example.com")).thenReturn(userDetails);
        when(jwtService.isValid("valid-token", userDetails)).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                .isEqualTo("john@example.com");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void should_notSetAuthentication_when_tokenIsInvalid() throws Exception {
        UserDetails userDetails = userDetails("john@example.com");

        when(request.getHeader("Authorization")).thenReturn("Bearer expired-token");
        when(jwtService.extractUsername("expired-token")).thenReturn("john@example.com");
        when(userDetailsService.loadUserByUsername("john@example.com")).thenReturn(userDetails);
        when(jwtService.isValid("expired-token", userDetails)).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    // =========================================================================
    // Usuario ya autenticado → no reautentica
    // =========================================================================

    @Test
    void should_skipAuthentication_when_contextAlreadyHasAuthentication() throws Exception {
        UserDetails userDetails = userDetails("john@example.com");

        // Primera pasada: autentica
        when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");
        when(jwtService.extractUsername("valid-token")).thenReturn("john@example.com");
        when(userDetailsService.loadUserByUsername("john@example.com")).thenReturn(userDetails);
        when(jwtService.isValid("valid-token", userDetails)).thenReturn(true);
        filter.doFilterInternal(request, response, filterChain);

        // Segunda pasada con contexto ya poblado
        filter.doFilterInternal(request, response, filterChain);

        // loadUserByUsername solo debe llamarse una vez
        verify(userDetailsService, times(1)).loadUserByUsername("john@example.com");
    }
}