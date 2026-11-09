package edu.eci.dosw.campusseguro.alertas.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

class AlertMigrationIT {

    private static final UUID ENDED_SIMULATION =
            UUID.fromString("10000000-0000-4000-8000-000000000001");

    private static final UUID ACTIVE_SIMULATION =
            UUID.fromString("10000000-0000-4000-8000-000000000002");

    private static final UUID ARCHIVED_MANUAL =
            UUID.fromString("10000000-0000-4000-8000-000000000003");

    private static final UUID ACTIVE_EARTHQUAKE =
            UUID.fromString("10000000-0000-4000-8000-000000000004");

    private static final UUID DRAFT_FIRE =
            UUID.fromString("10000000-0000-4000-8000-000000000005");

    private static final List<String> LEGACY_SCRIPTS = List.of(
            "001_create_schema.sql",
            "002_indexes.sql",
            "003_phase2_contract.sql",
            "004_align_simulation_contract.sql");

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
        for (String script : LEGACY_SCRIPTS) {
            applyScript(script);
        }
        seedLegacyRows();
        applyScript("005_business_logic_update.sql");
        applyScript("007_alert_purpose.sql");
        applyScript("008_alert_purpose_unique.sql");
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

        assertEquals("OTHER", typeOf(ENDED_SIMULATION));
        assertEquals("OTHER", typeOf(ACTIVE_SIMULATION));
        assertEquals("OTHER", typeOf(ARCHIVED_MANUAL));
        assertEquals("EARTHQUAKE", typeOf(ACTIVE_EARTHQUAKE));
        assertEquals("FIRE", typeOf(DRAFT_FIRE));

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

        assertTrue(constraintDefinition("ck_alerts_status").contains("ARCHIVED"));
        assertTrue(constraintDefinition("ck_alerts_status").contains("DRAFT"));
        assertTrue(constraintDefinition("ck_audit_action").contains("END"));
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

    private static void applyScript(String script) throws Exception {
        Path path = Path.of("database", script);
        String containerPath = "/tmp/" + script;
        POSTGRES.copyFileToContainer(MountableFile.forHostPath(path), containerPath);
        var result = POSTGRES.execInContainer(
                "psql",
                "-U",
                POSTGRES.getUsername(),
                "-d",
                POSTGRES.getDatabaseName(),
                "-v",
                "ON_ERROR_STOP=1",
                "-f",
                containerPath);
        assertEquals(0, result.getExitCode(), result.getStderr() + result.getStdout());
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
                """.formatted(ACTIVE_EARTHQUAKE));
        execute("""
                INSERT INTO alerts (id, type, title, status, instructions, origin,
                                    created_by)
                VALUES ('%s', 'FIRE', 'Incendio en borrador', 'DRAFT',
                        'Revisar', 'MANUAL', 'user')
                """.formatted(DRAFT_FIRE));
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
}
