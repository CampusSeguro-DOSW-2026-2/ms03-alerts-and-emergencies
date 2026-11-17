-- Catalogos de auditoria: acciones auditables y estados historicos.
--
-- Crece con cada operacion nueva que se audita. Se mantiene aparte de alert_status
-- porque el historial guarda valores que ya no existen en el ciclo de vida: END y
-- ENDED sobreviven solo como registros historicos.

CREATE TABLE audit_action (
    code  VARCHAR(40) PRIMARY KEY,
    label VARCHAR(120) NOT NULL,

    CONSTRAINT chh_audit_action_code
        CHECK (code ~ '^[A-Z][A-Z0-9_]{1,39}$'),

    CONSTRAINT chh_audit_action_label
        CHECK (char_length(btrim(label)) BETWEEN 3 AND 120)
);

INSERT INTO audit_action (code, label) VALUES
    ('CREATE', 'Alerta creada'),
    ('UPDATE', 'Alerta actualizada'),
    ('SCHEDULE', 'Alerta programada'),
    ('ACTIVATE', 'Alerta activada'),
    ('END', 'Alerta finalizada (historico)'),
    ('ARCHIVE', 'Alerta archivada'),
    ('CONFIRM', 'Recepcion confirmada'),
    ('NOTIFICATION_SENT', 'Notificacion enviada'),
    ('NOTIFICATION_FAILED', 'Notificacion fallida'),
    ('RECIPIENT_RESOLUTION_FAILED', 'No se pudieron resolver los destinatarios');

CREATE TABLE audit_status (
    code          VARCHAR(20) PRIMARY KEY,
    label         VARCHAR(80) NOT NULL,
    display_order INTEGER     NOT NULL,

    CONSTRAINT chh_audit_status_code
        CHECK (code ~ '^[A-Z][A-Z0-9_]{1,19}$'),

    CONSTRAINT chh_audit_status_label
        CHECK (char_length(btrim(label)) BETWEEN 3 AND 80),

    CONSTRAINT chh_audit_status_order
        CHECK (display_order > 0)
);

INSERT INTO audit_status (code, label, display_order) VALUES
    ('DRAFT', 'Borrador', 1),
    ('SCHEDULED', 'Programada', 2),
    ('ACTIVE', 'Activa', 3),
    ('ARCHIVED', 'Archivada', 4),
    ('ENDED', 'Finalizada (historico)', 5);

ALTER TABLE audit_events DROP CONSTRAINT IF EXISTS ck_audit_action;
ALTER TABLE audit_events DROP CONSTRAINT IF EXISTS ck_audit_from_status;
ALTER TABLE audit_events DROP CONSTRAINT IF EXISTS ck_audit_to_status;

ALTER TABLE audit_events
    ADD CONSTRAINT fk_audit_events_action
    FOREIGN KEY (action) REFERENCES audit_action (code) ON UPDATE CASCADE;

ALTER TABLE audit_events
    ADD CONSTRAINT fk_audit_events_from_status
    FOREIGN KEY (from_status) REFERENCES audit_status (code) ON UPDATE CASCADE;

ALTER TABLE audit_events
    ADD CONSTRAINT fk_audit_events_to_status
    FOREIGN KEY (to_status) REFERENCES audit_status (code) ON UPDATE CASCADE;
