package edu.eci.dosw.campusseguro.alertas.security;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * Contexto del solicitante que consumen los servicios de alertas.
 *
 * <p>La autorización se decide solo con {@link #permissions()}: los roles se conservan como nombres
 * informativos para los destinatarios dirigidos a un rol y para la auditoría, nunca para conceder un
 * permiso.
 *
 * @param actor usuario o sistema que ejecuta la operación, para la auditoría
 * @param roles nombres de los roles del solicitante, informativos
 * @param permissions códigos de los permisos efectivos del solicitante
 * @param serviceName servicio llamante que informó el API Gateway, metadato de auditoría
 */
public record RequesterContext(
        String actor,
        Set<String> roles,
        Set<String> permissions,
        String serviceName) {

    /**
     * Deja los roles y los permisos como conjuntos inmutables y ordenados.
     */
    public RequesterContext {
        roles = copyOf(roles);
        permissions = copyOf(permissions);
    }

    /**
     * Indica si el solicitante tiene el permiso, según los códigos que recibió en la cabecera.
     *
     * @param permission permiso que se desea comprobar
     * @return true si sus permisos contienen el código del permiso
     */
    public boolean has(Permission permission) {
        return permission != null && permissions.contains(permission.authority());
    }

    /**
     * Indica si el solicitante solo puede gestionar alertas de simulacro.
     *
     * @return true si puede gestionar simulacros y no alertas generales
     */
    public boolean isSimulationOnly() {
        return !has(Permission.ALERT_MANAGE)
                && has(Permission.ALERT_MANAGE_DRILL);
    }

    /**
     * Compone el actor de auditoría, con el servicio llamante delante cuando existe.
     *
     * @return el servicio y el actor separados por dos puntos, o el actor solo
     */
    public String auditActor() {
        return serviceName == null || serviceName.isBlank()
                ? actor
                : serviceName.strip() + ":" + actor;
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

    private static Set<String> copyOf(Set<String> values) {
        return values == null
                ? Set.of()
                : Collections.unmodifiableSet(new TreeSet<>(values));
    }
}
