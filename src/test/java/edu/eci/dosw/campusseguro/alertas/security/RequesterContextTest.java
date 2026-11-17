package edu.eci.dosw.campusseguro.alertas.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RequesterContextTest {

    private static final String USER_ID = "11111111-1111-4111-8111-111111111111";

    @Test
    void has_shouldResolvePermissionsFromTheHeader() {
        RequesterContext context = new RequesterContext(
                "user-1",
                Set.of("ESTUDIANTE", "COORDINADOR"),
                Identities.COORDINADOR,
                null);

        assertTrue(context.has(Permission.ALERT_RECEIVE));
        assertTrue(context.has(Permission.ALERT_MANAGE));
        assertTrue(context.has(Permission.ALERT_READ_HISTORY));
        assertFalse(context.has(Permission.ALERT_MANAGE_DRILL));
    }

    @Test
    void has_shouldIgnoreRoleNames_whenPermissionsDoNotGrantIt() {
        RequesterContext context = new RequesterContext(
                "user-1",
                Set.of("COORDINADOR", "ADMIN"),
                Identities.RECEPTOR,
                null);

        assertTrue(context.has(Permission.ALERT_RECEIVE));
        assertFalse(context.has(Permission.ALERT_MANAGE));
        assertFalse(context.has(Permission.ALERT_READ_HISTORY));
    }

    @Test
    void has_shouldGrantPermissionToAnyRoleThatReceivedIt() {
        RequesterContext context = new RequesterContext(
                "user-1",
                Set.of("DIRECTOR_DE_BIENESTAR"),
                Set.of("ms03:alert:manage"),
                null);

        assertTrue(context.has(Permission.ALERT_MANAGE));
        assertFalse(context.isSimulationOnly());
    }

    @Test
    void has_shouldBeFalse_whenPermissionIsNull() {
        RequesterContext context = new RequesterContext("user-1", Set.of(), Set.of(), null);

        assertFalse(context.has(null));
    }

    @Test
    void isSimulationOnly_shouldDependOnThePermissions() {
        RequesterContext organizer = new RequesterContext(
                "user-1",
                Set.of("ORGANIZADOR"),
                Identities.ORGANIZADOR,
                null);
        RequesterContext coordinator = new RequesterContext(
                "user-2",
                Set.of("COORDINADOR"),
                Identities.COORDINADOR,
                null);
        RequesterContext receiver = new RequesterContext(
                "user-3",
                Set.of("ESTUDIANTE"),
                Identities.RECEPTOR,
                null);

        assertTrue(organizer.isSimulationOnly());
        assertFalse(coordinator.isSimulationOnly());
        assertFalse(receiver.isSimulationOnly());
    }

    @Test
    void auditActor_shouldPrefixServiceRequests() {
        RequesterContext service = new RequesterContext(
                "service-user",
                Set.of("ORGANIZADOR"),
                Identities.ORGANIZADOR,
                "MS07");
        RequesterContext user = new RequesterContext(
                "user-1",
                Set.of("COORDINADOR"),
                Identities.COORDINADOR,
                null);

        assertEquals("MS07:service-user", service.auditActor());
        assertEquals("user-1", user.auditActor());
    }

    @Test
    void constructor_shouldTreatNullRolesAndPermissionsAsEmpty() {
        RequesterContext context = new RequesterContext("user-1", null, null, null);

        assertTrue(context.roles().isEmpty());
        assertTrue(context.permissions().isEmpty());
        assertFalse(context.has(Permission.ALERT_RECEIVE));
    }

    @Test
    void csv_shouldExposeRolesAndPermissionsSortedForPropagation() {
        RequesterContext context = new RequesterContext(
                "user-1",
                Set.of("PROFESOR", "COORDINADOR"),
                Identities.COORDINADOR,
                null);

        assertEquals("COORDINADOR,PROFESOR", context.rolesCsv());
        assertEquals(
                "ms03:alert:manage,ms03:alert:read_history,ms03:alert:receive",
                context.permissionsCsv());
    }

    @Test
    void requesterIdentity_shouldExposePermissionsAsAuthorities() {
        RequesterIdentity identity = Identities.identity(USER_ID, " MS03 ");

        assertEquals("ms03", identity.serviceName());
        assertTrue(identity.permissions().contains("ms03:alert:manage"));
        assertEquals(3, identity.authorities().size());
        assertEquals(
                List.of("ms03:alert:manage", "ms03:alert:read_history", "ms03:alert:receive"),
                identity.authorities().stream()
                        .map(authority -> authority.getAuthority())
                        .toList());
    }

    @Test
    void callerIdentity_shouldCarryTheThreeHeadersFromTheContext() {
        RequesterContext context = new RequesterContext(
                UUID.randomUUID().toString(),
                Set.of("COORDINADOR"),
                Identities.COORDINADOR,
                null);

        CallerIdentity caller = CallerIdentity.from(context);

        assertTrue(caller.isPresent());
        assertEquals(context.actor(), caller.userId());
        assertEquals("COORDINADOR", caller.rolesCsv());
        assertEquals(
                "ms03:alert:manage,ms03:alert:read_history,ms03:alert:receive",
                caller.permissionsCsv());
    }

    @Test
    void callerIdentity_shouldBeAbsent_whenThereIsNoRequester() {
        CallerIdentity caller = CallerIdentity.from(null);

        assertFalse(caller.isPresent());
    }
}
