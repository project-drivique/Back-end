package com.drivique.api.auth.service;

import com.drivique.api.auth.repository.PasswordPolicyRepository;

import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PasswordValidatorService {
    private final PasswordPolicyRepository policies;
    public PasswordValidatorService(PasswordPolicyRepository policies) { this.policies = policies; }

    @Transactional(readOnly = true)
    public void validate(String password) {
        var active = policies.findByActiveTrue();
        if (active.size() != 1 || active.getFirst().getMinLength() < 8
                || active.getFirst().getMinLength() > 72) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        }
        var policy = active.getFirst();
        // BCrypt accepts at most 72 UTF-8 bytes; never truncate or trim the password.
        if (password == null || password.isBlank() || password.length() > 72
                || password.getBytes(StandardCharsets.UTF_8).length > 72
                || password.codePointCount(0, password.length()) < policy.getMinLength()
                || (policy.isRequireUppercase() && password.codePoints().noneMatch(Character::isUpperCase))
                || (policy.isRequireNumber() && password.codePoints().noneMatch(c -> c >= '0' && c <= '9'))
                || (policy.isRequireSymbol() && password.codePoints().noneMatch(PasswordValidatorService::isSymbol))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
    }

    private static boolean isSymbol(int c) {
        int type = Character.getType(c);
        return switch (type) {
            case Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION,
                 Character.START_PUNCTUATION, Character.END_PUNCTUATION,
                 Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION,
                 Character.OTHER_PUNCTUATION, Character.MATH_SYMBOL,
                 Character.CURRENCY_SYMBOL, Character.MODIFIER_SYMBOL, Character.OTHER_SYMBOL -> true;
            default -> false;
        };
    }
}
