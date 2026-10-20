
package edu.eci.dosw.campusseguro.alertas.service.impl;

import edu.eci.dosw.campusseguro.alertas.client.catalog.CatalogClient;
import edu.eci.dosw.campusseguro.alertas.dto.request.AlertCreateRequest;
import edu.eci.dosw.campusseguro.alertas.dto.request.AlertFilter;
import edu.eci.dosw.campusseguro.alertas.dto.request.AlertUpdateRequest;
import edu.eci.dosw.campusseguro.alertas.dto.request.RecipientRequest;
import edu.eci.dosw.campusseguro.alertas.dto.request.ScopeRequest;
import edu.eci.dosw.campusseguro.alertas.dto.response.AlertResponse;
import edu.eci.dosw.campusseguro.alertas.dto.response.AlertSummaryResponse;
import edu.eci.dosw.campusseguro.alertas.dto.response.PageResponse;
import edu.eci.dosw.campusseguro.alertas.entity.AlertEntity;
import edu.eci.dosw.campusseguro.alertas.entity.AlertRecipientEntity;
import edu.eci.dosw.campusseguro.alertas.entity.AlertScopeItemEntity;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AuditAction;
import edu.eci.dosw.campusseguro.alertas.entity.enums.ScopeType;
import edu.eci.dosw.campusseguro.alertas.exception.AlertNotFoundException;
import edu.eci.dosw.campusseguro.alertas.exception.InvalidPaginationException;
import edu.eci.dosw.campusseguro.alertas.mapper.AlertMapper;
import edu.eci.dosw.campusseguro.alertas.repository.AlertRecipientRepository;
import edu.eci.dosw.campusseguro.alertas.repository.AlertRepository;
import edu.eci.dosw.campusseguro.alertas.repository.AlertScopeItemRepository;
import edu.eci.dosw.campusseguro.alertas.repository.AlertSpecifications;
import edu.eci.dosw.campusseguro.alertas.service.AlertService;
import edu.eci.dosw.campusseguro.alertas.service.AlertStateRules;
import edu.eci.dosw.campusseguro.alertas.service.AuditService;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import edu.eci.dosw.campusseguro.alertas.dto.request.EventAlertSyncRequest;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;
import edu.eci.dosw.campusseguro.alertas.exception.InvalidActivationDateException;
import edu.eci.dosw.campusseguro.alertas.exception.InvalidAlertStateException;
import java.util.EnumSet;

@Service
public class AlertServiceImpl implements AlertService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final AlertRepository alertRepository;
    private final AlertScopeItemRepository scopeItemRepository;
    private final AlertRecipientRepository recipientRepository;
    private final CatalogClient catalogClient;
    private final AuditService auditService;
    private final AlertMapper alertMapper;
    private final AlertStateRules stateRules;
    private final Clock clock;
    private static final Set<AlertStatus> LIVE_STATUSES =
        EnumSet.of(AlertStatus.SCHEDULED, AlertStatus.ACTIVE);

    public AlertServiceImpl(
            AlertRepository alertRepository,
            AlertScopeItemRepository scopeItemRepository,
            AlertRecipientRepository recipientRepository,
            CatalogClient catalogClient,
            AuditService auditService,
            AlertMapper alertMapper,
            AlertStateRules stateRules,
            Clock clock) {
        this.alertRepository = alertRepository;
        this.scopeItemRepository = scopeItemRepository;
        this.recipientRepository = recipientRepository;
        this.catalogClient = catalogClient;
        this.auditService = auditService;
        this.alertMapper = alertMapper;
        this.stateRules = stateRules;
        this.clock = clock;
    }

    @Override
    @Transactional
    public AlertResponse create(AlertCreateRequest request, String actor) {
        ScopeRequest scope =
                alertMapper.normalizeScope(request.scope());

        List<RecipientRequest> recipients =
                alertMapper.normalizeRecipients(request.recipients());

        validateScope(scope);
        validateRecipients(recipients);

        Instant now = clock.instant();

        AlertEntity alert = alertRepository.saveAndFlush(
                alertMapper.toEntity(request, actor, now));

        List<AlertScopeItemEntity> scopeItems =
                scopeItemRepository.saveAll(
                        alertMapper.toScopeItems(alert.getId(), scope, now));

        List<AlertRecipientEntity> recipientEntities =
                recipientRepository.saveAll(
                        alertMapper.toRecipients(
                                alert.getId(), recipients, now));

        String details = "Alert created: type=" + alert.getType()
                + ", scope=" + scope.type()
                + ", recipients=" + recipients.size();

        auditService.record(
                alert.getId(),
                AuditAction.CREATE,
                actor,
                null,
                alert.getStatus(),
                details);

        return alertMapper.toResponse(
                alert, scopeItems, recipientEntities);
    }

    @Override
    @Transactional
    public AlertResponse update(
            UUID id,
            AlertUpdateRequest request,
            String actor) {

        AlertEntity alert = alertRepository.findForUpdate(id)
                .orElseThrow(() -> new AlertNotFoundException(id));

        stateRules.assertEditable(alert);

        List<AlertScopeItemEntity> currentScope =
                scopeItemRepository.findByAlertId(id);

        List<AlertRecipientEntity> currentRecipients =
                recipientRepository.findByAlertId(id);

        Instant now = clock.instant();

        ScopeRequest newScope = request.scope() == null
                ? null
                : alertMapper.normalizeScope(request.scope());

        List<AlertScopeItemEntity> desiredScope = newScope == null
                ? null
                : alertMapper.toScopeItems(id, newScope, now);

        boolean scopeChanged = desiredScope != null
                && !keys(desiredScope, this::scopeKey)
                        .equals(keys(currentScope, this::scopeKey));

        List<RecipientRequest> newRecipients =
                request.recipients() == null
                        ? null
                        : alertMapper.normalizeRecipients(
                                request.recipients());

        List<AlertRecipientEntity> desiredRecipients =
                newRecipients == null
                        ? null
                        : alertMapper.toRecipients(
                                id, newRecipients, now);

        boolean recipientsChanged = desiredRecipients != null
                && !keys(desiredRecipients, this::recipientKey)
                        .equals(keys(currentRecipients, this::recipientKey));

        if (scopeChanged) {
            validateScope(newScope);
        }

        if (recipientsChanged) {
            validateRecipients(newRecipients);
        }

        List<String> changedFields = new ArrayList<>();

        if (request.type() != null
                && request.type() != alert.getType()) {
            alert.setType(request.type());
            changedFields.add("type");
        }

        if (request.instructions() != null
                && !request.instructions().strip()
                        .equals(alert.getInstructions())) {
            alert.setInstructions(request.instructions().strip());
            changedFields.add("instructions");
        }

        if (scopeChanged) {
            changedFields.add("scope");
        }

        if (recipientsChanged) {
            changedFields.add("recipients");
        }

        if (changedFields.isEmpty()) {
            return alertMapper.toResponse(
                    alert, currentScope, currentRecipients);
        }

        alert.setUpdatedAt(now);
        alertRepository.saveAndFlush(alert);

        if (scopeChanged) {
            replace(
                    currentScope,
                    desiredScope,
                    this::scopeKey,
                    scopeItemRepository);
        }

        if (recipientsChanged) {
            replace(
                    currentRecipients,
                    desiredRecipients,
                    this::recipientKey,
                    recipientRepository);
        }

        auditService.record(
                id,
                AuditAction.UPDATE,
                actor,
                alert.getStatus(),
                alert.getStatus(),
                "Modified fields: "
                        + String.join(", ", changedFields));

        return alertMapper.toResponse(
                alert,
                scopeItemRepository.findByAlertId(id),
                recipientRepository.findByAlertId(id));
    }

    @Override
    @Transactional(readOnly = true)
    public AlertResponse findById(UUID id) {
        AlertEntity alert = alertRepository.findById(id)
                .orElseThrow(() -> new AlertNotFoundException(id));

        return alertMapper.toResponse(
                alert,
                scopeItemRepository.findByAlertId(id),
                recipientRepository.findByAlertId(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AlertSummaryResponse> search(
            AlertFilter filter) {

        int page = filter.page() == null
                ? DEFAULT_PAGE
                : filter.page();

        int size = filter.size() == null
                ? DEFAULT_PAGE_SIZE
                : filter.size();

        validatePagination(filter, page, size);

        Specification<AlertEntity> specification =
                AlertSpecifications.fromFilter(filter, clock.getZone());

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")));

        Page<AlertEntity> result =
                alertRepository.findAll(specification, pageable);

        List<UUID> ids = result.getContent()
                .stream()
                .map(AlertEntity::getId)
                .toList();

        Map<UUID, List<AlertScopeItemEntity>> scopeByAlert =
                ids.isEmpty()
                        ? Map.of()
                        : scopeItemRepository.findByAlertIdIn(ids)
                                .stream()
                                .collect(Collectors.groupingBy(
                                        AlertScopeItemEntity::getAlertId));

        List<AlertSummaryResponse> content =
                result.getContent()
                        .stream()
                        .map(alert -> alertMapper.toSummary(
                                alert,
                                scopeByAlert.getOrDefault(
                                        alert.getId(), List.of())))
                        .toList();

        return PageResponse.of(content, result);
    }

    private void validatePagination(
            AlertFilter filter,
            int page,
            int size) {

        if (page < 0) {
            throw new InvalidPaginationException(
                    "page",
                    "Page index cannot be negative");
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidPaginationException(
                    "size",
                    "Page size must be between 1 and "
                            + MAX_PAGE_SIZE);
        }

        if (filter.startDate() != null
                && filter.endDate() != null
                && filter.startDate().isAfter(filter.endDate())) {
            throw new InvalidPaginationException(
                    "startDate",
                    "Start date cannot be after end date");
        }
    }

    private void validateScope(ScopeRequest scope) {
        if (scope.type() != ScopeType.CAMPUS) {
            catalogClient.validateBuildingsZones(
                    scope.type(), scope.ids());
        }
    }

    private void validateRecipients(
            List<RecipientRequest> recipients) {
        catalogClient.validateRecipients(recipients);
    }

    private <T> void replace(
            List<T> current,
            List<T> desired,
            Function<T, String> keyOf,
            JpaRepository<T, UUID> repository) {

        Set<String> currentKeys = keys(current, keyOf);
        Set<String> desiredKeys = keys(desired, keyOf);

        List<T> toRemove = current.stream()
                .filter(item -> !desiredKeys.contains(keyOf.apply(item)))
                .toList();

        List<T> toAdd = desired.stream()
                .filter(item -> !currentKeys.contains(keyOf.apply(item)))
                .toList();

        repository.deleteAll(toRemove);
        repository.flush();
        repository.saveAll(toAdd);
    }

    private <T> Set<String> keys(
            List<T> items,
            Function<T, String> keyOf) {
        return items.stream()
                .map(keyOf)
                .collect(Collectors.toSet());
    }

    private String scopeKey(AlertScopeItemEntity item) {
        return item.getScopeType()
                + "|"
                + Objects.toString(item.getScopeRefId(), "");
    }

    private String recipientKey(AlertRecipientEntity recipient) {
        return recipient.getRecipientType()
                + "|"
                + recipient.getRecipientRefId();
    }

    @Override
    @Transactional
    public AlertResponse synchronizeFromEvent(
            UUID eventId,
            EventAlertSyncRequest request,
            String actor) {

        Instant now = clock.instant();

        if (!request.activationAt().isAfter(now)) {
            throw new InvalidActivationDateException();
        }

        ScopeRequest scope = alertMapper.normalizeScope(request.scope());
        List<RecipientRequest> recipients =
                alertMapper.normalizeRecipients(request.recipients());

        validateScope(scope);
        validateRecipients(recipients);

        return alertRepository.findByEventIdAndStatusIn(eventId, LIVE_STATUSES)
                .map(existing -> updateFromEvent(existing, request, scope, recipients, actor, now))
                .orElseGet(() -> createFromEvent(eventId, request, scope, recipients, actor, now));
    }

    private AlertResponse createFromEvent(
            UUID eventId,
            EventAlertSyncRequest request,
            ScopeRequest scope,
            List<RecipientRequest> recipients,
            String actor,
            Instant now) {

        AlertEntity alert = alertRepository.saveAndFlush(
                alertMapper.toEventEntity(eventId, request, actor, now));

        List<AlertScopeItemEntity> scopeItems = scopeItemRepository.saveAll(
                alertMapper.toScopeItems(alert.getId(), scope, now));

        List<AlertRecipientEntity> recipientEntities = recipientRepository.saveAll(
                alertMapper.toRecipients(alert.getId(), recipients, now));

        auditService.record(
                alert.getId(),
                AuditAction.CREATE,
                actor,
                null,
                alert.getStatus(),
                "Alert created from event " + eventId
                        + ", activationAt=" + request.activationAt());

        return alertMapper.toResponse(alert, scopeItems, recipientEntities);
    }

    private AlertResponse updateFromEvent(
            AlertEntity alert,
            EventAlertSyncRequest request,
            ScopeRequest scope,
            List<RecipientRequest> recipients,
            String actor,
            Instant now) {

        // Si ya se activó, MS06 ya no puede cambiarla.
        if (alert.getStatus() != AlertStatus.SCHEDULED) {
            throw new InvalidAlertStateException(alert.getStatus());
        }

        UUID id = alert.getId();
        List<AlertScopeItemEntity> currentScope = scopeItemRepository.findByAlertId(id);
        List<AlertRecipientEntity> currentRecipients = recipientRepository.findByAlertId(id);

        List<AlertScopeItemEntity> desiredScope = alertMapper.toScopeItems(id, scope, now);
        List<AlertRecipientEntity> desiredRecipients = alertMapper.toRecipients(id, recipients, now);

        boolean scopeChanged = !keys(desiredScope, this::scopeKey)
                .equals(keys(currentScope, this::scopeKey));
        boolean recipientsChanged = !keys(desiredRecipients, this::recipientKey)
                .equals(keys(currentRecipients, this::recipientKey));

        List<String> changedFields = new ArrayList<>();

        if (request.type() != alert.getType()) {
            alert.setType(request.type());
            changedFields.add("type");
        }
        if (!request.instructions().strip().equals(alert.getInstructions())) {
            alert.setInstructions(request.instructions().strip());
            changedFields.add("instructions");
        }
        if (!request.activationAt().equals(alert.getActivationAt())) {
            alert.setActivationAt(request.activationAt());
            changedFields.add("activationAt");
        }
        if (scopeChanged) changedFields.add("scope");
        if (recipientsChanged) changedFields.add("recipients");

        if (changedFields.isEmpty()) {
            return alertMapper.toResponse(alert, currentScope, currentRecipients); // reintento idéntico
        }

        alert.setUpdatedAt(now);
        alertRepository.saveAndFlush(alert);

        if (scopeChanged) replace(currentScope, desiredScope, this::scopeKey, scopeItemRepository);
        if (recipientsChanged) replace(currentRecipients, desiredRecipients, this::recipientKey, recipientRepository);

        auditService.record(
                id,
                AuditAction.UPDATE,
                actor,
                alert.getStatus(),
                alert.getStatus(),
                "Synchronized from event. Modified fields: " + String.join(", ", changedFields));

        return alertMapper.toResponse(
                alert,
                scopeItemRepository.findByAlertId(id),
                recipientRepository.findByAlertId(id));
    }
}
