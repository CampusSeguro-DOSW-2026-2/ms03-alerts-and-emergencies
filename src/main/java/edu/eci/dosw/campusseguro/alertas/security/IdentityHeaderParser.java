package edu.eci.dosw.campusseguro.alertas.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Convierte las cabeceras de identidad que inyecta el API Gateway en un {@link RequesterIdentity}.
 *
 * <p>Las tres cabeceras son obligatorias en una petición autenticada: {@code X-User-Id},
 * {@code X-User-Role} y {@code X-User-Permission}. Solo los permisos de la tercera autorizan; el
 * nombre de un rol nunca concede nada.
 */
@Component
public class IdentityHeaderParser {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLE_HEADER = "X-User-Role";
    public static final String USER_PERMISSION_HEADER = "X-User-Permission";
    public static final String SERVICE_NAME_HEADER = "X-Service-Name";

    private static final int MAX_USER_ID_LENGTH = 36;
    private static final int MAX_ROLE_HEADER_LENGTH = 512;
    private static final int MAX_PERMISSION_HEADER_LENGTH = 4096;

    public RequesterIdentity parse(HttpServletRequest request) {
        String rawUserId = request.getHeader(USER_ID_HEADER);
        String rawRoles = request.getHeader(USER_ROLE_HEADER);
        String rawPermissions = request.getHeader(USER_PERMISSION_HEADER);

        if (rawUserId == null || rawUserId.isBlank()) {
            throw new InvalidIdentityHeadersException(
                    "La cabecera X-User-Id es obligatoria");
        }
        if (rawRoles == null || rawRoles.isBlank()) {
            throw new InvalidIdentityHeadersException(
                    "La cabecera X-User-Role es obligatoria");
        }
        if (rawPermissions == null || rawPermissions.isBlank()) {
            throw new InvalidIdentityHeadersException(
                    "La cabecera X-User-Permission es obligatoria");
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
        if (rawPermissions.length() > MAX_PERMISSION_HEADER_LENGTH) {
            throw new InvalidIdentityHeadersException(
                    "La cabecera X-User-Permission no tiene un formato válido");
        }

        Set<String> roles = RoleNames.parseList(rawRoles);
        if (roles.isEmpty()) {
            throw new InvalidIdentityHeadersException(
                    "La cabecera X-User-Role debe incluir al menos un rol");
        }

        Set<String> permissions = Permission.parseList(rawPermissions);
        if (permissions.isEmpty()) {
            throw new InvalidIdentityHeadersException(
                    "La cabecera X-User-Permission debe incluir al menos un permiso");
        }

        return new RequesterIdentity(
                parsedUserId,
                roles,
                permissions,
                request.getHeader(SERVICE_NAME_HEADER));
    }
}
