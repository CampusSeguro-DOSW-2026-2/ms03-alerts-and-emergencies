package edu.eci.dosw.campusseguro.alertas.security;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

public record AccessActor(String userId, Set<String> roles) {

    private static final String AUTHORITY_PREFIX = "ROLE_";

    public static AccessActor from(Authentication authentication) {
        if (authentication == null) {
            return new AccessActor("", Set.of());
        }
        Set<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority != null && !authority.isBlank())
                .map(AccessActor::normalize)
                .collect(Collectors.toUnmodifiableSet());
        return new AccessActor(authentication.getName(), roles);
    }

    private static String normalize(String authority) {
        String normalized = authority.strip().toUpperCase(Locale.ROOT);
        return normalized.startsWith(AUTHORITY_PREFIX)
                ? normalized.substring(AUTHORITY_PREFIX.length())
                : normalized;
    }
}
