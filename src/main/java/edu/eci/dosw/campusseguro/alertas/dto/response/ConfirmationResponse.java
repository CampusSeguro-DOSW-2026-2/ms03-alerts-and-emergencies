package edu.eci.dosw.campusseguro.alertas.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ConfirmationResponse(UUID id, UUID idAlerta, String usuarioId, Instant fechaConfirmacion) {
}
