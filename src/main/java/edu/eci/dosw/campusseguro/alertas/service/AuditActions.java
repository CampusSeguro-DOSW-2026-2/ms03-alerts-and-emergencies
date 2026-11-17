package edu.eci.dosw.campusseguro.alertas.service;

/**
 * Códigos de las acciones auditables que MS03 escribe.
 *
 * <p>El catálogo vive en la tabla {@code audit_action}, pero el código que registra un evento necesita
 * nombrar la acción que emite. Estas constantes son esos identificadores; agregar una acción nueva al
 * catálogo no requiere tocar nada aquí, solo registrarla donde ocurra.
 */
public final class AuditActions {

    public static final String CREATE = "CREATE";
    public static final String UPDATE = "UPDATE";
    public static final String SCHEDULE = "SCHEDULE";
    public static final String ACTIVATE = "ACTIVATE";
    public static final String ARCHIVE = "ARCHIVE";
    public static final String CONFIRM = "CONFIRM";
    public static final String NOTIFICATION_SENT = "NOTIFICATION_SENT";
    public static final String NOTIFICATION_FAILED = "NOTIFICATION_FAILED";
    public static final String RECIPIENT_RESOLUTION_FAILED = "RECIPIENT_RESOLUTION_FAILED";

    /** Acción histórica que ya no se emite: solo sobrevive en registros antiguos. */
    public static final String END = "END";

    private AuditActions() {
    }
}
