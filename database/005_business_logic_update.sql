-- Migracion de logica de negocio de MS03.
--
-- Aplicar con MS03 detenido y con un respaldo previo de la base. El codigo
-- nuevo usa spring.jpa.hibernate.ddl-auto=validate, por lo que este script
-- debe ejecutarse antes de arrancar la version nueva.
--
-- Rollback preferido: restaurar el respaldo y desplegar la version anterior.
-- Un rollback solo por SQL perderia el detalle de los tipos multiples.

BEGIN;

-- 1. Tipos multiples por alerta.
CREATE TABLE alert_types (
    alert_id  UUID        NOT NULL,
    type      VARCHAR(20) NOT NULL,

    CONSTRAINT pk_alert_types
        PRIMARY KEY (alert_id, type),

    CONSTRAINT fk_alert_types_alert
        FOREIGN KEY (alert_id)
        REFERENCES alerts (id)
        ON DELETE CASCADE,

    CONSTRAINT ck_alert_types_type
        CHECK (type IN ('EARTHQUAKE', 'FIRE', 'OTHER', 'OPERATIVA'))
);

INSERT INTO alert_types (alert_id, type)
SELECT id,
       CASE type
           WHEN 'SIMULATION' THEN 'OTHER'
           ELSE type
       END
FROM alerts;

-- 2. Retiro de ENDED y de ended_at.
UPDATE alerts
SET status = 'ARCHIVED',
    archived_at = COALESCE(archived_at, ended_at, now()),
    updated_at = now()
WHERE status = 'ENDED';

ALTER TABLE alerts DROP CONSTRAINT IF EXISTS ck_alerts_type;
ALTER TABLE alerts DROP CONSTRAINT IF EXISTS ck_alerts_status;
ALTER TABLE alerts DROP CONSTRAINT IF EXISTS ck_alerts_ended_has_date;
ALTER TABLE alerts DROP CONSTRAINT IF EXISTS ck_alerts_active_has_date;
ALTER TABLE alerts DROP CONSTRAINT IF EXISTS ck_alerts_archived_has_date;

ALTER TABLE alerts DROP COLUMN type;
ALTER TABLE alerts DROP COLUMN ended_at;

ALTER TABLE alerts
    ADD CONSTRAINT ck_alerts_status
    CHECK (status IN ('DRAFT', 'SCHEDULED', 'ACTIVE', 'ARCHIVED'));

ALTER TABLE alerts
    ADD CONSTRAINT ck_alerts_active_has_date
    CHECK (status <> 'ACTIVE' OR activated_at IS NOT NULL);

ALTER TABLE alerts
    ADD CONSTRAINT ck_alerts_archived_has_date
    CHECK (status <> 'ARCHIVED' OR archived_at IS NOT NULL);

-- 3. Auditoria compatible con filas historicas END/ENDED.
ALTER TABLE audit_events DROP CONSTRAINT IF EXISTS ck_audit_action;
ALTER TABLE audit_events DROP CONSTRAINT IF EXISTS ck_audit_from_status;
ALTER TABLE audit_events DROP CONSTRAINT IF EXISTS ck_audit_to_status;

ALTER TABLE audit_events
    ADD CONSTRAINT ck_audit_action
    CHECK (action IN (
        'CREATE',
        'UPDATE',
        'SCHEDULE',
        'ACTIVATE',
        'END',
        'ARCHIVE',
        'CONFIRM',
        'NOTIFICATION_SENT',
        'NOTIFICATION_FAILED',
        'RECIPIENT_RESOLUTION_FAILED'
    ));

ALTER TABLE audit_events
    ADD CONSTRAINT ck_audit_from_status
    CHECK (
        from_status IS NULL
        OR from_status IN ('DRAFT', 'SCHEDULED', 'ACTIVE', 'ENDED', 'ARCHIVED')
    );

ALTER TABLE audit_events
    ADD CONSTRAINT ck_audit_to_status
    CHECK (
        to_status IS NULL
        OR to_status IN ('DRAFT', 'SCHEDULED', 'ACTIVE', 'ENDED', 'ARCHIVED')
    );

-- 4. Indices de bandeja y filtros.
CREATE INDEX idx_alert_types_type
    ON alert_types (type, alert_id);

CREATE INDEX idx_alert_recipients_inbox
    ON alert_recipients (recipient_type, recipient_ref_id, alert_id);

CREATE INDEX idx_deliveries_user_alert
    ON notification_deliveries (user_id, alert_id);

CREATE INDEX idx_confirmations_user_alert
    ON alert_confirmations (user_id, alert_id);

CREATE INDEX idx_alerts_status_activated_at
    ON alerts (status, activated_at);

-- 5. Verificacion de integridad posterior a la migracion.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM alerts a
        WHERE NOT EXISTS (
            SELECT 1
            FROM alert_types t
            WHERE t.alert_id = a.id
        )
    ) THEN
        RAISE EXCEPTION 'Hay alertas sin al menos un tipo en alert_types';
    END IF;

    IF EXISTS (SELECT 1 FROM alerts WHERE status = 'ENDED') THEN
        RAISE EXCEPTION 'Quedaron alertas en estado ENDED';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'alerts'
          AND column_name = 'ended_at'
    ) THEN
        RAISE EXCEPTION 'La columna alerts.ended_at no fue eliminada';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'alerts'
          AND column_name = 'type'
    ) THEN
        RAISE EXCEPTION 'La columna alerts.type no fue eliminada';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM alerts
        WHERE status = 'ARCHIVED'
          AND archived_at IS NULL
    ) THEN
        RAISE EXCEPTION 'Hay alertas ARCHIVED sin archived_at';
    END IF;
END $$;

COMMIT;

-- Verificacion manual sugerida:
-- SELECT (SELECT count(*) FROM alerts) AS alertas,
--        (SELECT count(*) FROM alert_types) AS tipos;
-- SELECT count(*) FROM alerts WHERE status = 'ENDED';
-- SELECT conname, convalidated
-- FROM pg_constraint
-- WHERE conrelid IN ('alerts'::regclass, 'alert_types'::regclass,
--                    'audit_events'::regclass)
-- ORDER BY conname;
