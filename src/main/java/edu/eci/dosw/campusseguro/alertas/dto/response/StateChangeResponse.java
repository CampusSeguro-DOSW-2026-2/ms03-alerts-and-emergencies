
package edu.eci.dosw.campusseguro.alertas.dto.response;

import java.time.Instant;
import java.util.UUID;

public record StateChangeResponse(
        UUID id,
        String status,
        Instant activationAt
) {
}
