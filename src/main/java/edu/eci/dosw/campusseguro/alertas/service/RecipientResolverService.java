package edu.eci.dosw.campusseguro.alertas.service;

import edu.eci.dosw.campusseguro.alertas.client.catalog.CatalogClient;
import edu.eci.dosw.campusseguro.alertas.client.catalog.ResolvedUser;
import edu.eci.dosw.campusseguro.alertas.entity.AlertRecipientEntity;
import edu.eci.dosw.campusseguro.alertas.entity.enums.RecipientType;
import edu.eci.dosw.campusseguro.alertas.exception.ExternalServiceUnavailableException;
import edu.eci.dosw.campusseguro.alertas.repository.AlertRecipientRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RecipientResolverService {

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
                ResolvedUser user = catalogClient.findUserById(recipient.getRecipientRefId())
                        .orElseThrow(() -> new ExternalServiceUnavailableException(
                                "catálogo MS01: usuario " + recipient.getRecipientRefId() + " no encontrado"));
                users.putIfAbsent(user.id(), user);
            } else {
                for (ResolvedUser user : catalogClient.getUsersByRole(recipient.getRecipientRefId())) {
                    users.putIfAbsent(user.id(), user);
                }
            }
        }

        return List.copyOf(users.values());
    }
}
