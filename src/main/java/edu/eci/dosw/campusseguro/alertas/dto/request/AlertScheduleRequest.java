
package edu.eci.dosw.campusseguro.alertas.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record AlertScheduleRequest(
        @NotNull(message = "The activation date is mandatory.")
        Instant activationAt) {
}
