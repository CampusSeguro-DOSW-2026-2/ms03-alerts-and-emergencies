package edu.eci.dosw.campusseguro.alertas.security;

import java.util.Set;

/**
 * Identidades de prueba que emulan lo que inyecta el API Gateway.
 *
 * <p>El Gateway arma {@code X-User-Permission} con la unión de los permisos de todos los roles del
 * usuario, según la asignación inicial de MS01. MS03 nunca deriva permisos de un rol, así que las
 * pruebas declaran los permisos directamente y usan los roles solo cuando el caso lo necesita.
 */
public final class Identities {

    /** Permisos de MS03 del rol COORDINADOR en la asignación inicial. */
    public static final Set<String> COORDINADOR = Set.of(
            "ms03:alert:manage",
            "ms03:alert:read_history",
            "ms03:alert:receive");

    /** Permisos de MS03 del rol ORGANIZADOR en la asignación inicial. */
    public static final Set<String> ORGANIZADOR = Set.of(
            "ms03:alert:manage_drill",
            "ms03:alert:read_history",
            "ms03:alert:receive");

    /** Permisos de MS03 de los roles que solo reciben alertas. */
    public static final Set<String> RECEPTOR = Set.of("ms03:alert:receive");

    private Identities() {
    }

    public static RequesterContext coordinador(String actor) {
        return new RequesterContext(actor, Set.of("COORDINADOR"), COORDINADOR, null);
    }

    public static RequesterContext organizador(String actor) {
        return new RequesterContext(actor, Set.of("ORGANIZADOR"), ORGANIZADOR, null);
    }

    public static RequesterContext receptor(String actor) {
        return new RequesterContext(actor, Set.of("ESTUDIANTE"), RECEPTOR, null);
    }

    public static RequesterIdentity identity(String userId, String serviceName) {
        return new RequesterIdentity(
                java.util.UUID.fromString(userId),
                Set.of("COORDINADOR"),
                COORDINADOR,
                serviceName);
    }
}
