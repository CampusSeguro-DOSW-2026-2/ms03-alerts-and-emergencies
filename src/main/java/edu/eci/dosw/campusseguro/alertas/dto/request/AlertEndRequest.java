package edu.eci.dosw.campusseguro.alertas.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.UUID;

public record AlertEndRequest(
        @JsonAlias("eventId")
        UUID simulationId) {
}