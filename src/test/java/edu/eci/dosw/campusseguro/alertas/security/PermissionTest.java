package edu.eci.dosw.campusseguro.alertas.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class PermissionTest {

    @Test
    void hasFormat_shouldAcceptTheStandardFormat() {
        assertTrue(Permission.hasFormat("ms03:alert:manage"));
        assertTrue(Permission.hasFormat("ms01:role:read_all"));
        assertTrue(Permission.hasFormat("ms02:safe_zone:write"));
    }

    @Test
    void hasFormat_shouldRejectMalformedCodes() {
        assertFalse(Permission.hasFormat("ms3:alert:manage"));
        assertFalse(Permission.hasFormat("MS03:alert:manage"));
        assertFalse(Permission.hasFormat("ms03:Alert:manage"));
        assertFalse(Permission.hasFormat("ms03:alert"));
        assertFalse(Permission.hasFormat("alert:manage"));
        assertFalse(Permission.hasFormat(""));
        assertFalse(Permission.hasFormat(null));
    }

    @Test
    void parseList_shouldSplitTrimSortAndDeduplicate() {
        Set<String> codes = Permission.parseList(
                "ms03:alert:manage, ms03:alert:receive ,ms03:alert:manage");

        assertEquals(Set.of("ms03:alert:manage", "ms03:alert:receive"), codes);
    }

    @Test
    void parseList_shouldKeepPermissionsOfOtherModules() {
        Set<String> codes = Permission.parseList("ms02:campus:read,ms03:alert:receive");

        assertTrue(codes.contains("ms02:campus:read"));
        assertEquals(2, codes.size());
    }

    @Test
    void parseList_shouldReturnEmpty_whenValueIsNullOrBlank() {
        assertTrue(Permission.parseList(null).isEmpty());
        assertTrue(Permission.parseList("  ").isEmpty());
    }

    @Test
    void parseList_shouldRejectMalformedCode() {
        assertThrows(
                InvalidIdentityHeadersException.class,
                () -> Permission.parseList("ms03:alert:receive,ms03-alert-manage"));
    }

    @Test
    void authority_shouldMatchTheOfficialCatalog() {
        assertEquals("ms03:alert:manage", Permission.ALERT_MANAGE.authority());
        assertEquals("ms03:alert:manage_drill", Permission.ALERT_MANAGE_DRILL.authority());
        assertEquals("ms03:alert:read_history", Permission.ALERT_READ_HISTORY.authority());
        assertEquals("ms03:alert:receive", Permission.ALERT_RECEIVE.authority());
    }
}
