-- Regla 5: un solo correo por usuario y alerta
ALTER TABLE notification_deliveries
    ADD CONSTRAINT uq_deliveries_alert_user UNIQUE (alert_id, user_id);

-- Regla 6: una sola confirmacion por usuario y alerta
ALTER TABLE alert_confirmations
    ADD CONSTRAINT uq_confirmations_alert_user UNIQUE (alert_id, user_id);

-- Sin destinatarios repetidos en la misma alerta
ALTER TABLE alert_recipients
    ADD CONSTRAINT uq_recipients_alert_ref
    UNIQUE (alert_id, recipient_type, recipient_ref_id);

-- Sin alcance repetido en la misma alerta (NULLS NOT DISTINCT: PostgreSQL 15+)
ALTER TABLE alert_scope_items
    ADD CONSTRAINT uq_scope_items_alert_ref
    UNIQUE NULLS NOT DISTINCT (alert_id, scope_type, scope_ref_id);

-- v4 (tarea 0.1.1.3): idempotencia de la integracion con MS06.
-- Un simulation_id no puede tener mas de una alerta viva (SCHEDULED o ACTIVE)
-- a la vez. Una alerta ENDED/ARCHIVED o DRAFT no bloquea. Si MS06 reintenta,
-- el service captura la violacion (DataIntegrityViolationException) y responde
-- con la alerta existente / DUPLICATE_SIMULATION_ALERT segun se decida.
CREATE UNIQUE INDEX uq_alerts_simulation_live
    ON alerts (simulation_id)
    WHERE simulation_id IS NOT NULL
      AND status IN ('SCHEDULED', 'ACTIVE');

-- Indices de consulta

-- Scheduler: busca alertas SCHEDULED vencidas
CREATE INDEX idx_alerts_status_activation_at
    ON alerts (status, activation_at);

-- Listado ordenado por fecha de creacion (mas recientes primero)
CREATE INDEX idx_alerts_created_at
    ON alerts (created_at DESC);

-- Busqueda por simulacro, cualquier estado (filtro simulationId del listado, HU 5.1.2;
-- MS06 finaliza por simulation_id). Complementa al unico parcial de arriba.
CREATE INDEX idx_alerts_simulation_id
    ON alerts (simulation_id)
    WHERE simulation_id IS NOT NULL;

-- Alcance de una alerta (las unicas anteriores ya cubren recipients,
-- deliveries y confirmations por alert_id como primera columna)
CREATE INDEX idx_scope_items_alert_id
    ON alert_scope_items (alert_id);

-- Historial de una alerta en orden cronologico
CREATE INDEX idx_audit_alert_created
    ON audit_events (alert_id, created_at);

-- Consulta del destinatario: "mis alertas confirmadas"
CREATE INDEX idx_confirmations_user_id
    ON alert_confirmations (user_id);
