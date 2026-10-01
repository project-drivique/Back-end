package com.drivique.api.service;

import com.drivique.api.model.PasswordPolicy;
import com.drivique.api.repository.PasswordPolicyRepository;

import com.drivique.api.config.PasswordConfig;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PasswordValidatorServiceTests {
    PasswordPolicyRepository repo;
    PasswordPolicy policy;
    PasswordValidatorService validator;
    @BeforeEach
    void setup() {
        repo = mock(PasswordPolicyRepository.class);
        policy = mock(PasswordPolicy.class);
        when(repo.findByActiveTrue()).thenReturn(List.of(policy));
        when(policy.getMinLength()).thenReturn((short) 12);
        when(policy.isRequireUppercase()).thenReturn(true);
        when(policy.isRequireNumber()).thenReturn(true);
        when(policy.isRequireSymbol()).thenReturn(true);
        validator = new PasswordValidatorService(repo);
    }
    @Test
    void acceptsStrongPasswordAndUsesCost12WithRandomSalt() {
        String password = "ValidPassword12!";
        validator.validate(password);
        var encoder = new PasswordConfig().passwordEncoder();
        String hash = encoder.encode(password);
        assertThat(hash).startsWith("$2a$12$").doesNotContain(password);
        assertThat(encoder.matches(password, hash)).isTrue();
        assertThat(encoder.matches("OtherPassword12!", hash)).isFalse();
        assertThat(encoder.encode(password)).isNotEqualTo(hash);
    }
    @ParameterizedTest
    @ValueSource(strings = {"Short1!", "lowercaseonly12!", "NoNumbersHere!", "NoSymbolsHere12", "NoSymbolsHere12 ", ""})
    void rejectsEachMissingRequirement(String password) {
        assertThatThrownBy(() -> validator.validate(password)).isInstanceOfSatisfying(
                ResponseStatusException.class, ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }
    @Test
    void respectsPolicyChangesWithoutCache() {
        when(policy.isRequireUppercase()).thenReturn(false);
        when(policy.isRequireNumber()).thenReturn(false);
        when(policy.isRequireSymbol()).thenReturn(false);
        validator.validate("lowercasepassword");
        when(policy.getMinLength()).thenReturn((short) 24);
        assertThatThrownBy(() -> validator.validate("lowercasepassword")).isInstanceOf(ResponseStatusException.class);
    }
    @Test
    void missingOrAmbiguousPolicyFailsClosed() {
        when(repo.findByActiveTrue()).thenReturn(List.of());
        assertThatThrownBy(() -> validator.validate("ValidPassword12!")).isInstanceOfSatisfying(
                ResponseStatusException.class, ex -> assertThat(ex.getStatusCode().value()).isEqualTo(503));
        when(repo.findByActiveTrue()).thenReturn(List.of(policy, policy));
        assertThatThrownBy(() -> validator.validate("ValidPassword12!")).isInstanceOfSatisfying(
                ResponseStatusException.class, ex -> assertThat(ex.getStatusCode().value()).isEqualTo(503));
    }
    @Test
    void enforcesUtf8ByteLimitRatherThanOnlyCharacterCount() {
        validator.validate("A1!" + "x".repeat(69));
        assertThatThrownBy(() -> validator.validate("A1!" + "x".repeat(70))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> validator.validate("A1!" + "é".repeat(35))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> validator.validate(null)).isInstanceOf(ResponseStatusException.class);
    }
}
