package edu.eci.dosw.campusseguro.alertas.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class IdentityHeadersAuthenticationFilter extends OncePerRequestFilter {

    private final IdentityHeaderParser identityHeaderParser;
    private final AuthenticationEntryPoint authenticationEntryPoint;

    public IdentityHeadersAuthenticationFilter(
            IdentityHeaderParser identityHeaderParser,
            RestAuthenticationEntryPoint authenticationEntryPoint) {
        this.identityHeaderParser = identityHeaderParser;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String userId = request.getHeader(IdentityHeaderParser.USER_ID_HEADER);
        String roles = request.getHeader(IdentityHeaderParser.USER_ROLE_HEADER);

        if (isBlank(userId) && isBlank(roles)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            RequesterIdentity identity = identityHeaderParser.parse(request);

            UsernamePasswordAuthenticationToken authentication =
                    UsernamePasswordAuthenticationToken.authenticated(
                            identity,
                            null,
                            identity.authorities());
            authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (InvalidIdentityHeadersException ex) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(request, response, ex);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
