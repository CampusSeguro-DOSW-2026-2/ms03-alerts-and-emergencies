package edu.eci.dosw.campusseguro.alertas.validation;

import edu.eci.dosw.campusseguro.alertas.dto.request.ScopeRequest;
import edu.eci.dosw.campusseguro.alertas.entity.enums.ScopeType;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.List;

public class ScopeValidator implements ConstraintValidator<ValidScope, ScopeRequest> {

    private static final int MAX_ID_LENGTH = 64;

    @Override
    public boolean isValid(ScopeRequest value, ConstraintValidatorContext context) {
        if (value == null || value.tipo() == null || value.tipo() == ScopeType.CAMPUS) {
            return true;
        }
        List<String> ids = value.ids();
        if (ids == null || ids.isEmpty()) {
            return reject(context, "Debe indicar al menos un identificador para el alcance seleccionado");
        }
        boolean hasInvalidId = ids.stream()
                .anyMatch(id -> id == null || id.isBlank() || id.strip().length() > MAX_ID_LENGTH);
        if (hasInvalidId) {
            return reject(context, "Los identificadores del alcance no pueden estar vacíos ni superar 64 caracteres");
        }
        return true;
    }

    private boolean reject(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode("ids")
                .addConstraintViolation();
        return false;
    }
}
