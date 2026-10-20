
package edu.eci.dosw.campusseguro.alertas.dto.request;

import edu.eci.dosw.campusseguro.alertas.entity.enums.RecipientType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RecipientRequest(
        @NotNull(message = "Recipient type is required")
        RecipientType type,

        @NotBlank(message = "Recipient ID is required")
        @Size(max = 64, message = "Recipient ID cannot exceed 64 characters")
        String id
) {
}
