package edu.eci.dosw.campusseguro.alertas.dto.request;

import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertOrigin;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AlertOriginRequest(
        @NotNull(message = "Origin type is required")
        AlertOrigin type,

        UUID eventId) {
}