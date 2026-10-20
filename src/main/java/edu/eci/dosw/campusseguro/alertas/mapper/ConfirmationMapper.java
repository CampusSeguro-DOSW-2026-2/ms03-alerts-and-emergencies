package edu.eci.dosw.campusseguro.alertas.mapper;

import edu.eci.dosw.campusseguro.alertas.dto.response.ConfirmationResponse;
import edu.eci.dosw.campusseguro.alertas.entity.AlertConfirmationEntity;
import org.springframework.stereotype.Component;

@Component
public class ConfirmationMapper {

    public ConfirmationResponse toResponse(AlertConfirmationEntity confirmation) {
        return new ConfirmationResponse(
                confirmation.getId(),
                confirmation.getAlertId(),
                confirmation.getUserId(),
                confirmation.getConfirmedAt());
    }
}
