-- Bandeja de salida de la auditoria: cada hecho de MS03 se entrega a MS08.
--
-- La fila se escribe en la misma transaccion que el cambio de la alerta, asi que un
-- hecho auditado no se pierde si MS08 no responde. El despachador lo envia despues y
-- reintenta con backoff; MS08 deduplica por id, de modo que reintentar es seguro.

CREATE TABLE audit_outbox (
    id              UUID         PRIMARY KEY,
    payload         TEXT         NOT NULL,
    estado          VARCHAR(20)  NOT NULL,
    intentos        INTEGER      NOT NULL DEFAULT 0,
    proximo_intento TIMESTAMPTZ  NOT NULL,
    ultimo_error    VARCHAR(500),
    enviado_en      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chh_audit_outbox_estado
        CHECK (estado IN ('PENDIENTE', 'ENVIANDO', 'ENVIADO', 'DESCARTADO')),

    CONSTRAINT chh_audit_outbox_intentos
        CHECK (intentos >= 0)
);

-- El despachador solo busca pendientes vencidos.
CREATE INDEX idx_audit_outbox_pendientes
    ON audit_outbox (proximo_intento)
    WHERE estado = 'PENDIENTE';

-- Recupera los lotes que quedaron tomados por un reinicio del proceso.
CREATE INDEX idx_audit_outbox_enviando
    ON audit_outbox (updated_at)
    WHERE estado = 'ENVIANDO';
