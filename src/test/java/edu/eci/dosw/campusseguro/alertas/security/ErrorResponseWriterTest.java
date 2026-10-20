package edu.eci.dosw.campusseguro.alertas.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.eci.dosw.campusseguro.alertas.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ErrorResponseWriterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void write_shouldSetStatusAndStandardErrorBody() throws Exception {
        ErrorResponseWriter writer = new ErrorResponseWriter(
                objectMapper,
                Clock.fixed(Instant.parse("2026-10-15T18:00:00Z"), ZoneOffset.UTC));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/alertas");
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(request, response, ErrorCode.UNAUTHORIZED);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/json"));
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertEquals("UNAUTHORIZED", body.get("code").asString());
        assertEquals(401, body.get("status").asInt());
        assertEquals("/api/v1/alertas", body.get("path").asString());
        assertTrue(body.get("details").isArray());
    }
}
