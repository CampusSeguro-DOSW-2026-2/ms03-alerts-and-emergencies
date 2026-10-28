package edu.eci.dosw.campusseguro.alertas.security;

import static org.mockito.Mockito.verify;

import edu.eci.dosw.campusseguro.alertas.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

class RestSecurityHandlersTest {

    private final ErrorResponseWriter writer = org.mockito.Mockito.mock(ErrorResponseWriter.class);

    @Test
    void accessDeniedHandler_shouldWriteForbidden() throws Exception {
        HttpServletRequest request = org.mockito.Mockito.mock(HttpServletRequest.class);
        HttpServletResponse response = org.mockito.Mockito.mock(HttpServletResponse.class);

        new RestAccessDeniedHandler(writer).handle(request, response, new AccessDeniedException("denied"));

        verify(writer).write(request, response, ErrorCode.FORBIDDEN);
    }

    @Test
    void authenticationEntryPoint_shouldWriteUnauthorized() throws Exception {
        HttpServletRequest request = org.mockito.Mockito.mock(HttpServletRequest.class);
        HttpServletResponse response = org.mockito.Mockito.mock(HttpServletResponse.class);

        new RestAuthenticationEntryPoint(writer)
                .commence(request, response, new BadCredentialsException("invalid"));

        verify(writer).write(
                request,
                response,
                ErrorCode.UNAUTHORIZED,
                ErrorCode.UNAUTHORIZED.defaultMessage());
    }
}
