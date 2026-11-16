-- Catalogo de tipos de alerta.
--
-- Los tipos dejan de ser un enum del codigo y pasan a ser filas: agregar un tipo
-- nuevo es insertar aqui, sin desplegar. La etiqueta que ve el usuario y el orden
-- en que se muestran tambien son datos.
--
-- La tabla alert_types conserva su columna type y ahora apunta al catalogo con una
-- clave foranea, asi que un tipo inexistente ya no puede guardarse.

CREATE TABLE alert_type (
    code          VARCHAR(20)  PRIMARY KEY,
    label         VARCHAR(80)  NOT NULL,
    display_order INTEGER      NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,

    CONSTRAINT ck_alert_type_code
        CHECK (code ~ '^[A-Z][A-Z0-9_]{1,19}$'),

    CONSTRAINT ck_alert_type_label
        CHECK (char_length(btrim(label)) BETWEEN 3 AND 80),

    CONSTRAINT ck_alert_type_display_order
        CHECK (display_order > 0)
);

INSERT INTO alert_type (code, label, display_order) VALUES
    ('EARTHQUAKE', 'Terremoto', 1),
    ('FIRE', 'Incendio', 2),
    ('OTHER', 'Otra emergencia', 3),
    ('OPERATIVA', 'Operativa', 4);

ALTER TABLE alert_types DROP CONSTRAINT IF EXISTS ck_alert_types_type;

ALTER TABLE alert_types
    ADD CONSTRAINT fk_alert_types_catalog
    FOREIGN KEY (type) REFERENCES alert_type (code) ON UPDATE CASCADE;
