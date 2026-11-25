package edu.eci.dosw.campusseguro.alertas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Hecho de auditoría pendiente de entregar a MS08.
 *
 * <p>Se escribe en la misma transacción que el cambio de la alerta, así que existe exactamente
 * cuando el hecho local existe. El despachador lo envía después y MS08 deduplica por {@code id}.
 */
@Entity
@Table(name = "audit_outbox")
public class AuditOutboxEntity extends BaseEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "payload", nullable = false, updatable = false, length = 4000)
    private String payload;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado;

    @Column(name = "intentos", nullable = false)
    private int intentos;

    @Column(name = "proximo_intento", nullable = false)
    private Instant proximoIntento;

    @Column(name = "ultimo_error", length = 500)
    private String ultimoError;

    @Column(name = "enviado_en")
    private Instant enviadoEn;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public int getIntentos() {
        return intentos;
    }

    public void setIntentos(int intentos) {
        this.intentos = intentos;
    }

    public Instant getProximoIntento() {
        return proximoIntento;
    }

    public void setProximoIntento(Instant proximoIntento) {
        this.proximoIntento = proximoIntento;
    }

    public String getUltimoError() {
        return ultimoError;
    }

    public void setUltimoError(String ultimoError) {
        this.ultimoError = ultimoError;
    }

    public Instant getEnviadoEn() {
        return enviadoEn;
    }

    public void setEnviadoEn(Instant enviadoEn) {
        this.enviadoEn = enviadoEn;
    }
}
