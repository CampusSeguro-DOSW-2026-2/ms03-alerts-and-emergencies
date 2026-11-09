-- An event (simulation_id) can have several live alerts, one per purpose
-- (brigadiers, general, edit, finish). The unique index from 004 allowed only one
-- live alert per event, so MS06 failed with 409 when creating the second one.
--
-- This script is idempotent: it can be applied more than once without failing.

BEGIN;

DROP INDEX IF EXISTS uq_alerts_simulation_live;

CREATE UNIQUE INDEX IF NOT EXISTS uq_alerts_simulation_purpose_live
    ON alerts (simulation_id, COALESCE(purpose, ''))
    WHERE simulation_id IS NOT NULL AND status IN ('SCHEDULED', 'ACTIVE');

COMMIT;
