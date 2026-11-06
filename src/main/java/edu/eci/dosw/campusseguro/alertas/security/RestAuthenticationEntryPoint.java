package edu.eci.dosw.campusseguro.alertas.security;

import edu.eci.dosw.campusseguro.alertas.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ErrorResponseWriter errorResponseWriter;

    public RestAuthenticationEntryPoint(ErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {

        String message = authException instanceof InvalidIdentityHeadersException
                ? authException.getMessage()
                : ErrorCode.UNAUTHORIZED.defaultMessage();

        errorResponseWriter.write(
                request,
                response,
                ErrorCode.UNAUTHORIZED,
                message);
    }
}
