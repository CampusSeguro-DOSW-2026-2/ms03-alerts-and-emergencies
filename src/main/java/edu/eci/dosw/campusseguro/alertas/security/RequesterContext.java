package edu.eci.dosw.campusseguro.alertas.security;

import java.util.Set;

public record RequesterContext(
        String actor,
        Set<UserRole> roles,
        String serviceName) {

    public RequesterContext {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    public boolean has(Permission permission) {
        return roles.stream().anyMatch(role -> role.permissions().contains(permission));
    }

    public boolean isSimulationOnly() {
        return !has(Permission.ALERT_MANAGE)
                && has(Permission.ALERT_MANAGE_DRILL);
    }

    public String auditActor() {
        return serviceName == null || serviceName.isBlank()
                ? actor
                : serviceName.strip() + ":" + actor;
    }
}
