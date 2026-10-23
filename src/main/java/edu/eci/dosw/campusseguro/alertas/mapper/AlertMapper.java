package edu.eci.dosw.campusseguro.alertas.mapper;

import edu.eci.dosw.campusseguro.alertas.dto.request.AlertCreateRequest;
import edu.eci.dosw.campusseguro.alertas.dto.request.AlertOriginRequest;
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
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AlertMapper {

    public ScopeRequest normalizeScope(ScopeRequest scope) {
        Objects.requireNonNull(scope, "Scope cannot be null");

        if (scope.type() == ScopeType.CAMPUS) {
            return new ScopeRequest(ScopeType.CAMPUS, List.of());
        }

        List<String> scopeIds = Objects.requireNonNull(
                scope.ids(),
                "Scope IDs cannot be null");

        List<String> normalizedIds = scopeIds.stream()
                .map(id -> Objects.requireNonNull(
                        id,
                        "Scope ID cannot be null").strip())
                .distinct()
                .toList();

        return new ScopeRequest(scope.type(), normalizedIds);
    }

    public List<RecipientRequest> normalizeRecipients(
            List<RecipientRequest> recipients) {

        Objects.requireNonNull(recipients, "Recipients cannot be null");

        return recipients.stream()
                .map(recipient -> {
                    Objects.requireNonNull(
                            recipient,
                            "Recipient cannot be null");

                    return new RecipientRequest(
                            recipient.type(),
                            Objects.requireNonNull(
                                    recipient.id(),
                                    "Recipient ID cannot be null")
                                    .strip());
                })
                .distinct()
                .toList();
    }

    public AlertEntity toEntity(
            AlertCreateRequest request,
            String actor,
            Instant now) {

        Objects.requireNonNull(request, "Alert request cannot be null");
        Objects.requireNonNull(actor, "Actor cannot be null");
        Objects.requireNonNull(now, "Creation time cannot be null");

        AlertOriginRequest origin = request.origin();

        AlertEntity alert = new AlertEntity();
        alert.setType(request.type());
        alert.setTitle(request.title().strip());
        alert.setStatus(AlertStatus.DRAFT);
        alert.setInstructions(request.instructions().strip());
        alert.setCreatedBy(actor);
        alert.setCreatedAt(now);
        alert.setUpdatedAt(now);

        if (origin == null) {
            alert.setOrigin(AlertOrigin.MANUAL);
        } else {
            alert.setOrigin(origin.type());
            alert.setSimulationId(origin.simulationId());
        }

        return alert;
    }

    public List<AlertScopeItemEntity> toScopeItems(
            UUID alertId,
            ScopeRequest scope,
            Instant now) {

        Objects.requireNonNull(alertId, "Alert ID cannot be null");
        Objects.requireNonNull(scope, "Scope cannot be null");
        Objects.requireNonNull(now, "Creation time cannot be null");

        if (scope.type() == ScopeType.CAMPUS) {
            return List.of(
                    newScopeItem(alertId, ScopeType.CAMPUS, null, now));
        }

        List<String> scopeIds = Objects.requireNonNull(
                scope.ids(),
                "Scope IDs cannot be null");

        return scopeIds.stream()
                .map(id -> newScopeItem(alertId, scope.type(), id, now))
                .toList();
    }

    public List<AlertRecipientEntity> toRecipients(
            UUID alertId,
            List<RecipientRequest> recipients,
            Instant now) {

        Objects.requireNonNull(alertId, "Alert ID cannot be null");
        Objects.requireNonNull(recipients, "Recipients cannot be null");
        Objects.requireNonNull(now, "Creation time cannot be null");

        return recipients.stream()
                .map(recipient -> {
                    Objects.requireNonNull(
                            recipient,
                            "Recipient cannot be null");

                    AlertRecipientEntity entity = new AlertRecipientEntity();
                    entity.setAlertId(alertId);
                    entity.setRecipientType(recipient.type());
                    entity.setRecipientRefId(recipient.id());
                    entity.setCreatedAt(now);
                    entity.setUpdatedAt(now);

                    return entity;
                })
                .toList();
    }

    public ScopeRequest toScope(List<AlertScopeItemEntity> items) {
        Objects.requireNonNull(items, "Scope items cannot be null");

        if (items.isEmpty()) {
            return null;
        }

        ScopeType type = Objects.requireNonNull(
                items.get(0).getScopeType(),
                "Scope type cannot be null");

        boolean hasDifferentTypes = items.stream()
                .anyMatch(item -> item.getScopeType() != type);

        if (hasDifferentTypes) {
            throw new IllegalStateException(
                    "All scope items must have the same scope type");
        }

        if (type == ScopeType.CAMPUS) {
            return new ScopeRequest(ScopeType.CAMPUS, List.of());
        }

        List<String> ids = items.stream()
                .map(AlertScopeItemEntity::getScopeRefId)
                .filter(Objects::nonNull)
                .sorted()
                .toList();

        return new ScopeRequest(type, ids);
    }

    public List<RecipientRequest> toRecipientRequests(
            List<AlertRecipientEntity> recipients) {

        Objects.requireNonNull(recipients, "Recipients cannot be null");

        return recipients.stream()
                .map(recipient -> {
                    Objects.requireNonNull(
                            recipient,
                            "Recipient entity cannot be null");

                    return new RecipientRequest(
                            recipient.getRecipientType(),
                            recipient.getRecipientRefId());
                })
                .sorted(
                        Comparator.comparing(RecipientRequest::type)
                                .thenComparing(RecipientRequest::id))
                .toList();
    }

    public AlertResponse toResponse(
            AlertEntity alert,
            List<AlertScopeItemEntity> scopeItems,
            List<AlertRecipientEntity> recipients) {

        Objects.requireNonNull(alert, "Alert cannot be null");
        Objects.requireNonNull(scopeItems, "Scope items cannot be null");
        Objects.requireNonNull(recipients, "Recipients cannot be null");

        return new AlertResponse(
                alert.getId(),
                alert.getType(),
                alert.getTitle(),
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

    public AlertSummaryResponse toSummary(
            AlertEntity alert,
            List<AlertScopeItemEntity> scopeItems) {

        Objects.requireNonNull(alert, "Alert cannot be null");
        Objects.requireNonNull(scopeItems, "Scope items cannot be null");

        return new AlertSummaryResponse(
                alert.getId(),
                alert.getType(),
                alert.getTitle(),
                toScope(scopeItems),
                alert.getStatus(),
                alert.getOrigin(),
                alert.getSimulationId(),
                alert.getCreatedAt());
    }

    private AlertScopeItemEntity newScopeItem(
            UUID alertId,
            ScopeType type,
            String refId,
            Instant now) {

        AlertScopeItemEntity item = new AlertScopeItemEntity();
        item.setAlertId(alertId);
        item.setScopeType(type);
        item.setScopeRefId(refId);
        item.setCreatedAt(now);
        item.setUpdatedAt(now);

        return item;
    }
}