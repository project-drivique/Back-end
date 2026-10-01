package com.drivique.api.service;

import com.drivique.api.model.User;
import com.drivique.api.repository.UserRepository;
import com.drivique.api.dto.UpdateUserPreferenceRequestDTO;
import com.drivique.api.dto.UserPreferenceResponseDTO;
import com.drivique.api.model.UserPreference;
import com.drivique.api.repository.UserPreferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserPreferenceServiceTests {

    @Mock
    private UserPreferenceRepository preferenceRepository;

    @Mock
    private UserRepository userRepository;

    private UserPreferenceService preferenceService;
    private User testUser;

    @BeforeEach
    void setUp() {
        preferenceService = new UserPreferenceService(preferenceRepository, userRepository);

        testUser = new User("Carlos", "Gomez", "carlos@drivique.com", "$2a$12$hash");
        testUser.setId(UUID.randomUUID());
    }

    @Test
    void getPreferencesExistingReturnsRecord() {
        UserPreference preference = new UserPreference(testUser);
        preference.setThemePreference("DARK");
        preference.setEmailNotifications(true);
        preference.setSmsNotifications(false);

        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("carlos@drivique.com"))
                .thenReturn(Optional.of(testUser));
        when(preferenceRepository.findByUserId(testUser.getId())).thenReturn(Optional.of(preference));

        UserPreferenceResponseDTO response = preferenceService.getPreferences("carlos@drivique.com");

        assertThat(response).isNotNull();
        assertThat(response.themePreference()).isEqualTo("DARK");
        assertThat(response.emailNotifications()).isTrue();
        assertThat(response.smsNotifications()).isFalse();
    }

    @Test
    void getPreferencesCreatesDefaultWhenNotPresent() {
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("carlos@drivique.com"))
                .thenReturn(Optional.of(testUser));
        when(preferenceRepository.findByUserId(testUser.getId())).thenReturn(Optional.empty());
        when(preferenceRepository.save(any(UserPreference.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserPreferenceResponseDTO response = preferenceService.getPreferences("carlos@drivique.com");

        assertThat(response).isNotNull();
        assertThat(response.themePreference()).isEqualTo("SYSTEM");
        assertThat(response.emailNotifications()).isTrue();
        assertThat(response.smsNotifications()).isTrue();
        verify(preferenceRepository, times(1)).save(any(UserPreference.class));
    }

    @Test
    void updatePreferencesModifiesAndPersistsRecord() {
        UserPreference preference = new UserPreference(testUser);
        UUID langId = UUID.randomUUID();
        UUID currId = UUID.randomUUID();

        UpdateUserPreferenceRequestDTO request = new UpdateUserPreferenceRequestDTO(
                langId,
                currId,
                "DARK",
                false,
                true
        );

        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("carlos@drivique.com"))
                .thenReturn(Optional.of(testUser));
        when(preferenceRepository.findByUserId(testUser.getId())).thenReturn(Optional.of(preference));
        when(preferenceRepository.save(any(UserPreference.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserPreferenceResponseDTO response = preferenceService.updatePreferences("carlos@drivique.com", request);

        assertThat(response.languageId()).isEqualTo(langId);
        assertThat(response.currencyId()).isEqualTo(currId);
        assertThat(response.themePreference()).isEqualTo("DARK");
        assertThat(response.emailNotifications()).isFalse();
        assertThat(response.smsNotifications()).isTrue();
        verify(preferenceRepository, times(1)).save(preference);
    }
}
