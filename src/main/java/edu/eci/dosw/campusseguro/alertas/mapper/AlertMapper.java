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
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertType;
import edu.eci.dosw.campusseguro.alertas.entity.enums.ScopeType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

    /**
     * Normalizes the scope of an alert. A request may send the legacy single
     * {@code scope} or the new {@code scopes} list; the list wins when both are
     * present. Items of the same type are merged and every type is returned once.
     */
    public List<ScopeRequest> normalizeScopes(
            ScopeRequest scope,
            List<ScopeRequest> scopes) {

        if (scopes != null && !scopes.isEmpty()) {
            Map<ScopeType, LinkedHashSet<String>> grouped = new LinkedHashMap<>();
            for (ScopeRequest item : scopes) {
                ScopeRequest normalized = normalizeScope(item);
                if (normalized.type() == ScopeType.CAMPUS) {
                    grouped.computeIfAbsent(ScopeType.CAMPUS, type -> new LinkedHashSet<>());
                    continue;
                }
                grouped.computeIfAbsent(normalized.type(), type -> new LinkedHashSet<>())
                        .addAll(normalized.ids());
            }
            return grouped.entrySet().stream()
                    .map(entry -> new ScopeRequest(entry.getKey(), List.copyOf(entry.getValue())))
                    .sorted(Comparator.comparingInt(item -> item.type().ordinal()))
                    .toList();
        }

        return scope == null ? List.of() : List.of(normalizeScope(scope));
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
        alert.setTypes(new LinkedHashSet<>(sortedTypes(request.types())));
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

        alert.setPurpose(request.purpose() == null || request.purpose().isBlank()
                ? null
                : request.purpose().strip());

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

    public List<AlertScopeItemEntity> toScopeItems(
            UUID alertId,
            List<ScopeRequest> scopes,
            Instant now) {

        Objects.requireNonNull(scopes, "Scopes cannot be null");

        return scopes.stream()
                .flatMap(scope -> toScopeItems(alertId, scope, now).stream())
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

    /**
     * Groups the stored scope items by type. An alert may cover buildings and
     * zones at the same time, so the result can have more than one element.
     */
    public List<ScopeRequest> toScopes(List<AlertScopeItemEntity> items) {
        Objects.requireNonNull(items, "Scope items cannot be null");

        if (items.isEmpty()) {
            return List.of();
        }

        Map<ScopeType, List<String>> grouped = new LinkedHashMap<>();
        for (AlertScopeItemEntity item : items) {
            ScopeType type = Objects.requireNonNull(
                    item.getScopeType(),
                    "Scope type cannot be null");
            if (type == ScopeType.CAMPUS) {
                grouped.computeIfAbsent(ScopeType.CAMPUS, key -> new ArrayList<>());
                continue;
            }
            grouped.computeIfAbsent(type, key -> new ArrayList<>())
                    .add(item.getScopeRefId());
        }

        return grouped.entrySet().stream()
                .map(entry -> new ScopeRequest(
                        entry.getKey(),
                        entry.getValue().stream()
                                .filter(Objects::nonNull)
                                .sorted()
                                .toList()))
                .sorted(Comparator.comparingInt(item -> item.type().ordinal()))
                .toList();
    }

    /** Legacy single-scope view: the only scope, or {@code null} when there are several. */
    public ScopeRequest toScope(List<AlertScopeItemEntity> items) {
        List<ScopeRequest> scopes = toScopes(items);
        return scopes.size() == 1 ? scopes.get(0) : null;
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
                sortedTypes(alert.getTypes()),
                alert.getTitle(),
                alert.getStatus(),
                alert.getOrigin(),
                alert.getSimulationId(),
                alert.getPurpose(),
                toScope(scopeItems),
                toScopes(scopeItems),
                alert.getInstructions(),
                toRecipientRequests(recipients),
                alert.getCreatedBy(),
                alert.getCreatedAt(),
                alert.getUpdatedAt(),
                alert.getActivationAt(),
                alert.getActivatedAt(),
                alert.getArchivedAt());
    }

    public AlertSummaryResponse toSummary(
            AlertEntity alert,
            List<AlertScopeItemEntity> scopeItems,
            List<AlertRecipientEntity> recipients) {

        Objects.requireNonNull(alert, "Alert cannot be null");
        Objects.requireNonNull(scopeItems, "Scope items cannot be null");
        Objects.requireNonNull(recipients, "Recipients cannot be null");

        return new AlertSummaryResponse(
                alert.getId(),
                sortedTypes(alert.getTypes()),
                alert.getTitle(),
                alert.getPurpose(),
                toScope(scopeItems),
                toScopes(scopeItems),
                alert.getStatus(),
                alert.getOrigin(),
                alert.getSimulationId(),
                alert.getCreatedAt(),
                alert.getInstructions(),
                toRecipientRequests(recipients));
    }

    public List<AlertType> sortedTypes(Collection<AlertType> types) {
        if (types == null) {
            return List.of();
        }

        return types.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(AlertType::ordinal))
                .toList();
    }

    public Set<AlertType> toTypeSet(Collection<AlertType> types) {
        return new LinkedHashSet<>(sortedTypes(types));
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