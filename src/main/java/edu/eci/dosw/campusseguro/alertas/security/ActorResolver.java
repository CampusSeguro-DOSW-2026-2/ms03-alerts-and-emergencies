package edu.eci.dosw.campusseguro.alertas.security;

import java.util.Set;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resuelve el {@link RequesterContext} de la petición en curso a partir de la autenticación de Spring
 * Security.
 *
 * <p>La autenticación la dejó {@link IdentityHeadersAuthenticationFilter}: las autoridades son los
 * códigos de permiso que recibió en {@code X-User-Permission}, así que este resolver solo los transporta
 * y nunca los deriva de un nombre de rol.
 */
@Component
public class ActorResolver {

    public static final String SYSTEM_SCHEDULER = "SYSTEM_SCHEDULER";
    public static final String SYSTEM_NOTIFIER = "SYSTEM_NOTIFIER";

    private static final int MAX_ACTOR_LENGTH = 64;

    public String resolve() {
        return requester().actor();
    }

    public RequesterContext requester() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException(
                    "No hay un usuario autenticado");
        }

        if (authentication.getPrincipal() instanceof RequesterIdentity identity) {
            return new RequesterContext(
                    truncate(identity.userId().toString()),
                    identity.roles(),
                    identity.permissions(),
                    identity.serviceName());
        }

        Set<String> permissions = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(Permission::hasFormat)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        return new RequesterContext(
                truncate(authentication.getName()),
                Set.of(),
                permissions,
                null);
    }

    private String truncate(String actor) {
        return actor.length() <= MAX_ACTOR_LENGTH
                ? actor
                : actor.substring(0, MAX_ACTOR_LENGTH);
    }
}
