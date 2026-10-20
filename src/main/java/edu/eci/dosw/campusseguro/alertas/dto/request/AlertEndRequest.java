package edu.eci.dosw.campusseguro.alertas.dto.request;

import jakarta.validation.constraints.Size;

public record AlertEndRequest(
        @Size(max = 64, message = "El ID del simulacro no puede superar 64 caracteres") String simulationId) {
}
