package edu.eci.dosw.campusseguro.alertas.security;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public record RequesterIdentity(
        UUID userId,
        Set<UserRole> roles,
        String serviceName) {

    public RequesterIdentity {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        serviceName = serviceName == null
                ? null
                : serviceName.strip().toLowerCase(Locale.ROOT);
    }

    public Set<Permission> permissions() {
        Set<Permission> permissions = new LinkedHashSet<>();
        roles.forEach(role -> permissions.addAll(role.permissions()));
        return Set.copyOf(permissions);
    }

    public Collection<GrantedAuthority> authorities() {
        return permissions().stream()
                .map(permission -> (GrantedAuthority) new SimpleGrantedAuthority(
                        permission.authority()))
                .toList();
    }
}
