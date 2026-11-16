package edu.eci.dosw.campusseguro.alertas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Fila del catálogo de tipos de alerta.
 *
 * <p>El catálogo es data: MS03 lo lee para validar los tipos de una alerta y para mostrar la
 * etiqueta y el orden de cada uno. Agregar un tipo nuevo es insertar una fila, no desplegar código.
 */
@Entity
@Table(name = "alert_type")
public class AlertTypeEntity {

    @Id
    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "label", nullable = false, length = 80)
    private String label;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected AlertTypeEntity() {
    }

    public AlertTypeEntity(String code, String label, int displayOrder, boolean active) {
        this.code = code;
        this.label = label;
        this.displayOrder = displayOrder;
        this.active = active;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public boolean isActive() {
        return active;
    }
}
