package com.drivique.api.service;

import com.drivique.api.dto.UpdateUserProfileRequestDTO;
import com.drivique.api.dto.UserProfileDetailResponseDTO;
import com.drivique.api.exception.ResourceNotFoundException;
import com.drivique.api.model.Role;
import com.drivique.api.model.User;
import com.drivique.api.repository.*;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final UserSocialAccountRepository userSocialAccountRepository;
    private final UserConsentRepository userConsentRepository;
    private final VerificationCodeRepository verificationCodeRepository;
    private final UserSavedPaymentMethodRepository userSavedPaymentMethodRepository;
    private final UserFavoriteVehicleRepository userFavoriteVehicleRepository;
    private final UserCouponUsageRepository userCouponUsageRepository;
    private final NotificationRepository notificationRepository;
    private final UserDocumentRepository userDocumentRepository;
    private final BranchUserRepository branchUserRepository;
    private final ReservationRepository reservationRepository;
    private final RentalContractRepository rentalContractRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            UserSessionRepository userSessionRepository,
            UserPreferenceRepository userPreferenceRepository,
            UserSocialAccountRepository userSocialAccountRepository,
            UserConsentRepository userConsentRepository,
            VerificationCodeRepository verificationCodeRepository,
            UserSavedPaymentMethodRepository userSavedPaymentMethodRepository,
            UserFavoriteVehicleRepository userFavoriteVehicleRepository,
            UserCouponUsageRepository userCouponUsageRepository,
            NotificationRepository notificationRepository,
            UserDocumentRepository userDocumentRepository,
            BranchUserRepository branchUserRepository,
            ReservationRepository reservationRepository,
            RentalContractRepository rentalContractRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.userSessionRepository = userSessionRepository;
        this.userPreferenceRepository = userPreferenceRepository;
        this.userSocialAccountRepository = userSocialAccountRepository;
        this.userConsentRepository = userConsentRepository;
        this.verificationCodeRepository = verificationCodeRepository;
        this.userSavedPaymentMethodRepository = userSavedPaymentMethodRepository;
        this.userFavoriteVehicleRepository = userFavoriteVehicleRepository;
        this.userCouponUsageRepository = userCouponUsageRepository;
        this.notificationRepository = notificationRepository;
        this.userDocumentRepository = userDocumentRepository;
        this.branchUserRepository = branchUserRepository;
        this.reservationRepository = reservationRepository;
        this.rentalContractRepository = rentalContractRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public UserProfileDetailResponseDTO getProfile(String email) {
        User user = findUserByEmail(email);
        return mapToDetailDTO(user);
    }

    @Transactional
    public UserProfileDetailResponseDTO updateProfile(String email, UpdateUserProfileRequestDTO request) {
        User user = findUserByEmail(email);

        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setPhone(request.phone());
        user.setBirthDate(request.birthDate());
        user.setNationalityId(request.nationalityId());

        boolean isComplete = user.getFirstName() != null && !user.getFirstName().isBlank()
                && user.getLastName() != null && !user.getLastName().isBlank()
                && user.getPhone() != null && !user.getPhone().isBlank()
                && user.getBirthDate() != null
                && user.getDocumentNumber() != null && !user.getDocumentNumber().isBlank();
        user.setProfileComplete(isComplete);
        user.setUpdatedAt(Instant.now());

        User saved = userRepository.save(user);
        return mapToDetailDTO(saved);
    }

    @Transactional
    public void deleteAccount(String email, String password) {
        User user = findUserByEmail(email);
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadCredentialsException("Credenciales inválidas.");
        }

        userSessionRepository.revokeAllActiveSessionsForUser(user, Instant.now());

        boolean hasOperations = reservationRepository.existsByCustomerId(user.getId())
                || rentalContractRepository.existsByCustomerId(user.getId());

        if (!hasOperations) {
            // Usuario sin operaciones: borrado físico completo de cuenta y datos asociados
            deleteNonOperationalUserData(user);
            userConsentRepository.deleteByUserId(user.getId());
            userDocumentRepository.deleteByUserId(user.getId());
            branchUserRepository.deleteByUserId(user.getId());
            clearUserRoles(user);
            userRepository.delete(user);
        } else {
            // Usuario con contratos/operaciones: se borran datos personales y volátiles no requeridos
            deleteNonOperationalUserData(user);

            // Anonimización del perfil y bloqueo permanente manteniendo la referencia para conservación legal
            user.setFirstName("Usuario");
            user.setLastName("Eliminado");
            user.setPhone(null);
            user.setBirthDate(null);
            user.setNationalityId(null);
            user.setPasswordHash("DELETED_" + UUID.randomUUID());
            user.setAccountStatus("DELETED");
            user.setDeletedAt(Instant.now());
            user.setProfileComplete(false);
            user.setLockedUntil(Instant.now().plusSeconds(36500L * 86400L));
            clearUserRoles(user);
            user.setUpdatedAt(Instant.now());
            userRepository.save(user);
        }
    }

    private void clearUserRoles(User user) {
        if (user.getRoles() != null) {
            try {
                user.getRoles().clear();
            } catch (UnsupportedOperationException e) {
                user.setRoles(new java.util.HashSet<>());
            }
        }
    }

    private void deleteNonOperationalUserData(User user) {
        UUID userId = user.getId();
        userPreferenceRepository.deleteByUserId(userId);
        userSessionRepository.deleteByUser(user);
        userSocialAccountRepository.deleteByUserId(userId);
        verificationCodeRepository.deleteByUser(user);
        userSavedPaymentMethodRepository.deleteByUser(user);
        userFavoriteVehicleRepository.deleteByUserId(userId);
        userCouponUsageRepository.deleteByUser(user);
        notificationRepository.deleteByUserId(userId);
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con el correo: " + email));
    }

    private UserProfileDetailResponseDTO mapToDetailDTO(User user) {
        List<String> roleCodes = user.getRoles().stream()
                .map(Role::getCode)
                .toList();

        return new UserProfileDetailResponseDTO(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhone(),
                user.getDocumentTypeId(),
                user.getDocumentNumber(),
                user.getBirthDate(),
                user.getNationalityId(),
                roleCodes,
                user.isProfileComplete(),
                user.getAccountStatus(),
                user.getEmailVerifiedAt(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
