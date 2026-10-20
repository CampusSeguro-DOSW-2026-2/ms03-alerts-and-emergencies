package edu.eci.dosw.campusseguro.alertas.security;

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
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("No hay un usuario autenticado");
        }
        boolean isService = authentication.getAuthorities().stream()
                .anyMatch(authority -> Role.SERVICE_MS06.authority().equals(authority.getAuthority()));
        String actor = isService ? Role.SERVICE_MS06.name() : authentication.getName();
        return actor.length() <= MAX_ACTOR_LENGTH ? actor : actor.substring(0, MAX_ACTOR_LENGTH);
    }
}
