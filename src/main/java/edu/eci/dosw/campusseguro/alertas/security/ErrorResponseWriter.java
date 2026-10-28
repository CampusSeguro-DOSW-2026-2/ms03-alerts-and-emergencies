package edu.eci.dosw.campusseguro.alertas.security;

import edu.eci.dosw.campusseguro.alertas.exception.ErrorCode;
import edu.eci.dosw.campusseguro.alertas.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class ErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public ErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            ErrorCode code) throws IOException {
        write(request, response, code, code.defaultMessage());
    }

    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            ErrorCode code,
            String message) throws IOException {

        String correlationId = CorrelationIds.current(request);
        ErrorResponse body = ErrorResponse.of(
                code,
                message,
                List.of(),
                correlationId);

        response.setStatus(code.status().value());
        response.setHeader(CorrelationIds.HEADER, correlationId);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
