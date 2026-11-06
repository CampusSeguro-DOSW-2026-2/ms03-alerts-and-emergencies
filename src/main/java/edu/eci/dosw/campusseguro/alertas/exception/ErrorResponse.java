package edu.eci.dosw.campusseguro.alertas.exception;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ErrorResponse(
        int status,
        String codigo,
        String mensaje,
        List<ErrorDetail> errores,
        @JsonProperty("correlation_id") String correlationId) {

    public ErrorResponse {
        errores = errores == null ? List.of() : List.copyOf(errores);
    }

    public record ErrorDetail(String campo, String mensaje) {
    }

    public static ErrorResponse of(
            ErrorCode code,
            String message,
            List<ErrorDetail> errors,
            String correlationId) {

        return new ErrorResponse(
                code.status().value(),
                code.codigo(),
                message,
                errors,
                correlationId);
    }
}
