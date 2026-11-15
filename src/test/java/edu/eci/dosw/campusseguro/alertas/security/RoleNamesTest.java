package edu.eci.dosw.campusseguro.alertas.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RoleNamesTest {

    @Test
    void hasStoredFormat_shouldAcceptTheStandardFormat() {
        assertTrue(RoleNames.hasStoredFormat("COORDINADOR"));
        assertTrue(RoleNames.hasStoredFormat("COORDINADOR_ZONA"));
        assertTrue(RoleNames.hasStoredFormat("R1"));
    }

    @Test
    void hasStoredFormat_shouldRejectLowercaseShortAndInvalidNames() {
        assertFalse(RoleNames.hasStoredFormat("coordinador"));
        assertFalse(RoleNames.hasStoredFormat("A"));
        assertFalse(RoleNames.hasStoredFormat("_COORDINADOR"));
        assertFalse(RoleNames.hasStoredFormat("COORDINADOR-ZONA"));
        assertFalse(RoleNames.hasStoredFormat("COORDINADOR ZONA"));
        assertFalse(RoleNames.hasStoredFormat("A".repeat(31)));
        assertFalse(RoleNames.hasStoredFormat(null));
    }

    @Test
    void hasStoredFormat_shouldAcceptAnyRoleCreatedInMs01() {
        assertTrue(RoleNames.hasStoredFormat("DIRECTOR_DE_BIENESTAR"));
        assertTrue(RoleNames.hasStoredFormat("OPERADOR"));
    }

    @Test
    void parseList_shouldSplitTrimAndDeduplicate() {
        Set<String> roles = RoleNames.parseList("ESTUDIANTE, COORDINADOR , ESTUDIANTE");

        assertEquals(Set.of("ESTUDIANTE", "COORDINADOR"), roles);
    }

    @Test
    void parseList_shouldIgnoreEmptyTokens() {
        assertEquals(Set.of("ESTUDIANTE"), RoleNames.parseList(" ,ESTUDIANTE, ,"));
    }

    @Test
    void parseList_shouldAcceptNewRolesWithoutACatalog() {
        assertEquals(Set.of("DIRECTOR"), RoleNames.parseList("DIRECTOR"));
    }

    @Test
    void parseList_shouldReturnEmpty_whenValueIsNullOrBlank() {
        assertTrue(RoleNames.parseList(null).isEmpty());
        assertTrue(RoleNames.parseList("   ").isEmpty());
    }

    @Test
    void parseList_shouldRejectMalformedName() {
        assertThrows(
                InvalidIdentityHeadersException.class,
                () -> RoleNames.parseList("ESTUDIANTE, desconocido"));
    }

    @Test
    void sorted_shouldOrderNamesAlphabetically() {
        assertEquals(
                List.of("AUDITOR", "COORDINADOR", "ESTUDIANTE"),
                RoleNames.sorted(Set.of("ESTUDIANTE", "AUDITOR", "COORDINADOR")));
    }
}
