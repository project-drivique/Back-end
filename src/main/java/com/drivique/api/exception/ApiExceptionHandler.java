package com.drivique.api.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
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
        LOG.warn("404 {} -> {}", request.getRequestURI(), ex.getMessage());
        String detail = resolveNotFoundMessage(ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ApiProblem.of(HttpStatus.NOT_FOUND, request.getRequestURI(), detail, List.of()));
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<Object> conflict(ConflictException ex, HttpServletRequest request) {
        LOG.warn("409 {} -> {}", request.getRequestURI(), ex.getMessage());
        String detail = resolveConflictMessage(ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ApiProblem.of(HttpStatus.CONFLICT, request.getRequestURI(), detail, List.of()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Object> denied(AccessDeniedException ex, HttpServletRequest request) {
        LOG.warn("403 {} -> {}", request.getRequestURI(), ex.getMessage());
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
        LOG.warn("400 {} -> {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ApiProblem.of(HttpStatus.BAD_REQUEST, request.getRequestURI(), ex.getMessage(), List.of()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Object> dataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        LOG.warn("409 DB Integrity {} -> {}", request.getRequestURI(), ex.getMessage());
        String msg = "La operación no se pudo completar porque ya existe un registro con información en conflicto.";
        String exMsg = ex.getMessage() != null ? ex.getMessage().toLowerCase() : "";
        if (exMsg.contains("uq_users_document_number") || exMsg.contains("document_number")) {
            msg = "El número de documento ingresado ya se encuentra registrado con otra cuenta de usuario.";
        }
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ApiProblem.of(HttpStatus.CONFLICT, request.getRequestURI(), msg, List.of()));
    }

    private static String resolveConflictMessage(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank() || rawMessage.contains("sensitive-internal")) {
            return "No se pudo procesar la solicitud debido a un conflicto con el estado actual.";
        }
        if (rawMessage.contains("ya se encuentra registrado con otra cuenta")) {
            return rawMessage;
        }
        return switch (rawMessage.trim()) {
            case "Promotion already used by this user" -> "Ya has utilizado este cupón en una reserva anterior.";
            case "Promotion is not active" -> "El cupón no se encuentra activo o ha finalizado su periodo de vigencia.";
            case "Promotion use limit reached" -> "Este cupón ha alcanzado el límite máximo de usos disponibles.";
            case "Promotion conditions are not met" -> "La reserva no cumple las condiciones mínimas requeridas para este cupón.";
            case "Promotion not found" -> "El cupón ingresado no existe o no se encuentra disponible.";
            case "Vehicle does not match category" -> "El vehículo seleccionado no corresponde a la categoría de la promoción.";
            default -> rawMessage;
        };
    }

    private static String resolveNotFoundMessage(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank() || rawMessage.contains("sensitive-internal")) {
            return "El recurso solicitado no fue encontrado.";
        }
        return switch (rawMessage.trim()) {
            case "Promotion not found" -> "El cupón ingresado no existe o no se encuentra disponible.";
            case "Vehicle not found" -> "El vehículo seleccionado no fue encontrado.";
            case "Active vehicle not found" -> "El vehículo seleccionado no está disponible actualmente.";
            case "User not found" -> "Usuario no encontrado.";
            case "Active insurance coverage not found" -> "La cobertura de seguro seleccionada no está disponible.";
            case "Active mileage plan not found" -> "El plan de kilometraje seleccionado no está disponible.";
            case "Active additional service not found" -> "Uno de los servicios adicionales seleccionados no está disponible.";
            default -> rawMessage;
        };
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    ResponseEntity<Object> handleResponseStatus(org.springframework.web.server.ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String reason = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ApiProblem.of(status, request.getRequestURI(), reason, List.of()));
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
