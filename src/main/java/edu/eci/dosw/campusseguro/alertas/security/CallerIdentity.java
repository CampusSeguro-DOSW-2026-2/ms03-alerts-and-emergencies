package edu.eci.dosw.campusseguro.alertas.security;

import java.util.Set;

/**
 * Identidad que MS03 propaga a otro servicio cuando el API Gateway exige las tres cabeceras.
 *
 * <p>Existe como tipo propio para que las llamadas salientes no confundan el CSV de roles con el de
 * permisos: son dos textos con el mismo aspecto y un intercambio no fallaría al compilar.
 *
 * @param userId id del usuario en cuyo nombre se hace la llamada
 * @param rolesCsv nombres de rol separados por coma
 * @param permissionsCsv códigos de permiso separados por coma
 */
public record CallerIdentity(
        String userId,
        String rolesCsv,
        String permissionsCsv) {

    /**
     * Indica si hay identidad de usuario que propagar.
     *
     * @return true si el id del usuario no está vacío
     */
    public boolean isPresent() {
        return userId != null && !userId.isBlank();
    }

    /**
     * Compone la identidad que se propaga a partir del contexto del solicitante.
     *
     * @param requester contexto del solicitante de la operación
     * @return la identidad con su id, sus roles y sus permisos
     */
    public static CallerIdentity from(RequesterContext requester) {
        if (requester == null) {
            return new CallerIdentity(null, null, null);
        }
        return new CallerIdentity(
                requester.actor(),
                csv(requester.roles()),
                csv(requester.permissions()));
    }

    private static String csv(Set<String> values) {
        return values == null || values.isEmpty()
                ? null
                : String.join(",", RoleNames.sorted(values));
    }
}
