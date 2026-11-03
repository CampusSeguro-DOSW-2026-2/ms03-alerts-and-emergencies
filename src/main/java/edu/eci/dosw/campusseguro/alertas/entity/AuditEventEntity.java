package edu.eci.dosw.campusseguro.alertas.entity;

import edu.eci.dosw.campusseguro.alertas.entity.enums.AuditAction;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AuditStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "audit_events")
public class AuditEventEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "alert_id", nullable = false, updatable = false)
    private UUID alertId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, updatable = false, length = 40)
    private AuditAction action;

    @Column(name = "actor", nullable = false, updatable = false, length = 64)
    private String actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", updatable = false, length = 20)
    private AuditStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", updatable = false, length = 20)
    private AuditStatus toStatus;

    @Column(name = "details", updatable = false, length = 500)
    private String details;

    public UUID getId() {
        return id;
    }

    public UUID getAlertId() {
        return alertId;
    }

    public void setAlertId(UUID alertId) {
        this.alertId = alertId;
    }

    public AuditAction getAction() {
        return action;
    }

    public void setAction(AuditAction action) {
        this.action = action;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public AuditStatus getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(AuditStatus fromStatus) {
        this.fromStatus = fromStatus;
    }

    public AuditStatus getToStatus() {
        return toStatus;
    }

    public void setToStatus(AuditStatus toStatus) {
        this.toStatus = toStatus;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }
}