package edu.eci.dosw.campusseguro.alertas.dto.request;

import edu.eci.dosw.campusseguro.alertas.entity.enums.RecipientType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RecipientRequest(
        @NotNull(message = "El tipo de destinatario es obligatorio") RecipientType tipo,
        @NotBlank(message = "El identificador del destinatario es obligatorio")
        @Size(max = 64, message = "El identificador del destinatario no puede superar 64 caracteres") String id) {
}
