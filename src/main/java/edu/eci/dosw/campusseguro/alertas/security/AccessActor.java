package edu.eci.dosw.campusseguro.alertas.security;

import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

public record AccessActor(String userId, Set<String> roles) {

    public static AccessActor from(Authentication authentication) {
        if (authentication == null) {
            return new AccessActor("", Set.of());
        }

        if (authentication.getPrincipal() instanceof RequesterIdentity identity) {
            return new AccessActor(
                    identity.userId().toString(),
                    identity.roles().stream()
                            .map(UserRole::name)
                            .collect(Collectors.toUnmodifiableSet()));
        }

        Set<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(UserRole::from)
                .flatMap(java.util.Optional::stream)
                .map(UserRole::name)
                .collect(Collectors.toUnmodifiableSet());

        return new AccessActor(authentication.getName(), roles);
    }
}
