package edu.eci.dosw.campusseguro.alertas.dto.request;

import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertType;
import edu.eci.dosw.campusseguro.alertas.validation.AtLeastOneField;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

@AtLeastOneField
public record AlertUpdateRequest(
        AlertType tipo,
        @Valid ScopeRequest alcance,
        @Size(max = 1000, message = "Las instrucciones no pueden superar 1000 caracteres")
        @Pattern(regexp = ".*\\S.*", flags = Pattern.Flag.DOTALL, message = "Las instrucciones no pueden estar vacías") String instrucciones,
        @Size(min = 1, message = "Debe indicar al menos un destinatario")
        List<@NotNull(message = "El destinatario no puede ser nulo") @Valid RecipientRequest> destinatarios) {

    public boolean hasAnyField() {
        return tipo != null || alcance != null || instrucciones != null || destinatarios != null;
    }
}
