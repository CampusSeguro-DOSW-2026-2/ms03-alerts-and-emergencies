package edu.eci.dosw.campusseguro.alertas.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

class AlertMigrationIT {

    private static final UUID ENDED_SIMULATION =
            UUID.fromString("10000000-0000-4000-8000-000000000001");

    private static final UUID ACTIVE_SIMULATION =
            UUID.fromString("10000000-0000-4000-8000-000000000002");

    private static final UUID ARCHIVED_MANUAL =
            UUID.fromString("10000000-0000-4000-8000-000000000003");

    private static final UUID ACTIVE_SISMO =
            UUID.fromString("10000000-0000-4000-8000-000000000004");

    private static final UUID DRAFT_INCENDIO =
            UUID.fromString("10000000-0000-4000-8000-000000000005");

    /**
     * Version del esquema en la que todavia existian alerts.type y alerts.ended_at. Se migra hasta
     * aqui, se siembran las filas legadas y despues se completa la migracion a la ultima version.
     */
    private static final String LEGACY_TARGET_VERSION = "4";

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("campusseguro_alerts_migration")
            .withUsername("campusseguro")
            .withPassword("campusseguro");

    static {
        POSTGRES.start();
    }

    private static Connection connection;

    @BeforeAll
    static void prepareLegacyDatabase() throws Exception {
        connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        migrateTo(LEGACY_TARGET_VERSION);
        seedLegacyRows();
        migrateTo(null);
    }

    @AfterAll
    static void close() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }

    @Test
    void migration_shouldPreserveLegacyRowsAndReplaceEndedWithArchived() throws Exception {
        assertEquals(5, count("SELECT count(*) FROM alerts"));
        assertEquals(5, count("SELECT count(*) FROM alert_types"));
        assertEquals(0, count("""
                SELECT count(*)
                FROM alerts a
                WHERE NOT EXISTS (SELECT 1 FROM alert_types t WHERE t.alert_id = a.id)
                """));
        assertEquals(0, count("SELECT count(*) FROM alerts WHERE status = 'ENDED'"));
        assertEquals(0, count("""
                SELECT count(*)
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = 'alerts'
                  AND column_name IN ('type', 'ended_at')
                """));

        assertEquals("OTRO", typeOf(ENDED_SIMULATION));
        assertEquals("OTRO", typeOf(ACTIVE_SIMULATION));
        assertEquals("OTRO", typeOf(ARCHIVED_MANUAL));
        assertEquals("SISMO", typeOf(ACTIVE_SISMO));
        assertEquals("INCENDIO", typeOf(DRAFT_INCENDIO));

        assertEquals("ARCHIVED", statusOf(ENDED_SIMULATION));
        assertNotNull(archivedAtOf(ENDED_SIMULATION));
        assertNull(archivedAtOf(ACTIVE_SIMULATION));
        assertEquals("ACTIVE", statusOf(ACTIVE_SIMULATION));
        assertEquals("ARCHIVED", statusOf(ARCHIVED_MANUAL));

        assertEquals(1, count("""
                SELECT count(*)
                FROM audit_events
                WHERE alert_id = '%s'
                  AND action = 'END'
                  AND from_status = 'ACTIVE'
                  AND to_status = 'ENDED'
                """.formatted(ENDED_SIMULATION)));
    }

    @Test
    void migration_shouldSeedMs06CatalogRenameLegacyCodesAndKeepOperativaLocal() throws Exception {
        assertEquals(7, count("SELECT count(*) FROM alert_type"));
        assertEquals(0, count("""
                SELECT count(*)
                FROM alert_type
                WHERE code IN ('EARTHQUAKE', 'FIRE', 'OTHER')
                """));

        assertEquals(1, count("""
                SELECT count(*)
                FROM alert_type
                WHERE code = 'SISMO' AND label = 'Sismo' AND display_order = 1
                  AND source = 'MS06' AND active
                """));
        assertEquals(1, count("""
                SELECT count(*)
                FROM alert_type
                WHERE code = 'INCENDIO' AND label = 'Incendio' AND display_order = 2
                  AND source = 'MS06' AND active
                """));
        assertEquals(1, count("""
                SELECT count(*)
                FROM alert_type
                WHERE code = 'DISTURBIOS' AND label = 'Disturbios' AND display_order = 3
                  AND source = 'MS06' AND active
                """));
        assertEquals(1, count("""
                SELECT count(*)
                FROM alert_type
                WHERE code = 'INUNDACION' AND label = 'Inundación' AND display_order = 4
                  AND source = 'MS06' AND active
                """));
        assertEquals(1, count("""
                SELECT count(*)
                FROM alert_type
                WHERE code = 'FUGA_QUIMICA' AND label = 'Fuga de Sustancias Químicas'
                  AND display_order = 5 AND source = 'MS06' AND active
                """));
        assertEquals(1, count("""
                SELECT count(*)
                FROM alert_type
                WHERE code = 'OTRO' AND label = 'Otro' AND display_order = 6
                  AND source = 'MS06' AND active
                """));
        assertEquals(1, count("""
                SELECT count(*)
                FROM alert_type
                WHERE code = 'OPERATIVA' AND label = 'Operativa' AND display_order = 1000
                  AND source = 'LOCAL' AND active AND synced_at IS NULL
                """));

        assertEquals(2, count("""
                SELECT count(*)
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = 'alert_type'
                  AND column_name IN ('source', 'synced_at')
                """));
        assertEquals(40, columnLength("alert_type", "code"));
        assertEquals(40, columnLength("alert_types", "type"));
        assertTrue(constraintDefinition("ck_alert_type_code").contains("39"));
        assertTrue(constraintDefinition("fk_alert_types_catalog")
                .contains("ON UPDATE CASCADE"));
    }

    @Test
    void migration_shouldEnforceNewStatusesAndIndexes() throws Exception {
        assertThrows(SQLException.class, () -> execute("""
                INSERT INTO alerts (id, title, status, instructions, origin, created_by, activated_at)
                VALUES ('20000000-0000-4000-8000-000000000001',
                        'Estado legado', 'ENDED', 'x', 'MANUAL', 'user', now())
                """));

        assertTrue(indexExists("idx_alert_types_type"));
        assertTrue(indexExists("idx_alert_recipients_inbox"));
        assertTrue(indexExists("idx_deliveries_user_alert"));
        assertTrue(indexExists("idx_confirmations_user_alert"));
        assertTrue(indexExists("idx_alerts_status_activated_at"));
        assertTrue(indexExists("idx_alerts_simulation_purpose"));
        assertTrue(indexExists("uq_alerts_simulation_purpose_live"));
        assertFalse(indexExists("uq_alerts_simulation_live"));
        assertEquals(1, count("""
                SELECT count(*)
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = 'alerts'
                  AND column_name = 'purpose'
                """));

        assertTrue(constraintDefinition("fk_audit_events_action").contains("audit_action"));
    }

    @Test
    void migration_shouldSeedTheStatusCatalogAndItsTransitions() throws Exception {
        assertEquals(4, count("SELECT count(*) FROM alert_status"));
        assertEquals(6, count("SELECT count(*) FROM alert_status_transition"));

        assertEquals(0, count("""
                SELECT count(*)
                FROM alerts a
                WHERE NOT EXISTS (SELECT 1 FROM alert_status s WHERE s.code = a.status)
                """));

        assertTrue(constraintDefinition("fk_alerts_status").contains("alert_status"));

        assertEquals(1, count("""
                SELECT count(*)
                FROM alert_status_transition
                WHERE from_code = 'DRAFT' AND to_code = 'ACTIVE'
                """));
        assertEquals(1, count("""
                SELECT count(*)
                FROM alert_status_transition
                WHERE from_code = 'ACTIVE' AND to_code = 'ARCHIVED'
                """));
        assertEquals(0, count("""
                SELECT count(*)
                FROM alert_status_transition
                WHERE from_code = 'ARCHIVED'
                """));

        assertEquals(1, count("""
                SELECT count(*)
                FROM alert_status
                WHERE code = 'ARCHIVED' AND final_state
                """));
        assertEquals(2, count("SELECT count(*) FROM alert_status WHERE editable"));
    }

    @Test
    void migration_shouldAllowOneLiveAlertPerPurposeOfTheSameEvent() throws Exception {
        String insert = """
                INSERT INTO alerts (id, title, status, instructions, origin, created_by, activated_at,
                                    simulation_id, purpose)
                VALUES ('%s', 'Alerta del evento', 'ACTIVE', 'x', 'SIMULATION', 'ms06', now(),
                        '40000000-0000-4000-8000-000000000001', '%s')
                """;
        try {
            execute(insert.formatted("40000000-0000-4000-8000-000000000011", "BRIGADISTS"));
            execute(insert.formatted("40000000-0000-4000-8000-000000000012", "GENERAL"));

            assertThrows(SQLException.class,
                    () -> execute(insert.formatted("40000000-0000-4000-8000-000000000013", "GENERAL")));
        } finally {
            // Las otras pruebas cuentan las filas de esta base compartida
            execute("DELETE FROM alerts WHERE simulation_id = '40000000-0000-4000-8000-000000000001'");
        }
    }

    /**
     * Aplica las migraciones de Flyway hasta la version indicada, o hasta la ultima si es null.
     *
     * @param version version objetivo, null para la ultima
     */
    private static void migrateTo(String version) {
        var configuration = Flyway.configure()
                .dataSource(
                        POSTGRES.getJdbcUrl(),
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword())
                .locations("classpath:db/migration");
        if (version != null) {
            configuration = configuration.target(MigrationVersion.fromVersion(version));
        }
        configuration.load().migrate();
    }

    private static void seedLegacyRows() throws SQLException {
        execute("""
                INSERT INTO alerts (id, type, title, status, instructions, origin,
                                    simulation_id, created_by, activated_at, ended_at)
                VALUES ('%s', 'SIMULATION', 'Simulacro finalizado', 'ENDED',
                        'Evacuar', 'SIMULATION',
                        '30000000-0000-4000-8000-000000000001', 'ms06',
                        now(), now())
                """.formatted(ENDED_SIMULATION));
        execute("""
                INSERT INTO alerts (id, type, title, status, instructions, origin,
                                    simulation_id, created_by, activated_at)
                VALUES ('%s', 'SIMULATION', 'Simulacro activo', 'ACTIVE',
                        'Evacuar', 'SIMULATION',
                        '30000000-0000-4000-8000-000000000002', 'ms06', now())
                """.formatted(ACTIVE_SIMULATION));
        execute("""
                INSERT INTO alerts (id, type, title, status, instructions, origin,
                                    created_by, activated_at, ended_at, archived_at)
                VALUES ('%s', 'OTHER', 'Alerta archivada', 'ARCHIVED',
                        'Atender', 'MANUAL', 'user', now(), now(), now())
                """.formatted(ARCHIVED_MANUAL));
        execute("""
                INSERT INTO alerts (id, type, title, status, instructions, origin,
                                    created_by, activated_at)
                VALUES ('%s', 'EARTHQUAKE', 'Sismo activo', 'ACTIVE',
                        'Evacuar', 'MANUAL', 'user', now())
                """.formatted(ACTIVE_SISMO));
        execute("""
                INSERT INTO alerts (id, type, title, status, instructions, origin,
                                    created_by)
                VALUES ('%s', 'FIRE', 'Incendio en borrador', 'DRAFT',
                        'Revisar', 'MANUAL', 'user')
                """.formatted(DRAFT_INCENDIO));
        execute("""
                INSERT INTO audit_events (id, alert_id, action, actor, from_status, to_status, details)
                VALUES ('40000000-0000-4000-8000-000000000001', '%s', 'END', 'ms06',
                        'ACTIVE', 'ENDED', 'fila historica')
                """.formatted(ENDED_SIMULATION));
    }

    private static void execute(String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static int count(String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private static String typeOf(UUID alertId) throws SQLException {
        return queryString("SELECT type FROM alert_types WHERE alert_id = '%s'".formatted(alertId));
    }

    private static String statusOf(UUID alertId) throws SQLException {
        return queryString("SELECT status FROM alerts WHERE id = '%s'".formatted(alertId));
    }

    private static Timestamp archivedAtOf(UUID alertId) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT archived_at FROM alerts WHERE id = '%s'".formatted(alertId))) {
            resultSet.next();
            return resultSet.getTimestamp(1);
        }
    }

    private static String queryString(String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }

    private static boolean indexExists(String indexName) throws SQLException {
        return count("""
                SELECT count(*)
                FROM pg_indexes
                WHERE schemaname = current_schema()
                  AND indexname = '%s'
                """.formatted(indexName)) == 1;
    }

    private static String constraintDefinition(String constraintName) throws SQLException {
        return queryString("""
                SELECT pg_get_constraintdef(oid)
                FROM pg_constraint
                WHERE conname = '%s'
                """.formatted(constraintName));
    }

    private static int columnLength(String table, String column) throws SQLException {
        return count("""
                SELECT character_maximum_length
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = '%s'
                  AND column_name = '%s'
                """.formatted(table, column));
    }
}
