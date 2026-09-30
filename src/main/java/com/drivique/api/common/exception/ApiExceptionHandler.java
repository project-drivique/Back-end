package com.drivique.api.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ApiProblem.ValidationError> errors = ex.getBindingResult().getAllErrors().stream()
                .map(error -> new ApiProblem.ValidationError(
                        error instanceof org.springframework.validation.FieldError field ? field.getField() : "request",
                        error.getCode() == null ? "Invalid" : error.getCode(),
                        "El valor no cumple la validación requerida."))
                .toList();
        return response(HttpStatus.BAD_REQUEST, path(request), headers, errors);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (status.is5xxServerError()) {
            LOG.error("Unexpected MVC error", ex);
        }
        return response(HttpStatus.valueOf(status.value()), path(request), headers, List.of());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<Object> notFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, request.getRequestURI(), HttpHeaders.EMPTY, List.of());
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<Object> conflict(ConflictException ex, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, request.getRequestURI(), HttpHeaders.EMPTY, List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Object> denied(AccessDeniedException ex, HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, request.getRequestURI(), HttpHeaders.EMPTY, List.of());
    }

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<Object> unauthorized(BadCredentialsException ex, HttpServletRequest request) {
        return response(HttpStatus.UNAUTHORIZED, request.getRequestURI(), HttpHeaders.EMPTY, List.of());
    }

    @ExceptionHandler(AccountLockedException.class)
    ResponseEntity<Object> locked(AccountLockedException ex, HttpServletRequest request) {
        long minutes = ex.getRemainingMinutes();
        String message = minutes > 0
                ? "Cuenta temporalmente bloqueada. Intente de nuevo en " + minutes + " minuto(s)."
                : "Cuenta temporalmente bloqueada por seguridad.";
        return ResponseEntity.status(HttpStatus.LOCKED)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ApiProblem.of(HttpStatus.LOCKED, request.getRequestURI(), message, List.of()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Object> badRequest(IllegalArgumentException ex, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, request.getRequestURI(), HttpHeaders.EMPTY, List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> unexpected(Exception ex, HttpServletRequest request) {
        LOG.error("Unexpected API error", ex);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, request.getRequestURI(), HttpHeaders.EMPTY, List.of());
    }

    private static String path(WebRequest request) {
        return ((ServletWebRequest) request).getRequest().getRequestURI();
    }

    private static ResponseEntity<Object> response(HttpStatus status, String instance,
            HttpHeaders headers, List<ApiProblem.ValidationError> errors) {
        return ResponseEntity.status(status).headers(headers).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ApiProblem.of(status, instance, errors));
    }
}
