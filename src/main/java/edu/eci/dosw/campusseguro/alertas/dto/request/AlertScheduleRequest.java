
package edu.eci.dosw.campusseguro.alertas.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record AlertScheduleRequest(
        @NotNull(message = "La fecha de activación es obligatoria")
        Instant activationAt) {
}
