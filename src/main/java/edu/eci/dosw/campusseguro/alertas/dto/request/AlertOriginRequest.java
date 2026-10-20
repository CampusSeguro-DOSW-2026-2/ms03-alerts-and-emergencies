package edu.eci.dosw.campusseguro.alertas.dto.request;

import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertOrigin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AlertOriginRequest(
        @NotNull(message = "El tipo de origen es obligatorio") AlertOrigin tipo,
        @Size(max = 64, message = "El ID del simulacro no puede superar 64 caracteres") String simulationId) {
}
