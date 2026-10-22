package edu.eci.dosw.campusseguro.alertas.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RoleTest {

    @Test
    void fromClaim_shouldNormalizeCaseWhitespaceAndPrefix() {
        assertEquals(Role.COORDINATOR, Role.fromClaim("coordinator").orElseThrow());
        assertEquals(Role.AUDITOR, Role.fromClaim(" ROLE_AUDITOR ").orElseThrow());
        assertEquals(Role.SERVICE_MS06, Role.fromClaim("SERVICE_MS06").orElseThrow());
    }

    @Test
    void fromClaim_shouldReturnEmptyForNullOrUnknown() {
        assertTrue(Role.fromClaim(null).isEmpty());
        assertTrue(Role.fromClaim("RECIPIENT").isEmpty());
    }

    @Test
    void authority_shouldUseRolePrefix() {
        assertEquals("ROLE_COORDINATOR", Role.COORDINATOR.authority());
    }
}
