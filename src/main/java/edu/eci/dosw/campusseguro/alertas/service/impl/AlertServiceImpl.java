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
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertOrigin;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AuditAction;
import edu.eci.dosw.campusseguro.alertas.entity.enums.ScopeType;
import edu.eci.dosw.campusseguro.alertas.exception.AlertNotFoundException;
import edu.eci.dosw.campusseguro.alertas.exception.DuplicateSimulationAlertException;
import edu.eci.dosw.campusseguro.alertas.exception.InvalidActivationDateException;
import edu.eci.dosw.campusseguro.alertas.exception.InvalidAlertRequestException;
import edu.eci.dosw.campusseguro.alertas.exception.InvalidPaginationException;
import edu.eci.dosw.campusseguro.alertas.mapper.AlertMapper;
import edu.eci.dosw.campusseguro.alertas.repository.AlertRecipientRepository;
import edu.eci.dosw.campusseguro.alertas.repository.AlertRepository;
import edu.eci.dosw.campusseguro.alertas.repository.AlertScopeItemRepository;
import edu.eci.dosw.campusseguro.alertas.repository.AlertSpecifications;
import edu.eci.dosw.campusseguro.alertas.security.AccessActor;
import edu.eci.dosw.campusseguro.alertas.security.AlertAccessPolicy;
import edu.eci.dosw.campusseguro.alertas.service.AlertCreationResult;
import edu.eci.dosw.campusseguro.alertas.service.AlertLifecycleService;
import edu.eci.dosw.campusseguro.alertas.service.AlertService;
import edu.eci.dosw.campusseguro.alertas.service.AlertStateRules;
import edu.eci.dosw.campusseguro.alertas.service.AuditService;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final AlertAccessPolicy accessPolicy;
    private final AlertLifecycleService alertLifecycleService;
    private final Clock clock;
    private final TransactionTemplate createTransaction;

    public AlertServiceImpl(
            AlertRepository alertRepository,
            AlertScopeItemRepository scopeItemRepository,
            AlertRecipientRepository recipientRepository,
            CatalogClient catalogClient,
            AuditService auditService,
            AlertMapper alertMapper,
            AlertStateRules stateRules,
            AlertAccessPolicy accessPolicy,
            AlertLifecycleService alertLifecycleService,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.alertRepository = alertRepository;
        this.scopeItemRepository = scopeItemRepository;
        this.recipientRepository = recipientRepository;
        this.catalogClient = catalogClient;
        this.auditService = auditService;
        this.alertMapper = alertMapper;
        this.stateRules = stateRules;
        this.accessPolicy = accessPolicy;
        this.alertLifecycleService = alertLifecycleService;
        this.clock = clock;
        this.createTransaction = new TransactionTemplate(transactionManager);
    }

    @Override
    public AlertCreationResult create(AlertCreateRequest request, String actor) {
        CreationData data = prepareCreation(request);
        AlertCreationResult existing = findExistingSimulationAlert(data);
        if (existing != null) {
            return existing;
        }

        try {
            return createTransaction.execute(status -> createNewAlert(data, actor));
        } catch (DataIntegrityViolationException ex) {
            AlertCreationResult concurrent = findExistingSimulationAlert(data);
            if (concurrent != null) {
                return concurrent;
            }
            throw ex;
        }
    }

    private CreationData prepareCreation(AlertCreateRequest request) {
        ScopeRequest scope = alertMapper.normalizeScope(request.alcance());
        List<RecipientRequest> recipients = alertMapper.normalizeRecipients(request.destinatarios());
        validateScope(scope);
        validateRecipients(recipients);

        AlertOrigin origin = request.origen() == null ? AlertOrigin.MANUAL : request.origen().tipo();
        String simulationId = request.origen() == null ? null : blankToNull(request.origen().simulationId());
        boolean immediate = Boolean.TRUE.equals(request.activarInmediatamente());

        if (origin == AlertOrigin.SIMULATION && simulationId == null) {
            throw new InvalidAlertRequestException(
                    "origen.simulationId", "El origen Simulacro exige el ID del simulacro");
        }
        if (origin == AlertOrigin.MANUAL && simulationId != null) {
            throw new InvalidAlertRequestException(
                    "origen.simulationId", "El origen Manual no admite ID de simulacro");
        }
        if (immediate && request.activationAt() != null) {
            throw new InvalidAlertRequestException(
                    "activationAt", "No se puede programar y activar inmediatamente a la vez");
        }

        Instant now = clock.instant();
        if (request.activationAt() != null && !request.activationAt().isAfter(now)) {
            throw new InvalidActivationDateException();
        }

        return new CreationData(request, scope, recipients, origin, simulationId, immediate, now);
    }

    private AlertCreationResult findExistingSimulationAlert(CreationData data) {
        if (data.simulationId() == null) {
            return null;
        }
        return alertRepository
                .findFirstBySimulationIdAndStatusIn(
                        data.simulationId(), List.of(AlertStatus.SCHEDULED, AlertStatus.ACTIVE))
                .map(existing -> {
                    if (!samePayload(existing, data)) {
                        throw new DuplicateSimulationAlertException(data.simulationId(), existing.getId());
                    }
                    List<AlertScopeItemEntity> scope = scopeItemRepository.findByAlertId(existing.getId());
                    List<AlertRecipientEntity> recipients = recipientRepository.findByAlertId(existing.getId());
                    return new AlertCreationResult(alertMapper.toResponse(existing, scope, recipients), false);
                })
                .orElse(null);
    }

    private AlertCreationResult createNewAlert(CreationData data, String actor) {
        AlertCreateRequest request = data.request();
        AlertEntity alert = alertMapper.toEntity(request, actor, data.now());
        if (data.activationAt() != null) {
            alert.setStatus(AlertStatus.SCHEDULED);
            alert.setActivationAt(data.activationAt());
        }
        alertRepository.saveAndFlush(alert);

        List<AlertScopeItemEntity> scopeItems =
                scopeItemRepository.saveAll(alertMapper.toScopeItems(alert.getId(), data.scope(), data.now()));
        List<AlertRecipientEntity> recipientEntities =
                recipientRepository.saveAll(alertMapper.toRecipients(alert.getId(), data.recipients(), data.now()));

        String details = "Alerta creada: tipo=" + alert.getType()
                + ", origen=" + alert.getOrigin()
                + (alert.getSimulationId() == null ? "" : ", simulacro=" + alert.getSimulationId())
                + ", alcance=" + data.scope().tipo()
                + ", destinatarios=" + data.recipients().size();
        auditService.record(alert.getId(), AuditAction.CREATE, actor, null, alert.getStatus(), details);

        if (data.immediate()) {
            alertLifecycleService.activate(alert.getId(), actor);
            AlertEntity activated = alertRepository.findById(alert.getId()).orElse(alert);
            return new AlertCreationResult(
                    alertMapper.toResponse(
                            activated,
                            scopeItemRepository.findByAlertId(alert.getId()),
                            recipientRepository.findByAlertId(alert.getId())),
                    true);
        }

        return new AlertCreationResult(alertMapper.toResponse(alert, scopeItems, recipientEntities), true);
    }

    private boolean samePayload(AlertEntity existing, CreationData data) {
        if (existing.getType() != data.request().tipo()
                || !existing.getInstructions().equals(data.request().instrucciones().strip())
                || existing.getOrigin() != data.origin()
                || !Objects.equals(existing.getActivationAt(), data.activationAt())) {
            return false;
        }

        List<AlertScopeItemEntity> existingScope = scopeItemRepository.findByAlertId(existing.getId());
        ScopeRequest existingScopeRequest = alertMapper.toScope(existingScope);
        if (!Objects.equals(
                alertMapper.normalizeScope(existingScopeRequest == null ? new ScopeRequest(ScopeType.CAMPUS, List.of()) : existingScopeRequest),
                data.scope())) {
            return false;
        }

        Set<String> existingRecipients = recipientRepository.findByAlertId(existing.getId()).stream()
                .map(this::recipientKey)
                .collect(Collectors.toSet());
        Set<String> requestedRecipients = data.recipients().stream()
                .map(recipient -> recipient.tipo() + "|" + recipient.id())
                .collect(Collectors.toSet());
        return existingRecipients.equals(requestedRecipients);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private record CreationData(
            AlertCreateRequest request,
            ScopeRequest scope,
            List<RecipientRequest> recipients,
            AlertOrigin origin,
            String simulationId,
            boolean immediate,
            Instant now) {

        Instant activationAt() {
            return request.activationAt();
        }
    }

    @Override
    @Transactional
    public AlertResponse update(UUID id, AlertUpdateRequest request, String actor) {
        AlertEntity alert = alertRepository.findForUpdate(id).orElseThrow(() -> new AlertNotFoundException(id));
        stateRules.assertNotArchived(alert);
        stateRules.assertEditable(alert);

        List<AlertScopeItemEntity> currentScope = scopeItemRepository.findByAlertId(id);
        List<AlertRecipientEntity> currentRecipients = recipientRepository.findByAlertId(id);
        Instant now = clock.instant();

        ScopeRequest newScope = request.alcance() == null ? null : alertMapper.normalizeScope(request.alcance());
        List<AlertScopeItemEntity> desiredScope =
                newScope == null ? null : alertMapper.toScopeItems(id, newScope, now);
        boolean scopeChanged = desiredScope != null
                && !keys(desiredScope, this::scopeKey).equals(keys(currentScope, this::scopeKey));

        List<RecipientRequest> newRecipients =
                request.destinatarios() == null ? null : alertMapper.normalizeRecipients(request.destinatarios());
        List<AlertRecipientEntity> desiredRecipients =
                newRecipients == null ? null : alertMapper.toRecipients(id, newRecipients, now);
        boolean recipientsChanged = desiredRecipients != null
                && !keys(desiredRecipients, this::recipientKey).equals(keys(currentRecipients, this::recipientKey));

        if (scopeChanged) {
            validateScope(newScope);
        }
        if (recipientsChanged) {
            validateRecipients(newRecipients);
        }

        List<String> changedFields = new ArrayList<>();
        if (request.tipo() != null && request.tipo() != alert.getType()) {
            alert.setType(request.tipo());
            changedFields.add("tipo");
        }
        if (request.instrucciones() != null && !request.instrucciones().strip().equals(alert.getInstructions())) {
            alert.setInstructions(request.instrucciones().strip());
            changedFields.add("instrucciones");
        }
        if (scopeChanged) {
            changedFields.add("alcance");
        }
        if (recipientsChanged) {
            changedFields.add("destinatarios");
        }

        if (changedFields.isEmpty()) {
            return alertMapper.toResponse(alert, currentScope, currentRecipients);
        }

        alert.setUpdatedAt(now);
        alertRepository.saveAndFlush(alert);

        if (scopeChanged) {
            replace(currentScope, desiredScope, this::scopeKey, scopeItemRepository);
        }
        if (recipientsChanged) {
            replace(currentRecipients, desiredRecipients, this::recipientKey, recipientRepository);
        }

        auditService.record(
                id,
                AuditAction.UPDATE,
                actor,
                alert.getStatus(),
                alert.getStatus(),
                "Campos modificados: " + String.join(", ", changedFields));

        return alertMapper.toResponse(
                alert, scopeItemRepository.findByAlertId(id), recipientRepository.findByAlertId(id));
    }

    @Override
    @Transactional(readOnly = true)
    public AlertResponse findById(UUID id, AccessActor actor) {
        AlertEntity alert = alertRepository.findById(id).orElseThrow(() -> new AlertNotFoundException(id));
        List<AlertScopeItemEntity> scopeItems = scopeItemRepository.findByAlertId(id);
        List<AlertRecipientEntity> recipients = recipientRepository.findByAlertId(id);
        accessPolicy.assertCanView(alert, actor, recipients);
        return alertMapper.toResponse(alert, scopeItems, recipients);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AlertSummaryResponse> search(AlertFilter filter) {
        int page = filter.page() == null ? DEFAULT_PAGE : filter.page();
        int size = filter.size() == null ? DEFAULT_PAGE_SIZE : filter.size();
        validatePagination(filter, page, size);

        Specification<AlertEntity> specification = AlertSpecifications.fromFilter(filter, clock.getZone());
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<AlertEntity> result = alertRepository.findAll(specification, pageable);

        List<UUID> ids = result.getContent().stream().map(AlertEntity::getId).toList();
        Map<UUID, List<AlertScopeItemEntity>> scopeByAlert = ids.isEmpty()
                ? Map.of()
                : scopeItemRepository.findByAlertIdIn(ids).stream()
                        .collect(Collectors.groupingBy(AlertScopeItemEntity::getAlertId));

        List<AlertSummaryResponse> content = result.getContent().stream()
                .map(alert -> alertMapper.toSummary(alert, scopeByAlert.getOrDefault(alert.getId(), List.of())))
                .toList();
        return PageResponse.of(content, result);
    }

    private void validatePagination(AlertFilter filter, int page, int size) {
        if (page < 0) {
            throw new InvalidPaginationException("page", "La página no puede ser negativa");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidPaginationException("size", "El tamaño de página debe estar entre 1 y " + MAX_PAGE_SIZE);
        }
        if (filter.fechaInicio() != null && filter.fechaFin() != null && filter.fechaInicio().isAfter(filter.fechaFin())) {
            throw new InvalidPaginationException("fechaInicio", "La fecha de inicio no puede ser posterior a la fecha de fin");
        }
    }

    private void validateScope(ScopeRequest scope) {
        if (scope.tipo() != ScopeType.CAMPUS) {
            catalogClient.validateBuildingsZones(scope.tipo(), scope.ids());
        }
    }

    private void validateRecipients(List<RecipientRequest> recipients) {
        catalogClient.validateRecipients(recipients);
    }

    private <T> void replace(List<T> current, List<T> desired, Function<T, String> keyOf, JpaRepository<T, UUID> repository) {
        Set<String> currentKeys = keys(current, keyOf);
        Set<String> desiredKeys = keys(desired, keyOf);
        List<T> toRemove = current.stream().filter(item -> !desiredKeys.contains(keyOf.apply(item))).toList();
        List<T> toAdd = desired.stream().filter(item -> !currentKeys.contains(keyOf.apply(item))).toList();
        repository.deleteAll(toRemove);
        repository.flush();
        repository.saveAll(toAdd);
    }

    private <T> Set<String> keys(List<T> items, Function<T, String> keyOf) {
        return items.stream().map(keyOf).collect(Collectors.toSet());
    }

    private String scopeKey(AlertScopeItemEntity item) {
        return item.getScopeType() + "|" + Objects.toString(item.getScopeRefId(), "");
    }

    private String recipientKey(AlertRecipientEntity recipient) {
        return recipient.getRecipientType() + "|" + recipient.getRecipientRefId();
    }
}
