package edu.eci.dosw.campusseguro.alertas.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class IdentityHeaderParser {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLE_HEADER = "X-User-Role";
    public static final String SERVICE_NAME_HEADER = "X-Service-Name";

    private static final int MAX_USER_ID_LENGTH = 36;
    private static final int MAX_ROLE_HEADER_LENGTH = 512;

    public RequesterIdentity parse(HttpServletRequest request) {
        String rawUserId = request.getHeader(USER_ID_HEADER);
        String rawRoles = request.getHeader(USER_ROLE_HEADER);

        if (rawUserId == null || rawUserId.isBlank()) {
            throw new InvalidIdentityHeadersException(
                    "La cabecera X-User-Id es obligatoria");
        }
        if (rawRoles == null || rawRoles.isBlank()) {
            throw new InvalidIdentityHeadersException(
                    "La cabecera X-User-Role es obligatoria");
        }

        String userId = rawUserId.strip();
        if (userId.length() > MAX_USER_ID_LENGTH) {
            throw new InvalidIdentityHeadersException(
                    "La cabecera X-User-Id no tiene un formato válido");
        }

        UUID parsedUserId;
        try {
            parsedUserId = UUID.fromString(userId);
        } catch (IllegalArgumentException ex) {
            throw new InvalidIdentityHeadersException(
                    "La cabecera X-User-Id no tiene un formato válido");
        }

        if (rawRoles.length() > MAX_ROLE_HEADER_LENGTH) {
            throw new InvalidIdentityHeadersException(
                    "La cabecera X-User-Role no tiene un formato válido");
        }

        Set<UserRole> roles = UserRole.parseList(rawRoles);
        if (roles.isEmpty()) {
            throw new InvalidIdentityHeadersException(
                    "La cabecera X-User-Role debe incluir al menos un rol");
        }

        return new RequesterIdentity(
                parsedUserId,
                roles,
                request.getHeader(SERVICE_NAME_HEADER));
    }
}
