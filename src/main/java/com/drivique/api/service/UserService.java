package com.drivique.api.service;

import com.drivique.api.model.Role;
import com.drivique.api.model.User;
import com.drivique.api.repository.UserRepository;
import com.drivique.api.exception.ResourceNotFoundException;
import com.drivique.api.dto.UpdateUserProfileRequestDTO;
import com.drivique.api.dto.UserProfileDetailResponseDTO;
import com.drivique.api.repository.UserSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            UserSessionRepository userSessionRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.userSessionRepository = userSessionRepository;
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
        user.setAccountStatus("DELETED");
        user.setDeletedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
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
