package edu.eci.dosw.campusseguro.alertas.security;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * Actor de una operación sobre una alerta, con los datos que necesita la política de acceso.
 *
 * <p>La autorización se decide con {@link #permissions()}; los roles solo sirven para comparar
 * destinatarios dirigidos a un rol.
 *
 * @param userId id del usuario que ejecuta la operación
 * @param roles nombres de sus roles, informativos
 * @param permissions códigos de sus permisos efectivos
 */
public record AccessActor(String userId, Set<String> roles, Set<String> permissions) {

    public AccessActor {
        roles = sortedCopy(roles);
        permissions = sortedCopy(permissions);
    }

    /**
     * Compone el actor a partir de una autenticación de Spring Security.
     *
     * @param authentication autenticación de la petición, puede ser null
     * @return el actor, vacío si no hay autenticación
     */
    public static AccessActor from(Authentication authentication) {
        if (authentication == null) {
            return new AccessActor("", Set.of(), Set.of());
        }

        if (authentication.getPrincipal() instanceof RequesterIdentity identity) {
            return new AccessActor(
                    identity.userId().toString(),
                    identity.roles(),
                    identity.permissions());
        }

        Set<String> permissions = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(Permission::hasFormat)
                .collect(Collectors.toUnmodifiableSet());

        return new AccessActor(authentication.getName(), Set.of(), permissions);
    }

    /**
     * Indica si el actor tiene el permiso.
     *
     * @param permission permiso que se desea comprobar
     * @return true si sus permisos contienen el código del permiso
     */
    public boolean hasPermission(Permission permission) {
        return permission != null && permissions.contains(permission.authority());
    }

    private static Set<String> sortedCopy(Set<String> values) {
        return values == null
                ? Set.of()
                : Collections.unmodifiableSet(new TreeSet<>(values));
    }
}
