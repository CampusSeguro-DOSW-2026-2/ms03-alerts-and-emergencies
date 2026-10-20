package edu.eci.dosw.campusseguro.alertas.service;

import edu.eci.dosw.campusseguro.alertas.dto.response.AlertResponse;

public record AlertCreationResult(AlertResponse alert, boolean created) {
}
