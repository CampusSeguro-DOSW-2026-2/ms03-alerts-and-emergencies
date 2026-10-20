package edu.eci.dosw.campusseguro.alertas.service.impl;

import edu.eci.dosw.campusseguro.alertas.dto.response.ConfirmationResponse;
import edu.eci.dosw.campusseguro.alertas.entity.AlertConfirmationEntity;
import edu.eci.dosw.campusseguro.alertas.entity.AlertEntity;
import edu.eci.dosw.campusseguro.alertas.entity.AlertRecipientEntity;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AuditAction;
import edu.eci.dosw.campusseguro.alertas.exception.AlertNotActiveException;
import edu.eci.dosw.campusseguro.alertas.exception.AlertNotFoundException;
import edu.eci.dosw.campusseguro.alertas.mapper.ConfirmationMapper;
import edu.eci.dosw.campusseguro.alertas.repository.AlertConfirmationRepository;
import edu.eci.dosw.campusseguro.alertas.repository.AlertRecipientRepository;
import edu.eci.dosw.campusseguro.alertas.repository.AlertRepository;
import edu.eci.dosw.campusseguro.alertas.security.AccessActor;
import edu.eci.dosw.campusseguro.alertas.security.AlertAccessPolicy;
import edu.eci.dosw.campusseguro.alertas.service.AuditService;
import edu.eci.dosw.campusseguro.alertas.service.ConfirmationResult;
import edu.eci.dosw.campusseguro.alertas.service.ConfirmationService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConfirmationServiceImpl implements ConfirmationService {

    private final AlertRepository alertRepository;
    private final AlertRecipientRepository recipientRepository;
    private final AlertConfirmationRepository confirmationRepository;
    private final AlertAccessPolicy accessPolicy;
    private final AuditService auditService;
    private final ConfirmationMapper confirmationMapper;
    private final Clock clock;

    public ConfirmationServiceImpl(
            AlertRepository alertRepository,
            AlertRecipientRepository recipientRepository,
            AlertConfirmationRepository confirmationRepository,
            AlertAccessPolicy accessPolicy,
            AuditService auditService,
            ConfirmationMapper confirmationMapper,
            Clock clock) {
        this.alertRepository = alertRepository;
        this.recipientRepository = recipientRepository;
        this.confirmationRepository = confirmationRepository;
        this.accessPolicy = accessPolicy;
        this.auditService = auditService;
        this.confirmationMapper = confirmationMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ConfirmationResult confirm(UUID alertId, AccessActor actor) {
        AlertEntity alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new AlertNotFoundException(alertId));

        if (alert.getStatus() != AlertStatus.ACTIVE) {
            throw new AlertNotActiveException(alertId);
        }

        List<AlertRecipientEntity> recipients = recipientRepository.findByAlertId(alertId);
        accessPolicy.assertCanConfirm(alertId, actor, recipients);

        return confirmationRepository.findByAlertIdAndUserId(alertId, actor.userId())
                .map(existing -> new ConfirmationResult(confirmationMapper.toResponse(existing), false))
                .orElseGet(() -> createConfirmation(alertId, actor));
    }

    private ConfirmationResult createConfirmation(UUID alertId, AccessActor actor) {
        Instant now = clock.instant();
        int inserted = confirmationRepository.insertIfAbsent(alertId, actor.userId(), now);

        AlertConfirmationEntity confirmation = confirmationRepository
                .findByAlertIdAndUserId(alertId, actor.userId())
                .orElseThrow(() -> new IllegalStateException("No se pudo guardar la confirmación"));

        if (inserted > 0) {
            auditService.record(
                    alertId,
                    AuditAction.CONFIRM,
                    actor.userId(),
                    AlertStatus.ACTIVE,
                    AlertStatus.ACTIVE,
                    "Recepción confirmada por " + actor.userId());
        }

        ConfirmationResponse response = confirmationMapper.toResponse(confirmation);
        return new ConfirmationResult(response, inserted > 0);
    }
}
