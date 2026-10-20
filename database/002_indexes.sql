-- Regla 5: un solo correo por usuario y alerta.
ALTER TABLE notification_deliveries
    ADD CONSTRAINT uq_deliveries_alert_user
    UNIQUE (alert_id, user_id);


-- Regla 6: una sola confirmación por usuario y alerta.
ALTER TABLE alert_confirmations
    ADD CONSTRAINT uq_confirmations_alert_user
    UNIQUE (alert_id, user_id);


-- Sin destinatarios repetidos en la misma alerta.
ALTER TABLE alert_recipients
    ADD CONSTRAINT uq_recipients_alert_ref
    UNIQUE (alert_id, recipient_type, recipient_ref_id);


-- Sin alcance repetido en la misma alerta.
-- Requiere PostgreSQL 15 o superior.
ALTER TABLE alert_scope_items
    ADD CONSTRAINT uq_scope_items_alert_ref
    UNIQUE NULLS NOT DISTINCT (alert_id, scope_type, scope_ref_id);


-- Idempotencia de la integración con MS06 (Eventos).
-- Un evento no puede tener más de una alerta programada o activa
-- al mismo tiempo.
-- Las alertas DRAFT, ENDED y ARCHIVED no bloquean.
CREATE UNIQUE INDEX uq_alerts_event_live
    ON alerts (event_id)
    WHERE event_id IS NOT NULL
      AND status IN ('SCHEDULED', 'ACTIVE');


-- Índices de consulta.

-- Scheduler: busca alertas SCHEDULED cuya activación está vencida.
CREATE INDEX idx_alerts_status_activation_at
    ON alerts (status, activation_at);


-- Listado de alertas ordenado por fecha de creación.
CREATE INDEX idx_alerts_created_at
    ON alerts (created_at DESC);


-- Búsqueda de alertas por evento, independientemente de su estado.
CREATE INDEX idx_alerts_event_id
    ON alerts (event_id)
    WHERE event_id IS NOT NULL;


-- Consulta del alcance de una alerta.
CREATE INDEX idx_scope_items_alert_id
    ON alert_scope_items (alert_id);


-- Historial de una alerta en orden cronológico.
CREATE INDEX idx_audit_alert_created
    ON audit_events (alert_id, created_at);


-- Consulta de alertas confirmadas por un usuario.
CREATE INDEX idx_confirmations_user_id
    ON alert_confirmations (user_id);
    