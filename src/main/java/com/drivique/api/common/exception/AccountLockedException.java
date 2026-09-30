package com.drivique.api.common.exception;

import java.time.Duration;
import java.time.Instant;

public class AccountLockedException extends RuntimeException {
    private final Instant lockedUntil;

    public AccountLockedException(Instant lockedUntil) {
        super("Cuenta temporalmente bloqueada debido a múltiples intentos fallidos.");
        this.lockedUntil = lockedUntil;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public long getRemainingMinutes() {
        if (lockedUntil == null) return 0;
        long seconds = Duration.between(Instant.now(), lockedUntil).toSeconds();
        return seconds > 0 ? (seconds + 59) / 60 : 0;
    }
}
