package edu.eci.dosw.campusseguro.alertas.security;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Identidad de quien hace la petición, tal como la entrega el API Gateway en las cabeceras.
 *
 * <p>Los permisos de {@code X-User-Permission} son la única fuente de autorización: vienen ya calculados
 * por MS01 como la unión de los permisos de todos los roles del usuario. Los nombres de
 * {@code X-User-Role} nunca conceden nada; se conservan porque los destinatarios de una alerta pueden
 * dirigirse a un rol y porque hay que propagarlos a los servicios que exigen las tres cabeceras.
 *
 * @param userId id del usuario autenticado
 * @param roles nombres de los roles del usuario, informativos
 * @param permissions códigos de los permisos efectivos del usuario, los que autorizan
 * @param serviceName servicio llamante que informó el API Gateway, metadato de auditoría
 */
public record RequesterIdentity(
        UUID userId,
        Set<String> roles,
        Set<String> permissions,
        String serviceName) {

    /**
     * Valida los componentes y deja los roles y los permisos como conjuntos inmutables y ordenados.
     */
    public RequesterIdentity {
        Objects.requireNonNull(userId, "userId");
        roles = sortedCopy(roles);
        permissions = sortedCopy(permissions);
        serviceName = normalizeService(serviceName);
    }

    /**
     * Traduce los permisos a las autoridades que evalúa Spring Security.
     *
     * @return una autoridad por cada código de permiso, sin incluir los roles
     */
    public Collection<GrantedAuthority> authorities() {
        return permissions.stream()
                .map(code -> (GrantedAuthority) new SimpleGrantedAuthority(code))
                .toList();
    }

    /**
     * Devuelve los roles como texto separado por coma, listo para reenviar a otro servicio.
     *
     * @return los nombres de rol en orden alfabético separados por coma
     */
    public String rolesCsv() {
        return String.join(",", roles);
    }

    /**
     * Devuelve los permisos como texto separado por coma, listo para reenviar a otro servicio.
     *
     * @return los códigos de permiso en orden alfabético separados por coma
     */
    public String permissionsCsv() {
        return String.join(",", permissions);
    }

    private static Set<String> sortedCopy(Set<String> values) {
        return values == null
                ? Set.of()
                : Collections.unmodifiableSet(new TreeSet<>(values));
    }

    private static String normalizeService(String value) {
        return value == null ? null : value.strip().toLowerCase(java.util.Locale.ROOT);
    }
}
