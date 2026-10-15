package edu.eci.dosw.campusseguro.alertas.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ScopeValidator.class)
public @interface ValidScope {

    String message() default "El alcance no es válido";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
