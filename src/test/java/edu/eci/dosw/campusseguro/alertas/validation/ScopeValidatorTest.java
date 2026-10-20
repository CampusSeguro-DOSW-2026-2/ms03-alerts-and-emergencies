package edu.eci.dosw.campusseguro.alertas.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

import edu.eci.dosw.campusseguro.alertas.dto.request.ScopeRequest;
import edu.eci.dosw.campusseguro.alertas.entity.enums.ScopeType;
import jakarta.validation.ConstraintValidatorContext;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScopeValidatorTest {

    private final ScopeValidator validator = new ScopeValidator();
    private final ConstraintValidatorContext context =
            mock(ConstraintValidatorContext.class, RETURNS_DEEP_STUBS);

    @Test
    void shouldAcceptNullAndCampusScopes() {
        assertTrue(validator.isValid(null, context));
        assertTrue(validator.isValid(new ScopeRequest(null, List.of()), context));
        assertTrue(validator.isValid(new ScopeRequest(ScopeType.CAMPUS, null), context));
    }

    @Test
    void shouldRequireIdsForBuildingAndZoneScopes() {
        assertFalse(validator.isValid(new ScopeRequest(ScopeType.BUILDING, null), context));
        assertFalse(validator.isValid(new ScopeRequest(ScopeType.ZONE, List.of()), context));
    }

    @Test
    void shouldAcceptValidIds() {
        assertTrue(validator.isValid(
                new ScopeRequest(ScopeType.BUILDING, List.of("BLD-001", "BLD-002")), context));
    }

    @Test
    void shouldRejectBlankOrTooLongIds() {
        assertFalse(validator.isValid(new ScopeRequest(ScopeType.ZONE, List.of(" ")), context));
        assertFalse(validator.isValid(
                new ScopeRequest(ScopeType.ZONE, List.of("x".repeat(65))), context));
        assertFalse(validator.isValid(
                new ScopeRequest(ScopeType.ZONE, java.util.Arrays.asList((String) null)), context));
    }
}
