package edu.eci.dosw.campusseguro.alertas.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import edu.eci.dosw.campusseguro.alertas.dto.response.ConfirmationResponse;
import edu.eci.dosw.campusseguro.alertas.entity.AlertConfirmationEntity;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ConfirmationMapperTest {

    private final ConfirmationMapper mapper = new ConfirmationMapper();

    @Test
    void toResponse_shouldMapAllFields() {
        UUID id = UUID.randomUUID();
        UUID alertId = UUID.randomUUID();
        Instant confirmedAt = Instant.parse("2026-10-15T18:05:00Z");
        AlertConfirmationEntity confirmation = new AlertConfirmationEntity();
        confirmation.setAlertId(alertId);
        confirmation.setUserId("usr-001");
        confirmation.setConfirmedAt(confirmedAt);
        ReflectionTestUtils.setField(confirmation, "id", id);

        ConfirmationResponse response = mapper.toResponse(confirmation);

        assertEquals(id, response.id());
        assertEquals(alertId, response.idAlerta());
        assertEquals("usr-001", response.usuarioId());
        assertEquals(confirmedAt, response.fechaConfirmacion());
    }
}
