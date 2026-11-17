-- Alinea el esquema con el contrato de MS06: simulation_id y origen SIMULATION.
ALTER TABLE alerts DROP CONSTRAINT IF EXISTS ck_alerts_origin;
ALTER TABLE alerts DROP CONSTRAINT IF EXISTS ck_alerts_event_id;

ALTER TABLE alerts RENAME COLUMN event_id TO simulation_id;

UPDATE alerts
SET origin = 'SIMULATION'
WHERE origin = 'EVENT';

ALTER TABLE alerts
    ADD CONSTRAINT ck_alerts_origin
    CHECK (origin IN ('MANUAL', 'SIMULATION'));

ALTER TABLE alerts
    ADD CONSTRAINT ck_alerts_simulation_id
    CHECK (
        (origin = 'SIMULATION' AND simulation_id IS NOT NULL)
        OR
        (origin = 'MANUAL' AND simulation_id IS NULL)
    );

ALTER INDEX uq_alerts_event_live RENAME TO uq_alerts_simulation_live;
ALTER INDEX idx_alerts_event_id RENAME TO idx_alerts_simulation_id;

COMMENT ON COLUMN alerts.simulation_id
    IS 'Identificador del simulacro asociado en MS06, cuando aplica';
