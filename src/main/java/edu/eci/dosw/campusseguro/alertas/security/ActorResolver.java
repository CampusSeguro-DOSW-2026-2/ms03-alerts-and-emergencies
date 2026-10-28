package edu.eci.dosw.campusseguro.alertas.security;

import java.util.Set;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

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
                    identity.serviceName());
        }

        Set<UserRole> roles = authentication.getAuthorities().stream()
                .map(authority -> UserRole.from(authority.getAuthority()))
                .flatMap(java.util.Optional::stream)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        return new RequesterContext(
                truncate(authentication.getName()),
                roles,
                null);
    }

    private String truncate(String actor) {
        return actor.length() <= MAX_ACTOR_LENGTH
                ? actor
                : actor.substring(0, MAX_ACTOR_LENGTH);
    }
}
