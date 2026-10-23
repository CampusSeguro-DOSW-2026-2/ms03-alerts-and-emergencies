ALTER TABLE alerts
    ADD COLUMN IF NOT EXISTS title VARCHAR(150),
    ADD COLUMN IF NOT EXISTS event_id UUID;

ALTER TABLE alerts
    DROP CONSTRAINT IF EXISTS ck_alerts_type,
    DROP CONSTRAINT IF EXISTS ck_alerts_origin,
    DROP CONSTRAINT IF EXISTS ck_alerts_simulation_id,
    DROP CONSTRAINT IF EXISTS ck_alerts_title_not_blank,
    DROP CONSTRAINT IF EXISTS ck_alerts_event_id;

UPDATE alerts
SET title = 'Alerta migrada'
WHERE title IS NULL;

ALTER TABLE alerts
    ALTER COLUMN title SET NOT NULL;

DO $$
DECLARE
    has_simulation_id BOOLEAN;
    has_invalid_simulation_ids BOOLEAN;
BEGIN
    SELECT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'alerts'
          AND column_name = 'simulation_id'
    ) INTO has_simulation_id;

    IF has_simulation_id THEN
        EXECUTE $query$
            SELECT EXISTS (
                SELECT 1
                FROM alerts
                WHERE origin = 'SIMULATION'
                  AND (simulation_id IS NULL OR simulation_id !~*
                       '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$')
            )
        $query$ INTO has_invalid_simulation_ids;

        IF has_invalid_simulation_ids THEN
            RAISE EXCEPTION
                'Cannot migrate SIMULATION alerts: simulation_id must contain a UUID to map to event_id';
        END IF;

        EXECUTE $query$
            UPDATE alerts
            SET event_id = simulation_id::uuid,
                origin = 'EVENT'
            WHERE origin = 'SIMULATION'
        $query$;
    END IF;
END
$$;

ALTER TABLE alerts
    ADD CONSTRAINT ck_alerts_type
        CHECK (type IN ('EARTHQUAKE', 'SIMULATION', 'FIRE', 'OTHER')),
    ADD CONSTRAINT ck_alerts_origin
        CHECK (origin IN ('MANUAL', 'EVENT')),
    ADD CONSTRAINT ck_alerts_title_not_blank
        CHECK (length(btrim(title)) > 0),
    ADD CONSTRAINT ck_alerts_event_id
        CHECK (
            (origin = 'EVENT' AND event_id IS NOT NULL)
            OR
            (origin = 'MANUAL' AND event_id IS NULL)
        );
