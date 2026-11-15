package edu.eci.dosw.campusseguro.alertas.security;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Nombres de rol tal como los entrega el API Gateway en la cabecera X-User-Role.
 *
 * <p>Los roles ya no son un catálogo fijo del código: MS01 los guarda como datos y el administrador
 * puede crear, renombrar y borrar roles en caliente. Por eso MS03 no enumera roles y nunca decide una
 * autorización con su nombre; solo comprueba que el nombre tenga el formato de los Estándares y lo
 * conserva para dos usos que sí son del dominio: los destinatarios dirigidos a un rol y la propagación
 * de la identidad a los servicios que la exigen.
 */
public final class RoleNames {

    /** Formato de un nombre de rol guardado: 2 a 30 caracteres, mayúsculas, dígitos y guion bajo. */
    private static final Pattern STORED_FORMAT = Pattern.compile("^[A-Z][A-Z0-9_]{1,29}$");

    private static final String SEPARATOR = ",";

    private RoleNames() {
    }

    /**
     * Indica si un nombre tiene el formato de un rol guardado, sin comprobar que exista.
     *
     * @param name nombre a comprobar, puede ser null
     * @return true si el nombre tiene el formato de los Estándares
     */
    public static boolean hasStoredFormat(String name) {
        return name != null && STORED_FORMAT.matcher(name).matches();
    }

    /**
     * Convierte el valor de la cabecera en el conjunto de nombres de rol.
     *
     * @param value nombres de rol separados por coma, puede ser null o estar vacío
     * @return el conjunto de nombres, sin repetidos y en orden alfabético
     * @throws InvalidIdentityHeadersException si algún nombre no tiene el formato de los Estándares
     */
    public static Set<String> parseList(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        Set<String> roles = new LinkedHashSet<>();
        for (String part : value.split(SEPARATOR)) {
            String candidate = part.strip();
            if (candidate.isEmpty()) {
                continue;
            }
            if (!hasStoredFormat(candidate)) {
                throw new InvalidIdentityHeadersException(
                        "El rol indicado no es válido: " + candidate);
            }
            roles.add(candidate);
        }
        return roles.stream().sorted().collect(
                java.util.stream.Collectors.toUnmodifiableSet());
    }

    /**
     * Ordena alfabéticamente un conjunto de nombres de rol.
     *
     * @param roles nombres de rol
     * @return lista nueva con los nombres en orden alfabético
     */
    public static List<String> sorted(Collection<String> roles) {
        return roles.stream().sorted().toList();
    }
}
