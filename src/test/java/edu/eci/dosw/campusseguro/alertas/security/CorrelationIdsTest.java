package edu.eci.dosw.campusseguro.alertas.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;

class CorrelationIdsTest {

    private static final String VALID_ID =
            "11111111-1111-4111-8111-111111111111";

    @AfterEach
    void tearDown() {
        MDC.remove(CorrelationIds.MDC_KEY);
    }

    @Test
    void resolve_shouldKeepValidUuidV4() {
        assertEquals(VALID_ID, CorrelationIds.resolve(VALID_ID));
        assertEquals(VALID_ID, CorrelationIds.resolve("  " + VALID_ID + "  "));
    }

    @Test
    void resolve_shouldGenerateUuidV4_whenCandidateIsMissingOrInvalid() {
        assertUuidV4(CorrelationIds.resolve(null));
        assertUuidV4(CorrelationIds.resolve(" "));
        assertUuidV4(CorrelationIds.resolve("not-a-uuid"));
        assertUuidV4(CorrelationIds.resolve("11111111-1111-1111-1111-111111111111"));
    }

    @Test
    void current_shouldPreferRequestAttribute() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(CorrelationIds.ATTRIBUTE, VALID_ID);
        MDC.put(CorrelationIds.MDC_KEY, "22222222-2222-4222-8222-222222222222");

        assertEquals(VALID_ID, CorrelationIds.current(request));
    }

    @Test
    void current_shouldFallBackToMdc() {
        MDC.put(CorrelationIds.MDC_KEY, VALID_ID);

        assertEquals(VALID_ID, CorrelationIds.current());
        assertEquals(VALID_ID, CorrelationIds.current(new MockHttpServletRequest()));
    }

    @Test
    void current_shouldGenerateUuidV4_whenNothingIsAvailable() {
        assertUuidV4(CorrelationIds.current());
        assertUuidV4(CorrelationIds.current(new MockHttpServletRequest()));
    }

    private void assertUuidV4(String value) {
        assertNotNull(value);
        assertEquals(value, UUID.fromString(value).toString());
        assertEquals(4, UUID.fromString(value).version());
        assertNotEquals("11111111-1111-1111-1111-111111111111", value);
    }
}
