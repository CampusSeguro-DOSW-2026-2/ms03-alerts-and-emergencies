package edu.eci.dosw.campusseguro.alertas.service;

import edu.eci.dosw.campusseguro.alertas.client.catalog.CatalogClient;
import edu.eci.dosw.campusseguro.alertas.client.catalog.ResolvedUser;
import edu.eci.dosw.campusseguro.alertas.entity.AlertRecipientEntity;
import edu.eci.dosw.campusseguro.alertas.entity.enums.RecipientType;
import edu.eci.dosw.campusseguro.alertas.repository.AlertRecipientRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RecipientResolverService {

    private static final Logger log = LoggerFactory.getLogger(RecipientResolverService.class);

    private final AlertRecipientRepository recipientRepository;
    private final CatalogClient catalogClient;

    public RecipientResolverService(AlertRecipientRepository recipientRepository, CatalogClient catalogClient) {
        this.recipientRepository = recipientRepository;
        this.catalogClient = catalogClient;
    }

    public List<ResolvedUser> resolve(UUID alertId) {
        Map<String, ResolvedUser> users = new LinkedHashMap<>();
        List<AlertRecipientEntity> recipients = recipientRepository.findByAlertId(alertId);

        for (AlertRecipientEntity recipient : recipients) {
            if (recipient.getRecipientType() == RecipientType.USER) {
                // Un usuario que ya no existe en MS01 no debe impedir notificar a los demás.
                Optional<ResolvedUser> user = catalogClient.findUserById(recipient.getRecipientRefId());
                if (user.isEmpty()) {
                    log.warn("Destinatario {} de la alerta {} no existe en MS01; se omite",
                            recipient.getRecipientRefId(), alertId);
                    continue;
                }
                users.putIfAbsent(user.get().id(), user.get());
            } else {
                for (ResolvedUser user : catalogClient.getUsersByRole(recipient.getRecipientRefId())) {
                    users.putIfAbsent(user.id(), user);
                }
            }
        }

        return List.copyOf(users.values());
    }
}
