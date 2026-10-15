package edu.eci.dosw.campusseguro.alertas.security;

import java.util.Locale;
import java.util.Optional;

public enum Role {
    COORDINATOR,
    AUDITOR,
    SERVICE_MS06;

    private static final String AUTHORITY_PREFIX = "ROLE_";

    public String authority() {
        return AUTHORITY_PREFIX + name();
    }

    public static Optional<Role> fromClaim(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        if (normalized.startsWith(AUTHORITY_PREFIX)) {
            normalized = normalized.substring(AUTHORITY_PREFIX.length());
        }
        for (Role role : values()) {
            if (role.name().equals(normalized)) {
                return Optional.of(role);
            }
        }
        return Optional.empty();
    }
}
