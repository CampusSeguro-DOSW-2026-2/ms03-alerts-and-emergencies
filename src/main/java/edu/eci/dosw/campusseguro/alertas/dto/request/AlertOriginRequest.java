package edu.eci.dosw.campusseguro.alertas.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertOrigin;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AlertOriginRequest(
        @NotNull(message = "Origin type is required")
        @JsonAlias("tipo")
        AlertOrigin type,

        @JsonAlias("eventId")
        UUID simulationId) {
}