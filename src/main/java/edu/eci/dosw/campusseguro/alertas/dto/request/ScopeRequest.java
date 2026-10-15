package edu.eci.dosw.campusseguro.alertas.dto.request;

import edu.eci.dosw.campusseguro.alertas.entity.enums.ScopeType;
import edu.eci.dosw.campusseguro.alertas.validation.ValidScope;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@ValidScope
public record ScopeRequest(
        @NotNull(message = "El tipo de alcance es obligatorio") ScopeType tipo,
        List<String> ids) {
}
