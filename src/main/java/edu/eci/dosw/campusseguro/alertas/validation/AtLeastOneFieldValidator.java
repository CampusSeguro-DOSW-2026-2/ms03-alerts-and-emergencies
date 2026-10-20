
package edu.eci.dosw.campusseguro.alertas.validation;

import edu.eci.dosw.campusseguro.alertas.dto.request.AlertUpdateRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class AtLeastOneFieldValidator
        implements ConstraintValidator<AtLeastOneField, AlertUpdateRequest> {

    @Override
    public boolean isValid(
            AlertUpdateRequest value,
            ConstraintValidatorContext context) {

        if (value == null || value.hasAnyField()) {
            return true;
        }

        context.disableDefaultConstraintViolation();

        context.buildConstraintViolationWithTemplate(
                        context.getDefaultConstraintMessageTemplate())
                .addConstraintViolation();

        return false;
    }
}
