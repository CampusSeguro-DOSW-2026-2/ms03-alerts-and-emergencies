-- Estados del ciclo de vida y sus transiciones como datos.
--
-- Agregar un estado o una transicion es insertar filas, no desplegar codigo. La
-- maquina de estados de MS03 lee estas dos tablas en lugar de un mapa fijo.
--
-- La tabla audit_status se mantiene aparte porque el historial guarda estados que ya
-- no existen en el ciclo de vida: ENDED sobrevive solo como valor historico.

CREATE TABLE alert_status (
    code          VARCHAR(20) PRIMARY KEY,
    label         VARCHAR(80) NOT NULL,
    display_order INTEGER     NOT NULL,
    editable      BOOLEAN     NOT NULL,
    final_state   BOOLEAN     NOT NULL,

    CONSTRAINT chh_alert_status_code
        CHECK (code ~ '^[A-Z][A-Z0-9_]{1,19}$'),

    CONSTRAINT chh_alert_status_label
        CHECK (char_length(btrim(label)) BETWEEN 3 AND 80),

    CONSTRAINT chh_alert_status_order
        CHECK (display_order > 0)
);

INSERT INTO alert_status (code, label, display_order, editable, final_state) VALUES
    ('DRAFT', 'Borrador', 1, TRUE, FALSE),
    ('SCHEDULED', 'Programada', 2, TRUE, FALSE),
    ('ACTIVE', 'Activa', 3, FALSE, FALSE),
    ('ARCHIVED', 'Archivada', 4, FALSE, TRUE);

CREATE TABLE alert_status_transition (
    from_code VARCHAR(20) NOT NULL,
    to_code   VARCHAR(20) NOT NULL,

    CONSTRAINT pk_alert_status_transition
        PRIMARY KEY (from_code, to_code),

    CONSTRAINT fk_alert_status_transition_from
        FOREIGN KEY (from_code) REFERENCES alert_status (code) ON UPDATE CASCADE,

    CONSTRAINT fk_alert_status_transition_to
        FOREIGN KEY (to_code) REFERENCES alert_status (code) ON UPDATE CASCADE,

    CONSTRAINT chh_alert_status_transition_changes
        CHECK (from_code <> to_code)
);

INSERT INTO alert_status_transition (from_code, to_code) VALUES
    ('DRAFT', 'SCHEDULED'),
    ('DRAFT', 'ACTIVE'),
    ('DRAFT', 'ARCHIVED'),
    ('SCHEDULED', 'ACTIVE'),
    ('SCHEDULED', 'ARCHIVED'),
    ('ACTIVE', 'ARCHIVED');

ALTER TABLE alerts DROP CONSTRAINT IF EXISTS ck_alerts_status;

ALTER TABLE alerts
    ADD CONSTRAINT fk_alerts_status
    FOREIGN KEY (status) REFERENCES alert_status (code) ON UPDATE CASCADE;
