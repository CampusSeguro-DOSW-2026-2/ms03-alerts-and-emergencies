package edu.eci.dosw.campusseguro.alertas.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

class IdentityHeadersAuthenticationFilterTest {

    private static final String USER_ID =
            "11111111-1111-4111-8111-111111111111";

    private RestAuthenticationEntryPoint entryPoint;
    private FilterChain chain;
    private IdentityHeadersAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        entryPoint = mock(RestAuthenticationEntryPoint.class);
        chain = mock(FilterChain.class);
        filter = new IdentityHeadersAuthenticationFilter(new IdentityHeaderParser(), entryPoint);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void filter_shouldContinueWithoutAuthentication_whenHeadersAreAbsent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void filter_shouldAuthenticateRequester_whenHeadersAreValid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(IdentityHeaderParser.USER_ID_HEADER, USER_ID);
        request.addHeader(IdentityHeaderParser.USER_ROLE_HEADER, "COORDINADOR");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication);
        assertTrue(authentication.isAuthenticated());
        assertEquals(USER_ID, ((RequesterIdentity) authentication.getPrincipal()).userId().toString());
        assertTrue(authentication.getAuthorities().stream()
                .anyMatch(authority -> "ms03:alert:manage".equals(authority.getAuthority())));
    }

    @Test
    void filter_shouldRejectRequest_whenHeadersAreInvalid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(IdentityHeaderParser.USER_ID_HEADER, USER_ID);
        request.addHeader(IdentityHeaderParser.USER_ROLE_HEADER, "DESCONOCIDO");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(entryPoint).commence(
                eq(request),
                eq(response),
                any(InvalidIdentityHeadersException.class));
        verify(chain, never()).doFilter(
                any(HttpServletRequest.class),
                any(HttpServletResponse.class));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
