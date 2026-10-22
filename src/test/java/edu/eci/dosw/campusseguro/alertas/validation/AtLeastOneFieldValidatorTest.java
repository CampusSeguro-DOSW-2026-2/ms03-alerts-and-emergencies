package edu.eci.dosw.campusseguro.alertas.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

import edu.eci.dosw.campusseguro.alertas.dto.request.AlertUpdateRequest;
import edu.eci.dosw.campusseguro.alertas.dto.request.RecipientRequest;
import edu.eci.dosw.campusseguro.alertas.dto.request.ScopeRequest;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertType;
import edu.eci.dosw.campusseguro.alertas.entity.enums.RecipientType;
import edu.eci.dosw.campusseguro.alertas.entity.enums.ScopeType;
import jakarta.validation.ConstraintValidatorContext;
import java.util.List;
import org.junit.jupiter.api.Test;

class AtLeastOneFieldValidatorTest {

    private final AtLeastOneFieldValidator validator = new AtLeastOneFieldValidator();
    private final ConstraintValidatorContext context =
            mock(ConstraintValidatorContext.class, RETURNS_DEEP_STUBS);

    @Test
    void shouldAcceptNullRequest() {
        assertTrue(validator.isValid(null, context));
    }

    @Test
    void shouldRejectRequestWithoutAnyField() {
        assertFalse(validator.isValid(new AlertUpdateRequest(null, null, null, null, null), context));
    }

    @Test
    void shouldAcceptRequestWithAtLeastOneField() {
        assertTrue(validator.isValid(new AlertUpdateRequest(AlertType.FIRE, null, null, null, null), context));
        assertTrue(validator.isValid(
                new AlertUpdateRequest(null, null, new ScopeRequest(ScopeType.CAMPUS, null), null, null), context));
        assertTrue(validator.isValid(new AlertUpdateRequest(null, null, null, "Evacuar", null), context));
        assertTrue(validator.isValid(new AlertUpdateRequest(null, null, null, null,
                List.of(new RecipientRequest(RecipientType.USER, "usr-001"))), context));
    }
}
