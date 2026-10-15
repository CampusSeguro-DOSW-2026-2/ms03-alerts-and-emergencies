CREATE TABLE alerts (
    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    type           VARCHAR(20)   NOT NULL,
    status         VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    instructions   VARCHAR(1000) NOT NULL,
    origin         VARCHAR(20)   NOT NULL DEFAULT 'MANUAL',
    simulation_id  VARCHAR(64),
    created_by     VARCHAR(64)   NOT NULL,
    activation_at  TIMESTAMPTZ,
    activated_at   TIMESTAMPTZ,
    ended_at       TIMESTAMPTZ,
    archived_at    TIMESTAMPTZ,
    version        BIGINT        NOT NULL DEFAULT 0,  
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT ck_alerts_type
        CHECK (type IN ('EARTHQUAKE')),
    CONSTRAINT ck_alerts_status
        CHECK (status IN ('DRAFT', 'SCHEDULED', 'ACTIVE', 'ENDED', 'ARCHIVED')),
    CONSTRAINT ck_alerts_origin
        CHECK (origin IN ('MANUAL', 'SIMULATION')),
    CONSTRAINT ck_alerts_instructions_not_blank
        CHECK (length(btrim(instructions)) > 0),

    -- Regla 9: el origen Simulacro exige el ID del simulacro (y solo ese origen lo lleva)
    CONSTRAINT ck_alerts_simulation_id
        CHECK ((origin = 'SIMULATION' AND simulation_id IS NOT NULL)
            OR (origin = 'MANUAL'     AND simulation_id IS NULL)),

    -- Coherencia entre el estado y las fechas del ciclo de vida
    CONSTRAINT ck_alerts_scheduled_has_date
        CHECK (status <> 'SCHEDULED' OR activation_at IS NOT NULL),
    CONSTRAINT ck_alerts_active_has_date
        CHECK (status NOT IN ('ACTIVE', 'ENDED', 'ARCHIVED') OR activated_at IS NOT NULL),
    CONSTRAINT ck_alerts_ended_has_date
        CHECK (status NOT IN ('ENDED', 'ARCHIVED') OR ended_at IS NOT NULL),
    CONSTRAINT ck_alerts_archived_has_date
        CHECK (status <> 'ARCHIVED' OR archived_at IS NOT NULL)
);

COMMENT ON TABLE  alerts          IS 'Alerta de emergencia o simulacro y su ciclo de vida';
COMMENT ON COLUMN alerts.version  IS 'Bloqueo optimista de JPA (@Version)';
COMMENT ON COLUMN alerts.created_by IS 'ID del usuario o servicio que creo la alerta (MS01/MS06)';

CREATE TABLE alert_scope_items (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    alert_id      UUID         NOT NULL,
    scope_type    VARCHAR(20)  NOT NULL,
    scope_ref_id  VARCHAR(64),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_scope_items_alert
        FOREIGN KEY (alert_id) REFERENCES alerts (id) ON DELETE CASCADE,
    CONSTRAINT ck_scope_items_type
        CHECK (scope_type IN ('CAMPUS', 'BUILDING', 'ZONE')),
    -- CAMPUS no lleva referencia; BUILDING y ZONE si
    CONSTRAINT ck_scope_items_ref
        CHECK ((scope_type = 'CAMPUS'  AND scope_ref_id IS NULL)
            OR (scope_type <> 'CAMPUS' AND scope_ref_id IS NOT NULL))
);

CREATE TABLE alert_recipients (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    alert_id          UUID         NOT NULL,
    recipient_type    VARCHAR(20)  NOT NULL,
    recipient_ref_id  VARCHAR(64)  NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_recipients_alert
        FOREIGN KEY (alert_id) REFERENCES alerts (id) ON DELETE CASCADE,
    CONSTRAINT ck_recipients_type
        CHECK (recipient_type IN ('USER', 'ROLE'))
);


CREATE TABLE notification_deliveries (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    alert_id      UUID          NOT NULL,
    user_id       VARCHAR(64)   NOT NULL,
    email         VARCHAR(254)  NOT NULL,
    status        VARCHAR(20)   NOT NULL,
    error_reason  VARCHAR(500),
    sent_at       TIMESTAMPTZ,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT fk_deliveries_alert
        FOREIGN KEY (alert_id) REFERENCES alerts (id) ON DELETE CASCADE,
    CONSTRAINT ck_deliveries_status
        CHECK (status IN ('SENT', 'FAILED')),
    CONSTRAINT ck_deliveries_sent_has_date
        CHECK (status <> 'SENT' OR sent_at IS NOT NULL),
    CONSTRAINT ck_deliveries_failed_has_reason
        CHECK (status <> 'FAILED' OR error_reason IS NOT NULL)
);

CREATE TABLE alert_confirmations (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    alert_id      UUID         NOT NULL,
    user_id       VARCHAR(64)  NOT NULL,
    confirmed_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_confirmations_alert
        FOREIGN KEY (alert_id) REFERENCES alerts (id) ON DELETE CASCADE
);

CREATE TABLE audit_events (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    alert_id     UUID         NOT NULL,
    action       VARCHAR(40)  NOT NULL,
    actor        VARCHAR(64)  NOT NULL,   -- user id, SYSTEM_SCHEDULER o SERVICE_MS06
    from_status  VARCHAR(20),
    to_status    VARCHAR(20),
    details      VARCHAR(500),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_audit_alert
        FOREIGN KEY (alert_id) REFERENCES alerts (id) ON DELETE RESTRICT,
    CONSTRAINT ck_audit_action
        CHECK (action IN ('CREATE', 'UPDATE', 'SCHEDULE', 'ACTIVATE', 'END',
                          'ARCHIVE', 'CONFIRM', 'NOTIFICATION_FAILED',
                          'RECIPIENT_RESOLUTION_FAILED')),
    CONSTRAINT ck_audit_from_status
        CHECK (from_status IS NULL
            OR from_status IN ('DRAFT', 'SCHEDULED', 'ACTIVE', 'ENDED', 'ARCHIVED')),
    CONSTRAINT ck_audit_to_status
        CHECK (to_status IS NULL
            OR to_status IN ('DRAFT', 'SCHEDULED', 'ACTIVE', 'ENDED', 'ARCHIVED'))
);
