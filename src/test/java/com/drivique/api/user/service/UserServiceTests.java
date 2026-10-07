package com.drivique.api.user.service;

import com.drivique.api.dto.UpdateUserProfileRequestDTO;
import com.drivique.api.dto.UserProfileDetailResponseDTO;
import com.drivique.api.exception.ResourceNotFoundException;
import com.drivique.api.model.Role;
import com.drivique.api.model.User;
import com.drivique.api.repository.*;
import com.drivique.api.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

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

    @Mock
    private UserSessionRepository userSessionRepository;

    @Mock
    private UserPreferenceRepository userPreferenceRepository;

    @Mock
    private UserSocialAccountRepository userSocialAccountRepository;

    @Mock
    private UserConsentRepository userConsentRepository;

    @Mock
    private VerificationCodeRepository verificationCodeRepository;

    @Mock
    private UserSavedPaymentMethodRepository userSavedPaymentMethodRepository;

    @Mock
    private UserFavoriteVehicleRepository userFavoriteVehicleRepository;

    @Mock
    private UserCouponUsageRepository userCouponUsageRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserDocumentRepository userDocumentRepository;

    @Mock
    private BranchUserRepository branchUserRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private RentalContractRepository rentalContractRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;
    private User testUser;

    @BeforeEach
    void setUp() {
        userService = new UserService(
                userRepository,
                userSessionRepository,
                userPreferenceRepository,
                userSocialAccountRepository,
                userConsentRepository,
                verificationCodeRepository,
                userSavedPaymentMethodRepository,
                userFavoriteVehicleRepository,
                userCouponUsageRepository,
                notificationRepository,
                userDocumentRepository,
                branchUserRepository,
                reservationRepository,
                rentalContractRepository,
                passwordEncoder
        );

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
        assertThat(testUser.getEmail()).isEqualTo("carlos@drivique.com");
    }

    @Test
    void deleteAccountWithoutOperationsPerformsFullPhysicalDeletion() {
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("carlos@drivique.com"))
                .thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("ValidPassword123", "$2a$12$hashedPassword")).thenReturn(true);
        when(reservationRepository.existsByCustomerId(testUser.getId())).thenReturn(false);
        when(rentalContractRepository.existsByCustomerId(testUser.getId())).thenReturn(false);

        userService.deleteAccount("carlos@drivique.com", "ValidPassword123");

        verify(userSessionRepository, times(1)).revokeAllActiveSessionsForUser(eq(testUser), any());
        verify(userPreferenceRepository, times(1)).deleteByUserId(testUser.getId());
        verify(userSessionRepository, times(1)).deleteByUser(testUser);
        verify(userSocialAccountRepository, times(1)).deleteByUserId(testUser.getId());
        verify(userConsentRepository, times(1)).deleteByUserId(testUser.getId());
        verify(verificationCodeRepository, times(1)).deleteByUser(testUser);
        verify(userSavedPaymentMethodRepository, times(1)).deleteByUser(testUser);
        verify(userFavoriteVehicleRepository, times(1)).deleteByUserId(testUser.getId());
        verify(userCouponUsageRepository, times(1)).deleteByUser(testUser);
        verify(notificationRepository, times(1)).deleteByUserId(testUser.getId());
        verify(userDocumentRepository, times(1)).deleteByUserId(testUser.getId());
        verify(branchUserRepository, times(1)).deleteByUserId(testUser.getId());

        verify(userRepository, times(1)).delete(testUser);
        verify(userRepository, never()).save(any());
        assertThat(testUser.getRoles()).isEmpty();
    }

    @Test
    void deleteAccountWithOperationsAnonymizesProfileAndPreservesOperationalRecords() {
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("carlos@drivique.com"))
                .thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("ValidPassword123", "$2a$12$hashedPassword")).thenReturn(true);
        when(reservationRepository.existsByCustomerId(testUser.getId())).thenReturn(true);

        userService.deleteAccount("carlos@drivique.com", "ValidPassword123");

        verify(userSessionRepository, times(1)).revokeAllActiveSessionsForUser(eq(testUser), any());
        verify(userPreferenceRepository, times(1)).deleteByUserId(testUser.getId());
        verify(userSavedPaymentMethodRepository, times(1)).deleteByUser(testUser);
        verify(userSocialAccountRepository, times(1)).deleteByUserId(testUser.getId());
        verify(userFavoriteVehicleRepository, times(1)).deleteByUserId(testUser.getId());
        verify(verificationCodeRepository, times(1)).deleteByUser(testUser);
        verify(notificationRepository, times(1)).deleteByUserId(testUser.getId());

        verify(userRepository, never()).delete(any());
        verify(userRepository, times(1)).save(testUser);

        assertThat(testUser.getAccountStatus()).isEqualTo("DELETED");
        assertThat(testUser.getDeletedAt()).isNotNull();
        assertThat(testUser.getFirstName()).isEqualTo("Usuario");
        assertThat(testUser.getLastName()).isEqualTo("Eliminado");
        assertThat(testUser.getPhone()).isNull();
        assertThat(testUser.getBirthDate()).isNull();
        assertThat(testUser.getNationalityId()).isNull();
        assertThat(testUser.getPasswordHash()).startsWith("DELETED_");
        assertThat(testUser.isLocked()).isTrue();
        assertThat(testUser.getRoles()).isEmpty();
    }

    @Test
    void deleteAccountWithInvalidPasswordThrowsBadCredentials() {
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("carlos@drivique.com"))
                .thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("WrongPassword", "$2a$12$hashedPassword")).thenReturn(false);

        assertThatThrownBy(() -> userService.deleteAccount("carlos@drivique.com", "WrongPassword"))
                .isInstanceOf(BadCredentialsException.class);

        verify(userSessionRepository, never()).revokeAllActiveSessionsForUser(any(), any());
        verify(userRepository, never()).delete(any());
        verify(userRepository, never()).save(any());
    }
}
