-- Adds the purpose of an alert inside its simulation/event.
--
-- An event can create several live alerts (brigadiers, general, edit, finish)
-- that share the same simulation_id. The purpose tells them apart and is part
-- of the idempotency key used by MS06.
--
-- This script is idempotent: it can be applied more than once without failing.

BEGIN;

ALTER TABLE alerts
    ADD COLUMN IF NOT EXISTS purpose VARCHAR(32);

CREATE INDEX IF NOT EXISTS idx_alerts_simulation_purpose
    ON alerts (simulation_id, purpose);

COMMIT;
