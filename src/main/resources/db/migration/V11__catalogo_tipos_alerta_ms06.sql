-- Catalogo unico de tipos de alerta compartido con MS06.
--
-- MS06 es la fuente de verdad del catalogo. MS03 replica las filas con
-- source='MS06' y conserva OPERATIVA como tipo local, reservado y fuera de la
-- sincronizacion. synced_at registra la ultima vez que una fila se actualizo
-- desde MS06; los tipos que MS06 deja de publicar se desactivan, nunca se
-- borran, porque las alertas historicas siguen apuntando a ellos.

-- 1. La clave foranea se recrea despues de ampliar las dos columnas.
ALTER TABLE alert_types DROP CONSTRAINT IF EXISTS fk_alert_types_catalog;

ALTER TABLE alert_type ALTER COLUMN code TYPE VARCHAR(40);
ALTER TABLE alert_types ALTER COLUMN type TYPE VARCHAR(40);

ALTER TABLE alert_type DROP CONSTRAINT IF EXISTS ck_alert_type_code;

ALTER TABLE alert_type
    ADD CONSTRAINT ck_alert_type_code
    CHECK (code ~ '^[A-Z][A-Z0-9_]{1,39}$');

ALTER TABLE alert_types
    ADD CONSTRAINT fk_alert_types_catalog
    FOREIGN KEY (type) REFERENCES alert_type (code) ON UPDATE CASCADE;

-- 2. Origen de la fila y fecha de la ultima sincronizacion.
ALTER TABLE alert_type ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'MS06';
ALTER TABLE alert_type ADD COLUMN synced_at TIMESTAMPTZ;

-- 3. Codigos canonicos de MS06. El ON UPDATE CASCADE de la clave foranea
--    actualiza las filas existentes de alert_types.
UPDATE alert_type
SET code = 'SISMO', label = 'Sismo', display_order = 1
WHERE code = 'EARTHQUAKE';

UPDATE alert_type
SET code = 'INCENDIO', label = 'Incendio', display_order = 2
WHERE code = 'FIRE';

UPDATE alert_type
SET code = 'OTRO', label = 'Otro', display_order = 6
WHERE code = 'OTHER';

-- 4. OPERATIVA es local: la sincronizacion nunca la modifica ni la desactiva.
UPDATE alert_type
SET label = 'Operativa',
    display_order = 1000,
    source = 'LOCAL',
    active = TRUE
WHERE code = 'OPERATIVA';

-- 5. Tipos iniciales de MS06.
INSERT INTO alert_type (code, label, display_order, active, source) VALUES
    ('DISTURBIOS', 'Disturbios', 3, TRUE, 'MS06'),
    ('INUNDACION', 'Inundación', 4, TRUE, 'MS06'),
    ('FUGA_QUIMICA', 'Fuga de Sustancias Químicas', 5, TRUE, 'MS06')
ON CONFLICT (code) DO UPDATE
SET label = EXCLUDED.label,
    display_order = EXCLUDED.display_order,
    active = EXCLUDED.active,
    source = EXCLUDED.source;
