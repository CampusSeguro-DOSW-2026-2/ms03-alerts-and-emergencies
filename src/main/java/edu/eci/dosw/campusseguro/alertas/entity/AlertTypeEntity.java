package edu.eci.dosw.campusseguro.alertas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Fila del catálogo de tipos de alerta.
 *
 * <p>El catálogo es data: MS03 lo lee para validar los tipos de una alerta y para mostrar la
 * etiqueta y el orden de cada uno. Las filas con {@code source=MS06} se replican desde MS06; las
 * filas con {@code source=LOCAL} (por ahora {@code OPERATIVA}) son propias de MS03 y la
 * sincronización no las modifica.
 */
@Entity
@Table(name = "alert_type")
public class AlertTypeEntity {

    /** Origen de una fila replicada desde MS06. */
    public static final String SOURCE_MS06 = "MS06";

    /** Origen de una fila que solo existe en MS03. */
    public static final String SOURCE_LOCAL = "LOCAL";

    @Id
    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "label", nullable = false, length = 80)
    private String label;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "source", nullable = false, length = 20)
    private String source;

    @Column(name = "synced_at")
    private Instant syncedAt;

    protected AlertTypeEntity() {
    }

    /**
     * Conserva el constructor anterior; por defecto la fila se considera replicada de MS06.
     *
     * @param code código del tipo
     * @param label etiqueta visible
     * @param displayOrder orden de presentación
     * @param active si el tipo se puede usar en alertas nuevas
     */
    public AlertTypeEntity(String code, String label, int displayOrder, boolean active) {
        this(code, label, displayOrder, active, SOURCE_MS06);
    }

    public AlertTypeEntity(
            String code,
            String label,
            int displayOrder,
            boolean active,
            String source) {

        this.code = code;
        this.label = label;
        this.displayOrder = displayOrder;
        this.active = active;
        this.source = source == null || source.isBlank() ? SOURCE_MS06 : source;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Instant getSyncedAt() {
        return syncedAt;
    }

    public void setSyncedAt(Instant syncedAt) {
        this.syncedAt = syncedAt;
    }
}
