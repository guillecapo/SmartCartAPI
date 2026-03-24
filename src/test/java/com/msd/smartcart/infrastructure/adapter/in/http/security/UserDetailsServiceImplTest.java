package com.msd.smartcart.infrastructure.adapter.in.http.security;

import com.msd.smartcart.domain.model.UserData;
import com.msd.smartcart.domain.port.out.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserDetailsServiceImpl userDetailsService;

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private UserData userData() {
        return new UserData("user-123", "john@example.com", "John Doe", "encoded-pass", "USER");
    }

    // -------------------------------------------------------------------------
    // loadUserByUsername — happy path
    // -------------------------------------------------------------------------

    @Test
    void should_returnUserDetails_when_emailExists() {
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(userData()));

        UserDetails result = userDetailsService.loadUserByUsername("john@example.com");

        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo("john@example.com");
    }

    @Test
    void should_setEncodedPasswordOnUserDetails_when_userFound() {
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(userData()));

        UserDetails result = userDetailsService.loadUserByUsername("john@example.com");

        assertThat(result.getPassword()).isEqualTo("encoded-pass");
    }

    @Test
    void should_includeCorrectAuthority_when_userHasRoleUser() {
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(userData()));

        UserDetails result = userDetailsService.loadUserByUsername("john@example.com");

        boolean hasRole = result.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_USER"));
        assertThat(hasRole).isTrue();
    }

    @Test
    void should_includeCorrectAuthority_when_userHasRoleAdmin() {
        UserData admin = new UserData("user-999", "admin@example.com", "Admin", "encoded-pass", "ADMIN");
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));

        UserDetails result = userDetailsService.loadUserByUsername("admin@example.com");

        boolean hasRole = result.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        assertThat(hasRole).isTrue();
    }

    @Test
    void should_delegateToRepository_when_loadByUsernameCalled() {
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(userData()));

        userDetailsService.loadUserByUsername("john@example.com");

        verify(userRepository, times(1)).findByEmail("john@example.com");
    }

    // -------------------------------------------------------------------------
    // loadUserByUsername — usuario no encontrado
    // -------------------------------------------------------------------------

    @Test
    void should_throwUsernameNotFoundException_when_emailDoesNotExist() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("unknown@example.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void should_includeEmailInExceptionMessage_when_userNotFound() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("unknown@example.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("unknown@example.com");
    }
}