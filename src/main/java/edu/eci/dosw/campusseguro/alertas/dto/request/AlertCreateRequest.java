package edu.eci.dosw.campusseguro.alertas.dto.request;

import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public record AlertCreateRequest(
        @NotNull(message = "El tipo de alerta es obligatorio") AlertType tipo,
        @NotNull(message = "El alcance es obligatorio") @Valid ScopeRequest alcance,
        @NotBlank(message = "Las instrucciones son obligatorias")
        @Size(max = 1000, message = "Las instrucciones no pueden superar 1000 caracteres") String instrucciones,
        @NotEmpty(message = "Debe indicar al menos un destinatario")
        List<@NotNull(message = "El destinatario no puede ser nulo") @Valid RecipientRequest> destinatarios,
        @Valid AlertOriginRequest origen,
        Instant activationAt,
        Boolean activarInmediatamente) {

    public AlertCreateRequest(
            AlertType tipo, ScopeRequest alcance, String instrucciones, List<RecipientRequest> destinatarios) {
        this(tipo, alcance, instrucciones, destinatarios, null, null, null);
    }
}
