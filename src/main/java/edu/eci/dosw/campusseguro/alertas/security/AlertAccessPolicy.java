package edu.eci.dosw.campusseguro.alertas.security;

import edu.eci.dosw.campusseguro.alertas.entity.AlertEntity;
import edu.eci.dosw.campusseguro.alertas.entity.AlertRecipientEntity;
import edu.eci.dosw.campusseguro.alertas.entity.enums.RecipientType;
import edu.eci.dosw.campusseguro.alertas.exception.NotARecipientException;
import edu.eci.dosw.campusseguro.alertas.repository.AlertRecipientRepository;
import edu.eci.dosw.campusseguro.alertas.repository.NotificationDeliveryRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AlertAccessPolicy {

    private static final Set<String> PRIVILEGED_ROLES = Set.of("COORDINATOR", "AUDITOR", "SERVICE_MS06");

    private final AlertRecipientRepository recipientRepository;
    private final NotificationDeliveryRepository deliveryRepository;

    public AlertAccessPolicy(
            AlertRecipientRepository recipientRepository,
            NotificationDeliveryRepository deliveryRepository) {
        this.recipientRepository = recipientRepository;
        this.deliveryRepository = deliveryRepository;
    }

    public void assertCanView(AlertEntity alert, AccessActor actor) {
        if (actor.roles().stream().anyMatch(PRIVILEGED_ROLES::contains)) {
            return;
        }
        boolean delivered = deliveryRepository.existsByAlertIdAndUserId(alert.getId(), actor.userId());
        if (isRecipient(alert.getId(), actor) || delivered) {
            return;
        }
        throw new NotARecipientException(alert.getId());
    }

    private boolean isRecipient(UUID alertId, AccessActor actor) {
        List<AlertRecipientEntity> recipients = recipientRepository.findByAlertId(alertId);
        return recipients.stream().anyMatch(recipient -> matches(recipient, actor));
    }

    private boolean matches(AlertRecipientEntity recipient, AccessActor actor) {
        if (recipient.getRecipientType() == RecipientType.USER) {
            return recipient.getRecipientRefId().equals(actor.userId());
        }
        return actor.roles().contains(recipient.getRecipientRefId());
    }
}
