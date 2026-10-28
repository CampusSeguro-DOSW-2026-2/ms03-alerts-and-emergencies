package edu.eci.dosw.campusseguro.alertas.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    VALIDATION_ERROR(
            HttpStatus.BAD_REQUEST,
            "VALIDACION",
            "Los datos enviados no son válidos"),
    INVALID_REFERENCE(
            HttpStatus.BAD_REQUEST,
            "REFERENCIA_INVALIDA",
            "Una o más referencias no existen"),
    INVALID_PAGINATION(
            HttpStatus.BAD_REQUEST,
            "PAGINACION_INVALIDA",
            "Los parámetros de consulta no son válidos"),
    INVALID_ACTIVATION_DATE(
            HttpStatus.BAD_REQUEST,
            "FECHA_ACTIVACION_INVALIDA",
            "La fecha de activación debe ser futura"),

    UNAUTHORIZED(
            HttpStatus.UNAUTHORIZED,
            "NO_AUTENTICADO",
            "Se requiere autenticación o la identidad no es válida"),
    FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "PROHIBIDO",
            "No tienes permiso para realizar esta operación"),
    NOT_A_RECIPIENT(
            HttpStatus.FORBIDDEN,
            "NO_ES_DESTINATARIO",
            "No eres destinatario de esta alerta"),

    ALERT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "ALERTA_NO_ENCONTRADA",
            "La alerta no existe"),
    RESOURCE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "NO_ENCONTRADO",
            "El recurso solicitado no existe"),

    METHOD_NOT_ALLOWED(
            HttpStatus.METHOD_NOT_ALLOWED,
            "METODO_NO_PERMITIDO",
            "El método HTTP no está permitido"),
    UNSUPPORTED_MEDIA_TYPE(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "TIPO_NO_SOPORTADO",
            "El tipo de contenido no está soportado"),

    INVALID_ALERT_STATE(
            HttpStatus.CONFLICT,
            "ESTADO_ALERTA_INVALIDO",
            "El estado de la alerta no permite esta operación"),
    INVALID_TRANSITION(
            HttpStatus.CONFLICT,
            "TRANSICION_INVALIDA",
            "La transición de estado solicitada no es válida"),
    ALERT_NOT_ACTIVE(
            HttpStatus.CONFLICT,
            "ALERTA_NO_ACTIVA",
            "La alerta no está activa"),
    SIMULATION_MISMATCH(
            HttpStatus.CONFLICT,
            "CONFLICTO_SIMULACION",
            "El simulacro no corresponde a la alerta"),
    DUPLICATE_SIMULATION_ALERT(
            HttpStatus.CONFLICT,
            "SIMULACION_DUPLICADA",
            "Ya existe una alerta activa o programada para este simulacro"),
    CONCURRENT_MODIFICATION(
            HttpStatus.CONFLICT,
            "MODIFICACION_CONCURRENTE",
            "La alerta fue modificada por otro usuario"),

    EXTERNAL_SERVICE_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "SERVICIO_EXTERNO_NO_DISPONIBLE",
            "Un servicio externo no está disponible. Intenta de nuevo más tarde"),
    INTERNAL_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "ERROR_INTERNO",
            "Ocurrió un error inesperado");

    private final HttpStatus status;
    private final String codigo;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String codigo, String defaultMessage) {
        this.status = status;
        this.codigo = codigo;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String codigo() {
        return codigo;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
