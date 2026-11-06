package edu.eci.dosw.campusseguro.alertas.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private static final String VALID_ID =
            "11111111-1111-4111-8111-111111111111";

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void tearDown() {
        MDC.remove(CorrelationIds.MDC_KEY);
    }

    @Test
    void filter_shouldReuseValidHeaderAndClearMdcAfterChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIds.HEADER, VALID_ID);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        AtomicReference<String> mdcDuringChain = new AtomicReference<>();

        doAnswer(invocation -> {
            mdcDuringChain.set(MDC.get(CorrelationIds.MDC_KEY));
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);

        assertEquals(VALID_ID, request.getAttribute(CorrelationIds.ATTRIBUTE));
        assertEquals(VALID_ID, response.getHeader(CorrelationIds.HEADER));
        assertEquals(VALID_ID, mdcDuringChain.get());
        assertNull(MDC.get(CorrelationIds.MDC_KEY));
        verify(chain).doFilter(request, response);
    }

    @Test
    void filter_shouldGenerateUuidV4_whenHeaderIsInvalid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIds.HEADER, "not-a-uuid");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        String generated = (String) request.getAttribute(CorrelationIds.ATTRIBUTE);
        assertNotNull(generated);
        assertEquals(4, UUID.fromString(generated).version());
        assertEquals(generated, response.getHeader(CorrelationIds.HEADER));
    }

    @Test
    void filter_shouldClearMdc_whenChainFails() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        try {
            doAnswer(invocation -> {
                throw new IllegalStateException("boom");
            }).when(chain).doFilter(any(), any());
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }

        assertThrows(
                IllegalStateException.class,
                () -> filter.doFilter(request, response, chain));
        assertNull(MDC.get(CorrelationIds.MDC_KEY));
    }
}
