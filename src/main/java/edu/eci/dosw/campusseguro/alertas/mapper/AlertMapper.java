package edu.eci.dosw.campusseguro.alertas.mapper;

import edu.eci.dosw.campusseguro.alertas.dto.request.AlertCreateRequest;
import edu.eci.dosw.campusseguro.alertas.dto.request.RecipientRequest;
import edu.eci.dosw.campusseguro.alertas.dto.request.ScopeRequest;
import edu.eci.dosw.campusseguro.alertas.dto.response.AlertResponse;
import edu.eci.dosw.campusseguro.alertas.dto.response.AlertSummaryResponse;
import edu.eci.dosw.campusseguro.alertas.entity.AlertEntity;
import edu.eci.dosw.campusseguro.alertas.entity.AlertRecipientEntity;
import edu.eci.dosw.campusseguro.alertas.entity.AlertScopeItemEntity;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertOrigin;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;
import edu.eci.dosw.campusseguro.alertas.entity.enums.ScopeType;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AlertMapper {

    public ScopeRequest normalizeScope(ScopeRequest scope) {
        if (scope.tipo() == ScopeType.CAMPUS) {
            return new ScopeRequest(ScopeType.CAMPUS, List.of());
        }
        List<String> ids = scope.ids().stream().map(String::strip).distinct().toList();
        return new ScopeRequest(scope.tipo(), ids);
    }

    public List<RecipientRequest> normalizeRecipients(List<RecipientRequest> recipients) {
        return recipients.stream()
                .map(recipient -> new RecipientRequest(recipient.tipo(), recipient.id().strip()))
                .distinct()
                .toList();
    }

    public AlertEntity toEntity(AlertCreateRequest request, String actor, Instant now) {
        AlertEntity alert = new AlertEntity();
        alert.setType(request.tipo());
        alert.setStatus(AlertStatus.DRAFT);
        alert.setOrigin(AlertOrigin.MANUAL);
        alert.setInstructions(request.instrucciones().strip());
        alert.setCreatedBy(actor);
        alert.setCreatedAt(now);
        alert.setUpdatedAt(now);
        return alert;
    }

    public List<AlertScopeItemEntity> toScopeItems(UUID alertId, ScopeRequest scope, Instant now) {
        if (scope.tipo() == ScopeType.CAMPUS) {
            return List.of(newScopeItem(alertId, ScopeType.CAMPUS, null, now));
        }
        return scope.ids().stream()
                .map(id -> newScopeItem(alertId, scope.tipo(), id, now))
                .toList();
    }

    public List<AlertRecipientEntity> toRecipients(UUID alertId, List<RecipientRequest> recipients, Instant now) {
        return recipients.stream()
                .map(recipient -> {
                    AlertRecipientEntity entity = new AlertRecipientEntity();
                    entity.setAlertId(alertId);
                    entity.setRecipientType(recipient.tipo());
                    entity.setRecipientRefId(recipient.id());
                    entity.setCreatedAt(now);
                    entity.setUpdatedAt(now);
                    return entity;
                })
                .toList();
    }

    public ScopeRequest toScope(List<AlertScopeItemEntity> items) {
        if (items.isEmpty()) {
            return null;
        }
        ScopeType type = items.stream()
                .map(AlertScopeItemEntity::getScopeType)
                .min(Comparator.naturalOrder())
                .orElseThrow();
        if (type == ScopeType.CAMPUS) {
            return new ScopeRequest(type, List.of());
        }
        List<String> ids = items.stream()
                .filter(item -> item.getScopeType() == type)
                .map(AlertScopeItemEntity::getScopeRefId)
                .sorted()
                .toList();
        return new ScopeRequest(type, ids);
    }

    public List<RecipientRequest> toRecipientRequests(List<AlertRecipientEntity> recipients) {
        return recipients.stream()
                .map(recipient -> new RecipientRequest(recipient.getRecipientType(), recipient.getRecipientRefId()))
                .sorted(Comparator.comparing(RecipientRequest::tipo).thenComparing(RecipientRequest::id))
                .toList();
    }

    public AlertResponse toResponse(AlertEntity alert, List<AlertScopeItemEntity> scopeItems, List<AlertRecipientEntity> recipients) {
        return new AlertResponse(
                alert.getId(),
                alert.getType(),
                alert.getStatus(),
                alert.getOrigin(),
                alert.getSimulationId(),
                toScope(scopeItems),
                alert.getInstructions(),
                toRecipientRequests(recipients),
                alert.getCreatedBy(),
                alert.getCreatedAt(),
                alert.getUpdatedAt(),
                alert.getActivationAt(),
                alert.getActivatedAt(),
                alert.getEndedAt(),
                alert.getArchivedAt());
    }

    public AlertSummaryResponse toSummary(AlertEntity alert, List<AlertScopeItemEntity> scopeItems) {
        return new AlertSummaryResponse(
                alert.getId(),
                alert.getType(),
                toScope(scopeItems),
                alert.getStatus(),
                alert.getOrigin(),
                alert.getCreatedAt());
    }

    private AlertScopeItemEntity newScopeItem(UUID alertId, ScopeType type, String refId, Instant now) {
        AlertScopeItemEntity item = new AlertScopeItemEntity();
        item.setAlertId(alertId);
        item.setScopeType(type);
        item.setScopeRefId(refId);
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        return item;
    }
}
