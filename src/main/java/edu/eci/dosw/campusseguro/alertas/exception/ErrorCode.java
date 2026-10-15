package edu.eci.dosw.campusseguro.alertas.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Los datos enviados no son válidos"),
    INVALID_REFERENCE(HttpStatus.BAD_REQUEST, "Una o más referencias no existen"),
    INVALID_PAGINATION(HttpStatus.BAD_REQUEST, "Los parámetros de consulta no son válidos"),
    INVALID_ACTIVATION_DATE(HttpStatus.BAD_REQUEST, "La fecha de activación no es válida"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Autenticación requerida o token inválido"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "No tiene permisos para realizar esta operación"),
    NOT_A_RECIPIENT(HttpStatus.FORBIDDEN, "No es destinatario de esta alerta"),
    ALERT_NOT_FOUND(HttpStatus.NOT_FOUND, "La alerta no existe"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "El recurso solicitado no existe"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Método HTTP no permitido"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de contenido no soportado"),
    INVALID_ALERT_STATE(HttpStatus.CONFLICT, "La alerta no está en un estado que admita esta operación"),
    INVALID_TRANSITION(HttpStatus.CONFLICT, "La transición de estado no es válida"),
    ALERT_NOT_ACTIVE(HttpStatus.CONFLICT, "La alerta no está activa"),
    SIMULATION_MISMATCH(HttpStatus.CONFLICT, "El simulacro no corresponde a la alerta"),
    DUPLICATE_SIMULATION_ALERT(HttpStatus.CONFLICT, "Ya existe una alerta vigente para este simulacro"),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "La alerta fue modificada por otra persona"),
    EXTERNAL_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Servicio no disponible, intente de nuevo"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
