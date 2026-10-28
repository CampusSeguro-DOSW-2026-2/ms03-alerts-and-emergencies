package edu.eci.dosw.campusseguro.alertas.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UserRoleTest {

    @Test
    void from_shouldAcceptOfficialRole() {
        assertEquals(Optional.of(UserRole.COORDINADOR), UserRole.from("COORDINADOR"));
    }

    @Test
    void from_shouldIgnoreCaseAndRolePrefix() {
        assertEquals(Optional.of(UserRole.ORGANIZADOR), UserRole.from("ROLE_organizador"));
        assertEquals(Optional.of(UserRole.ESTUDIANTE), UserRole.from(" estudiante "));
    }

    @Test
    void from_shouldReturnEmpty_whenValueIsMissingOrUnknown() {
        assertTrue(UserRole.from(null).isEmpty());
        assertTrue(UserRole.from(" ").isEmpty());
        assertTrue(UserRole.from("COORDINATOR").isEmpty());
    }

    @Test
    void parseList_shouldParseCommaSeparatedRoles() {
        Set<UserRole> roles = UserRole.parseList("ESTUDIANTE, ROLE_AUDITOR");

        assertEquals(Set.of(UserRole.ESTUDIANTE, UserRole.AUDITOR), roles);
    }

    @Test
    void parseList_shouldReturnEmpty_whenValueIsMissing() {
        assertTrue(UserRole.parseList(null).isEmpty());
        assertTrue(UserRole.parseList(" ").isEmpty());
    }

    @Test
    void parseList_shouldRejectUnknownRole() {
        org.junit.jupiter.api.Assertions.assertThrows(
                InvalidIdentityHeadersException.class,
                () -> UserRole.parseList("ESTUDIANTE, DESCONOCIDO"));
    }

    @Test
    void permissions_shouldMatchTheOfficialRoleMatrix() {
        assertEquals(
                Set.of(Permission.ALERT_RECEIVE),
                UserRole.ESTUDIANTE.permissions());
        assertEquals(
                Set.of(
                        Permission.ALERT_RECEIVE,
                        Permission.ALERT_MANAGE,
                        Permission.ALERT_READ_HISTORY),
                UserRole.COORDINADOR.permissions());
        assertEquals(
                Set.of(
                        Permission.ALERT_RECEIVE,
                        Permission.ALERT_MANAGE_DRILL,
                        Permission.ALERT_READ_HISTORY),
                UserRole.ORGANIZADOR.permissions());
        assertEquals(Set.of(Permission.ALERT_READ_HISTORY), UserRole.AUDITOR.permissions());
        assertTrue(UserRole.ADMIN.permissions().isEmpty());
    }

    @Test
    void permissions_shouldNotGrantManagementToStudents() {
        assertFalse(UserRole.ESTUDIANTE.permissions().contains(Permission.ALERT_MANAGE));
        assertFalse(UserRole.PROFESOR.permissions().contains(Permission.ALERT_MANAGE_DRILL));
    }
}
