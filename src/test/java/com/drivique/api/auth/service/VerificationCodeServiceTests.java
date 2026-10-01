package com.drivique.api.service;

import com.drivique.api.model.User;
import com.drivique.api.model.VerificationCode;
import com.drivique.api.repository.VerificationCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class VerificationCodeServiceTests {

    @Mock
    private VerificationCodeRepository codeRepository;

    @Mock
    private JwtService jwtService;

    private VerificationCodeService verificationCodeService;
    private User testUser;

    @BeforeEach
    void setUp() {
        verificationCodeService = new VerificationCodeService(codeRepository, jwtService);
        testUser = new User("Laura", "Perez", "laura@drivique.com", "$2a$12$hash");
        testUser.setId(UUID.randomUUID());
    }

    @Test
    void createVerificationCodeGeneratesValidSixDigitOtpAndSavesHash() {
        when(jwtService.hashToken(anyString())).thenReturn("hashed_otp_value");

        String rawOtp = verificationCodeService.createVerificationCode(testUser, "ACCOUNT_VERIFICATION", 15);

        assertThat(rawOtp).isNotNull().matches("^[0-9]{6}$");

        ArgumentCaptor<VerificationCode> captor = ArgumentCaptor.forClass(VerificationCode.class);
        verify(codeRepository, times(1)).save(captor.capture());

        VerificationCode saved = captor.getValue();
        assertThat(saved.getUser()).isEqualTo(testUser);
        assertThat(saved.getPurpose()).isEqualTo("ACCOUNT_VERIFICATION");
        assertThat(saved.getCodeHash()).isEqualTo("hashed_otp_value");
        assertThat(saved.getExpiresAt()).isAfter(Instant.now());
        assertThat(saved.getUsedAt()).isNull();
    }

    @Test
    void validateAndConsumeSuccessMarksCodeAsUsed() {
        String rawOtp = "123456";
        String hashed = "hash_123456";
        VerificationCode code = new VerificationCode(testUser, "ACCOUNT_VERIFICATION", hashed, Instant.now().plus(10, ChronoUnit.MINUTES));

        when(jwtService.hashToken(rawOtp)).thenReturn(hashed);
        when(codeRepository.findByUserAndPurposeAndCodeHash(testUser, "ACCOUNT_VERIFICATION", hashed))
                .thenReturn(Optional.of(code));

        verificationCodeService.validateAndConsume(testUser, "ACCOUNT_VERIFICATION", rawOtp);

        assertThat(code.isUsed()).isTrue();
        assertThat(code.getUsedAt()).isNotNull();
        verify(codeRepository, times(1)).save(code);
    }

    @Test
    void validateAndConsumeExpiredCodeThrowsBadRequest() {
        String rawOtp = "123456";
        String hashed = "hash_123456";
        VerificationCode code = new VerificationCode(testUser, "PASSWORD_RESET", hashed, Instant.now().minus(5, ChronoUnit.MINUTES));

        when(jwtService.hashToken(rawOtp)).thenReturn(hashed);
        when(codeRepository.findByUserAndPurposeAndCodeHash(testUser, "PASSWORD_RESET", hashed))
                .thenReturn(Optional.of(code));

        assertThatThrownBy(() -> verificationCodeService.validateAndConsume(testUser, "PASSWORD_RESET", rawOtp))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void validateAndConsumeAlreadyUsedCodeThrowsBadRequest() {
        String rawOtp = "123456";
        String hashed = "hash_123456";
        VerificationCode code = new VerificationCode(testUser, "PASSWORD_RESET", hashed, Instant.now().plus(10, ChronoUnit.MINUTES));
        code.markUsed();

        when(jwtService.hashToken(rawOtp)).thenReturn(hashed);
        when(codeRepository.findByUserAndPurposeAndCodeHash(testUser, "PASSWORD_RESET", hashed))
                .thenReturn(Optional.of(code));

        assertThatThrownBy(() -> verificationCodeService.validateAndConsume(testUser, "PASSWORD_RESET", rawOtp))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void validateAndConsumeNotFoundThrowsBadRequest() {
        when(jwtService.hashToken("999999")).thenReturn("hash_999999");
        when(codeRepository.findByUserAndPurposeAndCodeHash(testUser, "PASSWORD_RESET", "hash_999999"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> verificationCodeService.validateAndConsume(testUser, "PASSWORD_RESET", "999999"))
                .isInstanceOf(ResponseStatusException.class);
    }
}
