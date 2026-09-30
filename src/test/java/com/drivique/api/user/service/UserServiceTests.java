package com.drivique.api.user.service;

import com.drivique.api.auth.entity.Role;
import com.drivique.api.auth.entity.User;
import com.drivique.api.auth.repository.UserRepository;
import com.drivique.api.common.exception.ResourceNotFoundException;
import com.drivique.api.user.dto.UpdateUserProfileRequestDTO;
import com.drivique.api.user.dto.UserProfileDetailResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTests {

    @Mock
    private UserRepository userRepository;

    private UserService userService;
    private User testUser;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository);

        Role customerRole = new Role(UUID.randomUUID(), "CUSTOMER", "Customer", "Customer role", true);
        testUser = new User("Carlos", "Gomez", "carlos@drivique.com", "$2a$12$hashedPassword");
        testUser.setId(UUID.randomUUID());
        testUser.setPhone("+573001234567");
        testUser.setDocumentNumber("123456789");
        testUser.setBirthDate(LocalDate.of(1995, 8, 20));
        testUser.setRoles(Set.of(customerRole));
    }

    @Test
    void getProfileReturnsCompleteDetails() {
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("carlos@drivique.com"))
                .thenReturn(Optional.of(testUser));

        UserProfileDetailResponseDTO profile = userService.getProfile("carlos@drivique.com");

        assertThat(profile).isNotNull();
        assertThat(profile.email()).isEqualTo("carlos@drivique.com");
        assertThat(profile.firstName()).isEqualTo("Carlos");
        assertThat(profile.lastName()).isEqualTo("Gomez");
        assertThat(profile.phone()).isEqualTo("+573001234567");
        assertThat(profile.roles()).containsExactly("CUSTOMER");
    }

    @Test
    void getProfileNotFoundThrowsResourceNotFoundException() {
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("unknown@drivique.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getProfile("unknown@drivique.com"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateProfileModifiesAllowedFieldsAndRecalculatesProfileComplete() {
        UpdateUserProfileRequestDTO request = new UpdateUserProfileRequestDTO(
                "Carlos Andres",
                "Gomez Perez",
                "+573119876543",
                LocalDate.of(1994, 10, 12),
                UUID.randomUUID()
        );

        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("carlos@drivique.com"))
                .thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserProfileDetailResponseDTO updated = userService.updateProfile("carlos@drivique.com", request);

        assertThat(updated.firstName()).isEqualTo("Carlos Andres");
        assertThat(updated.lastName()).isEqualTo("Gomez Perez");
        assertThat(updated.phone()).isEqualTo("+573119876543");
        assertThat(updated.birthDate()).isEqualTo(LocalDate.of(1994, 10, 12));
        assertThat(updated.profileComplete()).isTrue();
        assertThat(testUser.getEmail()).isEqualTo("carlos@drivique.com"); // Email unchanged
    }
}
