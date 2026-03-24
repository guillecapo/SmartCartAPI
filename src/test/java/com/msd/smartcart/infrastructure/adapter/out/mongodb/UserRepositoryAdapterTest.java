package com.msd.smartcart.infrastructure.adapter.out.mongodb;

import com.msd.smartcart.domain.model.UserData;
import com.msd.smartcart.infrastructure.adapter.out.mongodb.document.UserDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserRepositoryAdapterTest {

    @Mock private UserMongoRepository userMongoRepository;

    private UserRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new UserRepositoryAdapter(userMongoRepository);
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private UserDocument userDocument() {
        return UserDocument.builder()
                .id("user-123")
                .email("john@example.com")
                .fullName("John Doe")
                .password("encoded-pass")
                .role("ROLE_USER")
                .build();
    }

    // =========================================================================
    // findByEmail
    // =========================================================================

    @Test
    void should_returnEmpty_when_emailNotFound() {
        when(userMongoRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        Optional<UserData> result = adapter.findByEmail("unknown@example.com");

        assertThat(result).isEmpty();
    }

    @Test
    void should_returnMappedUserData_when_documentFound() {
        when(userMongoRepository.findByEmail("john@example.com"))
                .thenReturn(Optional.of(userDocument()));

        Optional<UserData> result = adapter.findByEmail("john@example.com");

        assertThat(result).isPresent();
        assertThat(result.get().userId()).isEqualTo("user-123");
        assertThat(result.get().email()).isEqualTo("john@example.com");
        assertThat(result.get().fullName()).isEqualTo("John Doe");
        assertThat(result.get().encodedPassword()).isEqualTo("encoded-pass");
        assertThat(result.get().role()).isEqualTo("ROLE_USER");
    }

    // =========================================================================
    // existsByEmail
    // =========================================================================

    @Test
    void should_returnTrue_when_emailExists() {
        when(userMongoRepository.existsByEmail("john@example.com")).thenReturn(true);

        assertThat(adapter.existsByEmail("john@example.com")).isTrue();
    }

    @Test
    void should_returnFalse_when_emailDoesNotExist() {
        when(userMongoRepository.existsByEmail("unknown@example.com")).thenReturn(false);

        assertThat(adapter.existsByEmail("unknown@example.com")).isFalse();
    }

    // =========================================================================
    // save
    // =========================================================================

    @Test
    void should_saveDocumentWithCorrectFields_when_saveCalledWithUserData() {
        UserData userData = new UserData(null, "john@example.com", "John Doe", "encoded-pass", "ROLE_USER");

        adapter.save(userData);

        ArgumentCaptor<UserDocument> captor = ArgumentCaptor.forClass(UserDocument.class);
        verify(userMongoRepository).save(captor.capture());

        UserDocument saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("john@example.com");
        assertThat(saved.getFullName()).isEqualTo("John Doe");
        assertThat(saved.getPassword()).isEqualTo("encoded-pass");
        assertThat(saved.getRole()).isEqualTo("ROLE_USER");
    }

    @Test
    void should_neverSetIdManually_when_savingNewUser() {
        UserData userData = new UserData(null, "john@example.com", "John Doe", "encoded-pass", "ROLE_USER");

        adapter.save(userData);

        ArgumentCaptor<UserDocument> captor = ArgumentCaptor.forClass(UserDocument.class);
        verify(userMongoRepository).save(captor.capture());

        // El id debe ser nulo para que MongoDB lo genere automáticamente
        assertThat(captor.getValue().getId()).isNull();
    }

    @Test
    void should_delegateToRepository_when_saveIsCalled() {
        UserData userData = new UserData(null, "john@example.com", "John Doe", "encoded-pass", "ROLE_USER");

        adapter.save(userData);

        verify(userMongoRepository, times(1)).save(any(UserDocument.class));
    }
}