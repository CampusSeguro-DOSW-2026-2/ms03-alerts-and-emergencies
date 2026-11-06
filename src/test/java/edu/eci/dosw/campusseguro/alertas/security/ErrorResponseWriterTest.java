package edu.eci.dosw.campusseguro.alertas.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.eci.dosw.campusseguro.alertas.exception.ErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ErrorResponseWriterTest {

    private static final String CORRELATION_ID =
            "11111111-1111-4111-8111-111111111111";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void write_shouldSetStatusAndSpanishErrorBody() throws Exception {
        ErrorResponseWriter writer = new ErrorResponseWriter(objectMapper);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/alertas");
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(request, response, ErrorCode.UNAUTHORIZED);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/json"));
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertEquals("NO_AUTENTICADO", body.get("codigo").asString());
        assertEquals(401, body.get("status").asInt());
        assertEquals(
                ErrorCode.UNAUTHORIZED.defaultMessage(),
                body.get("mensaje").asString());
        assertTrue(body.get("errores").isArray());
        assertNotNull(body.get("correlation_id"));
        assertEquals(
                response.getHeader(CorrelationIds.HEADER),
                body.get("correlation_id").asString());
    }

    @Test
    void write_shouldReuseValidCorrelationIdFromRequest() throws Exception {
        ErrorResponseWriter writer = new ErrorResponseWriter(objectMapper);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/alertas");
        request.addHeader(CorrelationIds.HEADER, CORRELATION_ID);
        request.setAttribute(CorrelationIds.ATTRIBUTE, CORRELATION_ID);
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(request, response, ErrorCode.FORBIDDEN);

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertEquals(403, body.get("status").asInt());
        assertEquals("PROHIBIDO", body.get("codigo").asString());
        assertEquals(CORRELATION_ID, body.get("correlation_id").asString());
        assertEquals(CORRELATION_ID, response.getHeader(CorrelationIds.HEADER));
    }

    @Test
    void write_shouldGenerateUuidCorrelationId_whenRequestHasNone() throws Exception {
        ErrorResponseWriter writer = new ErrorResponseWriter(objectMapper);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/alertas");
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(request, response, ErrorCode.INTERNAL_ERROR);

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        String correlationId = body.get("correlation_id").asString();
        assertEquals(UUID.fromString(correlationId).toString(), correlationId);
        assertEquals(4, UUID.fromString(correlationId).version());
    }
}
