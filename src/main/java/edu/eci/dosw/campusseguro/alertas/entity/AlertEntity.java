package edu.eci.dosw.campusseguro.alertas.entity;

import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertOrigin;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "alerts")
public class AlertEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "alert_types",
            joinColumns = @JoinColumn(name = "alert_id"))
    @Column(name = "type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @BatchSize(size = 50)
    private Set<AlertType> types = new LinkedHashSet<>();

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AlertStatus status;

    @Column(name = "instructions", nullable = false, length = 1000)
    private String instructions;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, length = 20)
    private AlertOrigin origin;

    @Column(name = "simulation_id")
    private UUID simulationId;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    @Column(name = "activation_at")
    private Instant activationAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public UUID getId() {
        return id;
    }

    public Set<AlertType> getTypes() {
        return types;
    }

    public void setTypes(Set<AlertType> types) {
        this.types = types == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(types);
    }

    public AlertStatus getStatus() {
        return status;
    }

    public void setStatus(AlertStatus status) {
        this.status = status;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public AlertOrigin getOrigin() {
        return origin;
    }

    public void setOrigin(AlertOrigin origin) {
        this.origin = origin;
    }

    public UUID getSimulationId() {
        return simulationId;
    }

    public void setSimulationId(UUID simulationId) {
        this.simulationId = simulationId;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getActivationAt() {
        return activationAt;
    }

    public void setActivationAt(Instant activationAt) {
        this.activationAt = activationAt;
    }

    public Instant getActivatedAt() {
        return activatedAt;
    }

    public void setActivatedAt(Instant activatedAt) {
        this.activatedAt = activatedAt;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(Instant archivedAt) {
        this.archivedAt = archivedAt;
    }

    public Long getVersion() {
        return version;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}