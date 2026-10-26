package com.drivique.api.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Fallo de inicio de sesión con un código estable (INVALID_PASSWORD, ACCOUNT_NOT_FOUND, EMAIL_NOT_VERIFIED,
 * ACCOUNT_DISABLED, ACCOUNT_LOCKED) para que web y app muestren un mensaje claro en el idioma de la persona.
 */
public class LoginFailedException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final String field;
    private final Map<String, Object> meta;

    public LoginFailedException(HttpStatus status, String code, String field, String message, Map<String, Object> meta) {
        super(message);
        this.status = status;
        this.code = code;
        this.field = field;
        this.meta = meta == null ? Map.of() : meta;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
    public String getField() { return field; }
    public Map<String, Object> getMeta() { return meta; }
}
