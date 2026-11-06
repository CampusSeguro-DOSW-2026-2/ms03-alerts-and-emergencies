package edu.eci.dosw.campusseguro.alertas.security;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public enum UserRole {

    ESTUDIANTE(Permission.ALERT_RECEIVE),
    PROFESOR(Permission.ALERT_RECEIVE),
    ADMINISTRATIVO(Permission.ALERT_RECEIVE),
    BRIGADISTA(Permission.ALERT_RECEIVE),
    COORDINADOR(
            Permission.ALERT_RECEIVE,
            Permission.ALERT_MANAGE,
            Permission.ALERT_READ_HISTORY),
    ORGANIZADOR(
            Permission.ALERT_RECEIVE,
            Permission.ALERT_MANAGE_DRILL,
            Permission.ALERT_READ_HISTORY),
    ADMIN(),
    AUDITOR(Permission.ALERT_READ_HISTORY);

    private static final String ROLE_PREFIX = "ROLE_";

    private final Set<Permission> permissions;

    UserRole(Permission... permissions) {
        this.permissions = Set.of(permissions);
    }

    public Set<Permission> permissions() {
        return permissions;
    }

    public static Optional<UserRole> from(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        if (normalized.startsWith(ROLE_PREFIX)) {
            normalized = normalized.substring(ROLE_PREFIX.length());
        }
        for (UserRole role : values()) {
            if (role.name().equals(normalized)) {
                return Optional.of(role);
            }
        }
        return Optional.empty();
    }

    public static Set<UserRole> parseList(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        Set<UserRole> roles = new LinkedHashSet<>();
        for (String part : value.split(",")) {
            String candidate = part.strip();
            if (candidate.isEmpty()) {
                continue;
            }
            UserRole role = from(candidate)
                    .orElseThrow(() -> new InvalidIdentityHeadersException(
                            "El rol indicado no es válido: " + candidate));
            roles.add(role);
        }
        return Set.copyOf(roles);
    }
}
