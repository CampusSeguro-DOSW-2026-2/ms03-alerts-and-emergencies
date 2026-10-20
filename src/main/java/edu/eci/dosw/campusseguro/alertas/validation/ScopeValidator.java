
package edu.eci.dosw.campusseguro.alertas.validation;

import edu.eci.dosw.campusseguro.alertas.dto.request.ScopeRequest;
import edu.eci.dosw.campusseguro.alertas.entity.enums.ScopeType;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.List;

public class ScopeValidator
        implements ConstraintValidator<ValidScope, ScopeRequest> {

    private static final int MAX_ID_LENGTH = 64;

    @Override
    public boolean isValid(
            ScopeRequest value,
            ConstraintValidatorContext context) {

        if (value == null
                || value.type() == null
                || value.type() == ScopeType.CAMPUS) {
            return true;
        }

        List<String> ids = value.ids();

        if (ids == null || ids.isEmpty()) {
            return reject(
                    context,
                    "At least one identifier must be provided for the selected scope");
        }

        boolean hasInvalidId = ids.stream()
                .anyMatch(id -> id == null
                        || id.isBlank()
                        || id.strip().length() > MAX_ID_LENGTH);

        if (hasInvalidId) {
            return reject(
                    context,
                    "Scope identifiers must not be blank or exceed 64 characters");
        }

        return true;
    }

    private boolean reject(
            ConstraintValidatorContext context,
            String message) {

        context.disableDefaultConstraintViolation();

        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode("ids")
                .addConstraintViolation();

        return false;
    }
}
