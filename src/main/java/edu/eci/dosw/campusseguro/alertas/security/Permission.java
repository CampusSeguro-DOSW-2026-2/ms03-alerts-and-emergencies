package edu.eci.dosw.campusseguro.alertas.security;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Permisos de MS03 y formato de un código de permiso.
 *
 * <p>El catálogo completo vive en MS01 y el API Gateway entrega al usuario sus permisos efectivos, ya
 * calculados como la unión de los de todos sus roles, en la cabecera X-User-Permission. Aquí solo se
 * declaran los permisos que MS03 usa para autorizar y se comprueba el formato de los códigos recibidos.
 */
public enum Permission {

    ALERT_MANAGE("ms03:alert:manage"),
    ALERT_MANAGE_DRILL("ms03:alert:manage_drill"),
    ALERT_READ_HISTORY("ms03:alert:read_history"),
    ALERT_RECEIVE("ms03:alert:receive");

    /** Formato de un código de permiso: {@code msNN:recurso:accion}. */
    private static final Pattern FORMAT =
            Pattern.compile("^ms[0-9]{2}:[a-z][a-z0-9_]*:[a-z][a-z0-9_]*$");

    private static final String SEPARATOR = ",";

    private final String authority;

    Permission(String authority) {
        this.authority = authority;
    }

    public String authority() {
        return authority;
    }

    /**
     * Indica si un código tiene el formato de permiso de los Estándares, sin comprobar que exista en el
     * catálogo de MS01.
     *
     * @param code código a comprobar, puede ser null
     * @return true si el código tiene el formato {@code msNN:recurso:accion}
     */
    public static boolean hasFormat(String code) {
        return code != null && FORMAT.matcher(code).matches();
    }

    /**
     * Convierte el valor de la cabecera en el conjunto de códigos de permiso.
     *
     * @param value códigos separados por coma, puede ser null o estar vacío
     * @return el conjunto de códigos, sin repetidos y en orden alfabético
     * @throws InvalidIdentityHeadersException si algún código no tiene el formato de los Estándares
     */
    public static Set<String> parseList(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        Set<String> codes = new LinkedHashSet<>();
        for (String part : value.split(SEPARATOR)) {
            String candidate = part.strip();
            if (candidate.isEmpty()) {
                continue;
            }
            if (!hasFormat(candidate)) {
                throw new InvalidIdentityHeadersException(
                        "El permiso indicado no es válido: " + candidate);
            }
            codes.add(candidate);
        }
        return codes.stream().sorted().collect(
                java.util.stream.Collectors.toUnmodifiableSet());
    }
}
