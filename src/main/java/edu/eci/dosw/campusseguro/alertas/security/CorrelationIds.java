package edu.eci.dosw.campusseguro.alertas.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class CorrelationIds {

    public static final String HEADER = "X-Correlation-ID";
    public static final String MDC_KEY = "correlation_id";
    public static final String ATTRIBUTE = CorrelationIds.class.getName() + ".id";

    private CorrelationIds() {
    }

    public static String resolve(String candidate) {
        if (candidate != null && !candidate.isBlank()) {
            try {
                UUID parsed = UUID.fromString(candidate.strip());
                if (parsed.version() == 4) {
                    return parsed.toString();
                }
            } catch (IllegalArgumentException ignored) {
                // Se genera uno nuevo cuando el valor recibido no es válido.
            }
        }
        return UUID.randomUUID().toString();
    }

    public static String current() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            Object value = servletAttributes.getRequest()
                    .getAttribute(ATTRIBUTE);
            if (value instanceof String correlationId) {
                return correlationId;
            }
        }

        String fromMdc = MDC.get(MDC_KEY);
        if (fromMdc != null && !fromMdc.isBlank()) {
            return fromMdc;
        }

        return UUID.randomUUID().toString();
    }

    public static String current(HttpServletRequest request) {
        Object value = request.getAttribute(ATTRIBUTE);
        if (value instanceof String correlationId) {
            return correlationId;
        }
        return current();
    }
}
