package com.drivique.api.service;

import com.drivique.api.model.User;
import com.drivique.api.repository.UserRepository;
import com.drivique.api.exception.ResourceNotFoundException;
import com.drivique.api.dto.UpdateUserPreferenceRequestDTO;
import com.drivique.api.dto.UserPreferenceResponseDTO;
import com.drivique.api.model.UserPreference;
import com.drivique.api.repository.UserPreferenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class UserPreferenceService {

    private final UserPreferenceRepository preferenceRepository;
    private final UserRepository userRepository;

    public UserPreferenceService(UserPreferenceRepository preferenceRepository, UserRepository userRepository) {
        this.preferenceRepository = preferenceRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public UserPreferenceResponseDTO getPreferences(String email) {
        User user = findUserByEmail(email);
        UserPreference preference = preferenceRepository.findByUserId(user.getId())
                .orElseGet(() -> preferenceRepository.save(new UserPreference(user)));

        return mapToDTO(preference);
    }

    @Transactional
    public UserPreferenceResponseDTO updatePreferences(String email, UpdateUserPreferenceRequestDTO request) {
        User user = findUserByEmail(email);
        UserPreference preference = preferenceRepository.findByUserId(user.getId())
                .orElseGet(() -> new UserPreference(user));

        if (request.languageId() != null) {
            preference.setLanguageId(request.languageId());
        }
        if (request.currencyId() != null) {
            preference.setCurrencyId(request.currencyId());
        }
        if (request.themePreference() != null && !request.themePreference().isBlank()) {
            preference.setThemePreference(request.themePreference());
        }
        if (request.emailNotifications() != null) {
            preference.setEmailNotifications(request.emailNotifications());
        }
        if (request.smsNotifications() != null) {
            preference.setSmsNotifications(request.smsNotifications());
        }
        preference.setUpdatedAt(Instant.now());

        UserPreference saved = preferenceRepository.save(preference);
        return mapToDTO(saved);
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con el correo: " + email));
    }

    private UserPreferenceResponseDTO mapToDTO(UserPreference preference) {
        return new UserPreferenceResponseDTO(
                preference.getUserId(),
                preference.getLanguageId(),
                preference.getCurrencyId(),
                preference.getThemePreference(),
                preference.isEmailNotifications(),
                preference.isSmsNotifications(),
                preference.getUpdatedAt()
        );
    }
}
