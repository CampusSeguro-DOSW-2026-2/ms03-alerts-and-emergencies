package edu.eci.dosw.campusseguro.alertas.service;

import edu.eci.dosw.campusseguro.alertas.dto.response.ConfirmationResponse;

public record ConfirmationResult(ConfirmationResponse confirmation, boolean created) {
}
