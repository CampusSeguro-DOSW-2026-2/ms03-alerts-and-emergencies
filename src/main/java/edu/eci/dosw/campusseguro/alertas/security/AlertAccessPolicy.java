package edu.eci.dosw.campusseguro.alertas.security;

import edu.eci.dosw.campusseguro.alertas.entity.AlertEntity;
import edu.eci.dosw.campusseguro.alertas.entity.AlertRecipientEntity;
import edu.eci.dosw.campusseguro.alertas.entity.enums.RecipientType;
import edu.eci.dosw.campusseguro.alertas.exception.NotARecipientException;
import edu.eci.dosw.campusseguro.alertas.repository.AlertRecipientRepository;
import edu.eci.dosw.campusseguro.alertas.repository.NotificationDeliveryRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Política de acceso a una alerta concreta.
 *
 * <p>Quien tiene el permiso de consultar el historial puede ver cualquier alerta, sin importar con qué
 * rol lo obtuvo. Sin ese permiso solo la ve si es destinatario directo o si la alerta se le entregó, y
 * esa comprobación sí compara nombres de rol porque es lo que identifica a un destinatario dirigido a
 * un rol.
 */
@Component
public class AlertAccessPolicy {

    private final AlertRecipientRepository recipientRepository;
    private final NotificationDeliveryRepository deliveryRepository;

    public AlertAccessPolicy(
            AlertRecipientRepository recipientRepository,
            NotificationDeliveryRepository deliveryRepository) {
        this.recipientRepository = recipientRepository;
        this.deliveryRepository = deliveryRepository;
    }

    public void assertCanView(AlertEntity alert, AccessActor actor) {
        assertCanView(
                alert,
                actor,
                recipientRepository.findByAlertId(alert.getId()));
    }

    public void assertCanView(
            AlertEntity alert,
            AccessActor actor,
            List<AlertRecipientEntity> recipients) {

        if (actor.hasPermission(Permission.ALERT_READ_HISTORY)) {
            return;
        }
        if (isRecipient(recipients, actor) || isDelivered(alert.getId(), actor)) {
            return;
        }
        throw new NotARecipientException(alert.getId());
    }

    public void assertCanConfirm(
            UUID alertId,
            AccessActor actor,
            List<AlertRecipientEntity> recipients) {

        if (isRecipient(recipients, actor) || isDelivered(alertId, actor)) {
            return;
        }
        throw new NotARecipientException(alertId);
    }

    private boolean isDelivered(UUID alertId, AccessActor actor) {
        return deliveryRepository.existsByAlertIdAndUserId(
                alertId,
                actor.userId());
    }

    private boolean isRecipient(
            List<AlertRecipientEntity> recipients,
            AccessActor actor) {

        return recipients.stream().anyMatch(recipient -> matches(recipient, actor));
    }

    private boolean matches(
            AlertRecipientEntity recipient,
            AccessActor actor) {

        if (recipient.getRecipientType() == RecipientType.USER) {
            return recipient.getRecipientRefId().equals(actor.userId());
        }
        return actor.roles().contains(recipient.getRecipientRefId());
    }
}
