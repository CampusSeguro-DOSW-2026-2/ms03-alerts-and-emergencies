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

        Boolean activateImmediately,

        /**
         * Alternative to {@link #scope} for alerts that cover more than one kind of scope
         * (for example buildings and zones in the same event). Exactly one of
         * {@code scope} or {@code scopes} must be present.
         */
        @Valid
        List<ScopeRequest> scopes,

        /**
         * Purpose of the alert inside its simulation/event (for example BRIGADISTS,
         * GENERAL, EDIT or FINISH). It lets several live alerts share the same
         * {@code simulationId}.
         */
        @Size(max = 32, message = "Purpose cannot exceed 32 characters")
        String purpose
) {

    /** Keeps the previous constructor for callers that do not use scopes or purpose. */
    public AlertCreateRequest(
            List<AlertType> types,
            String title,
            ScopeRequest scope,
            String instructions,
            List<RecipientRequest> recipients,
            AlertOriginRequest origin,
            Instant activationAt,
            Boolean activateImmediately) {
        this(types, title, scope, instructions, recipients, origin, activationAt, activateImmediately, null, null);
    }
}
