package edu.eci.dosw.campusseguro.alertas.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RequesterContextTest {

    @Test
    void has_shouldResolvePermissionFromAnyRole() {
        RequesterContext context = new RequesterContext(
                "user-1",
                Set.of(UserRole.ESTUDIANTE, UserRole.COORDINADOR),
                null);

        assertTrue(context.has(Permission.ALERT_RECEIVE));
        assertTrue(context.has(Permission.ALERT_MANAGE));
        assertTrue(context.has(Permission.ALERT_READ_HISTORY));
        assertFalse(context.has(Permission.ALERT_MANAGE_DRILL));
    }

    @Test
    void isSimulationOnly_shouldBeTrueOnlyForDrillOrganizers() {
        RequesterContext organizer = new RequesterContext(
                "user-1",
                Set.of(UserRole.ORGANIZADOR),
                null);
        RequesterContext coordinator = new RequesterContext(
                "user-2",
                Set.of(UserRole.COORDINADOR),
                null);

        assertTrue(organizer.isSimulationOnly());
        assertFalse(coordinator.isSimulationOnly());
    }

    @Test
    void auditActor_shouldPrefixServiceRequests() {
        RequesterContext service = new RequesterContext(
                "service-user",
                Set.of(UserRole.ORGANIZADOR),
                "MS06");
        RequesterContext user = new RequesterContext(
                "user-1",
                Set.of(UserRole.COORDINADOR),
                "ms03");

        assertTrue(service.isServiceMs06());
        assertEquals("ms06:service-user", service.auditActor());
        assertFalse(user.isServiceMs06());
        assertEquals("user-1", user.auditActor());
    }

    @Test
    void constructor_shouldTreatNullRolesAsEmpty() {
        RequesterContext context = new RequesterContext("user-1", null, null);

        assertTrue(context.roles().isEmpty());
        assertFalse(context.has(Permission.ALERT_RECEIVE));
    }

    @Test
    void requesterIdentity_shouldExposePermissionsAndAuthorities() {
        RequesterIdentity identity = new RequesterIdentity(
                UUID.fromString("11111111-1111-4111-8111-111111111111"),
                Set.of(UserRole.COORDINADOR),
                " MS03 ");

        assertEquals("ms03", identity.serviceName());
        assertTrue(identity.permissions().contains(Permission.ALERT_MANAGE));
        assertEquals(3, identity.authorities().size());
    }
}
