package com.drivique.api.exception;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;

@Schema(name = "ApiProblem", description = "RFC 7807 error with safe validation details")
public record ApiProblem(
        String type, String title, int status, String detail, String instance,
        @Schema(format = "date-time") String timestamp, List<ValidationError> errors,
        @Schema(description = "Datos extra para mostrar el error (por ejemplo intentos restantes)") java.util.Map<String, Object> meta) {

    public record ValidationError(String field, String code, String message) {}

    public static ApiProblem of(HttpStatus status, String instance) {
        return of(status, instance, List.of());
    }

    public static ApiProblem of(HttpStatus status, String instance, List<ValidationError> errors) {
        String detail = switch (status) {
            case BAD_REQUEST -> "La solicitud contiene datos inválidos.";
            case UNAUTHORIZED -> "Se requiere autenticación válida.";
            case FORBIDDEN -> "No tiene permiso para realizar esta operación.";
            case NOT_FOUND -> "El recurso solicitado no existe.";
            case CONFLICT -> "La operación no se pudo completar debido a un conflicto con el estado actual.";
            case LOCKED -> "La cuenta se encuentra temporalmente bloqueada.";
            case METHOD_NOT_ALLOWED -> "El método HTTP no está permitido para este recurso.";
            case UNSUPPORTED_MEDIA_TYPE -> "El tipo de contenido no es compatible.";
            case NOT_ACCEPTABLE -> "No se puede generar el formato solicitado.";
            default -> status.is5xxServerError()
                    ? "Ocurrió un error interno. Intente nuevamente más tarde."
                    : "No se pudo procesar la solicitud.";
        };
        return of(status, instance, detail, errors);
    }

    public static ApiProblem of(HttpStatus status, String instance, String detail, List<ValidationError> errors) {
        return of(status, instance, detail, errors, java.util.Map.of());
    }

    public static ApiProblem of(HttpStatus status, String instance, String detail, List<ValidationError> errors,
                                java.util.Map<String, Object> meta) {
        return new ApiProblem("about:blank", status.getReasonPhrase(), status.value(), detail,
                instance, Instant.now().toString(), List.copyOf(errors), java.util.Map.copyOf(meta));
    }
}
