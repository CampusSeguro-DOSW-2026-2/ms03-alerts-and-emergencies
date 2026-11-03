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
        @NotEmpty(message = "At least one alert type is required")
        List<
                @NotNull(message = "Alert type cannot be null")
                AlertType
        > types,

        @NotBlank(message = "Title is required")
        @Size(max = 150, message = "Title cannot exceed 150 characters")
        String title,

        @NotNull(message = "Alert scope is required")
        @Valid
        ScopeRequest scope,

        @NotBlank(message = "Instructions are required")
        @Size(max = 1000, message = "Instructions cannot exceed 1000 characters")
        String instructions,

        @NotEmpty(message = "At least one recipient is required")
        List<
                @NotNull(message = "Recipient cannot be null")
                @Valid RecipientRequest
        > recipients,

        @Valid
        AlertOriginRequest origin,

        Instant activationAt,

        Boolean activateImmediately
) {
}
