package com.drivique.api.service;

import com.drivique.api.model.User;
import com.drivique.api.model.VerificationCode;
import com.drivique.api.repository.VerificationCodeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class VerificationCodeService {

    private final VerificationCodeRepository codeRepository;
    private final JwtService jwtService;
    private final SecureRandom secureRandom = new SecureRandom();

    public VerificationCodeService(VerificationCodeRepository codeRepository, JwtService jwtService) {
        this.codeRepository = codeRepository;
        this.jwtService = jwtService;
    }

    public String generateNumericOtp() {
        int number = secureRandom.nextInt(1_000_000);
        return String.format("%06d", number);
    }

    @Transactional
    public String createVerificationCode(User user, String purpose, int expirationMinutes) {
        String rawOtp = generateNumericOtp();
        String codeHash = jwtService.hashToken(rawOtp);
        Instant expiresAt = Instant.now().plus(expirationMinutes, ChronoUnit.MINUTES);

        VerificationCode verificationCode = new VerificationCode(user, purpose, codeHash, expiresAt);
        codeRepository.save(verificationCode);

        return rawOtp;
    }

    @Transactional(readOnly = true)
    public void validate(User user, String purpose, String rawCode) {
        String codeHash = jwtService.hashToken(rawCode);
        VerificationCode verificationCode = codeRepository.findByUserAndPurposeAndCodeHash(user, purpose, codeHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Código de verificación inválido o expirado."));
        if (!verificationCode.isValid()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Código de verificación inválido o expirado.");
        }
    }

    @Transactional
    public void validateAndConsume(User user, String purpose, String rawCode) {
        validate(user, purpose, rawCode);
        String codeHash = jwtService.hashToken(rawCode);
        VerificationCode verificationCode = codeRepository.findByUserAndPurposeAndCodeHash(user, purpose, codeHash).orElseThrow();
        verificationCode.markUsed();
        codeRepository.save(verificationCode);
    }
}
