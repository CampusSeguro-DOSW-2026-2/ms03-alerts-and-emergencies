package edu.eci.dosw.campusseguro.alertas.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import edu.eci.dosw.campusseguro.alertas.client.catalog.CatalogClient;
import edu.eci.dosw.campusseguro.alertas.dto.request.AlertCreateRequest;
import edu.eci.dosw.campusseguro.alertas.dto.request.AlertFilter;
import edu.eci.dosw.campusseguro.alertas.dto.request.AlertOriginRequest;
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
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertType;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AuditAction;
import edu.eci.dosw.campusseguro.alertas.entity.enums.RecipientType;
import edu.eci.dosw.campusseguro.alertas.entity.enums.ScopeType;
import edu.eci.dosw.campusseguro.alertas.exception.AlertNotFoundException;
import edu.eci.dosw.campusseguro.alertas.exception.DuplicateSimulationAlertException;
import edu.eci.dosw.campusseguro.alertas.exception.InvalidPaginationException;
import edu.eci.dosw.campusseguro.alertas.mapper.AlertMapper;
import edu.eci.dosw.campusseguro.alertas.repository.AlertRecipientRepository;
import edu.eci.dosw.campusseguro.alertas.repository.AlertRepository;
import edu.eci.dosw.campusseguro.alertas.repository.AlertScopeItemRepository;
import edu.eci.dosw.campusseguro.alertas.security.AccessActor;
import edu.eci.dosw.campusseguro.alertas.security.AlertAccessPolicy;
import edu.eci.dosw.campusseguro.alertas.service.AlertCreationResult;
import edu.eci.dosw.campusseguro.alertas.service.AlertLifecycleService;
import edu.eci.dosw.campusseguro.alertas.service.AlertStateRules;
import edu.eci.dosw.campusseguro.alertas.service.AuditService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AlertServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-15T18:00:00Z");
    private static final UUID ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final String ACTOR = "admin";

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private AlertScopeItemRepository scopeItemRepository;

    @Mock
    private AlertRecipientRepository recipientRepository;

    @Mock
    private CatalogClient catalogClient;

    @Mock
    private AuditService auditService;

    @Mock
    private AlertMapper alertMapper;

    @Mock
    private AlertStateRules stateRules;

    @Mock
    private AlertAccessPolicy accessPolicy;

    @Mock
    private AlertLifecycleService lifecycleService;

    private AlertServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AlertServiceImpl(
                alertRepository,
                scopeItemRepository,
                recipientRepository,
                catalogClient,
                auditService,
                alertMapper,
                stateRules,
                accessPolicy,
                lifecycleService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private AlertEntity alert(AlertStatus status, String instructions) {
        AlertEntity alert = new AlertEntity();
        ReflectionTestUtils.setField(alert, "id", ID);
        alert.setStatus(status);
        alert.setType(AlertType.EARTHQUAKE);
        alert.setTitle("Simulacro");
        alert.setInstructions(instructions);
        return alert;
    }

    private AlertScopeItemEntity scopeItem(ScopeType type, String refId) {
        AlertScopeItemEntity item = new AlertScopeItemEntity();
        item.setAlertId(ID);
        item.setScopeType(type);
        item.setScopeRefId(refId);
        return item;
    }

    private ScopeRequest scopeRequest(ScopeType type) {
        ScopeRequest scope = mock(ScopeRequest.class);
        when(scope.type()).thenReturn(type);
        return scope;
    }

    private AlertFilter filterWith(Integer page, Integer size) {
        AlertFilter filter = mock(AlertFilter.class);
        when(filter.page()).thenReturn(page);
        when(filter.size()).thenReturn(size);
        return filter;
    }

    @Test
    void create_shouldSaveAndAudit_whenScopeIsCampus() {
        AlertCreateRequest request = mock(AlertCreateRequest.class);
        ScopeRequest scope = scopeRequest(ScopeType.CAMPUS);
        List<RecipientRequest> recipients = List.of(mock(RecipientRequest.class));
        AlertEntity saved = alert(AlertStatus.DRAFT, "instructions");
        List<AlertScopeItemEntity> scopeItems = List.of(scopeItem(ScopeType.CAMPUS, null));
        List<AlertRecipientEntity> recipientEntities = List.of(new AlertRecipientEntity());
        AlertResponse expected = mock(AlertResponse.class);

        when(alertMapper.normalizeScope(any())).thenReturn(scope);
        when(alertMapper.normalizeRecipients(any())).thenReturn(recipients);
        when(alertMapper.toEntity(request, ACTOR, NOW)).thenReturn(saved);
        when(alertRepository.saveAndFlush(saved)).thenReturn(saved);
        when(alertMapper.toScopeItems(ID, scope, NOW)).thenReturn(scopeItems);
        when(alertMapper.toRecipients(ID, recipients, NOW)).thenReturn(recipientEntities);
        when(scopeItemRepository.saveAll(scopeItems)).thenReturn(scopeItems);
        when(recipientRepository.saveAll(recipientEntities)).thenReturn(recipientEntities);
        when(alertMapper.toResponse(saved, scopeItems, recipientEntities)).thenReturn(expected);

        AlertCreationResult result = service.create(request, ACTOR);

        assertSame(expected, result.alert());
        assertTrue(result.created());
        verify(catalogClient, never()).validateBuildingsZones(any(), any());
        verify(catalogClient).validateRecipients(recipients);
        verify(auditService).record(
                ID,
                AuditAction.CREATE,
                ACTOR,
                null,
                AlertStatus.DRAFT,
                "Alert created: title=Simulacro, type=EARTHQUAKE, origin=null, "
                        + "simulationId=null, scope=CAMPUS, recipients=1");
    }

    @Test
    void create_withSameSimulationIdAndSameData_shouldReturnExistingAlert() {
        UUID simulationId = UUID.randomUUID();
        ScopeRequest scope = new ScopeRequest(ScopeType.CAMPUS, List.of());
        AlertEntity existing = alert(AlertStatus.SCHEDULED, "Evacuar");
        existing.setSimulationId(simulationId);
        existing.setActivationAt(NOW.plusSeconds(3600));

        AlertCreateRequest request = simulationRequest(
                simulationId,
                " Simulacro ",
                " Evacuar ",
                NOW.plusSeconds(3600));

        AlertResponse expected = mock(AlertResponse.class);
        when(expected.scope()).thenReturn(scope);
        when(expected.recipients()).thenReturn(List.of());
        when(alertMapper.normalizeScope(any())).thenReturn(scope);
        when(alertMapper.normalizeRecipients(any())).thenReturn(List.of());
        when(alertRepository.findFirstBySimulationIdAndStatusInOrderByCreatedAtDesc(
                eq(simulationId), any())).thenReturn(Optional.of(existing));
        when(alertMapper.toResponse(eq(existing), any(), any()))
                .thenReturn(expected);

        AlertCreationResult result = service.create(request, ACTOR);

        assertSame(expected, result.alert());
        assertFalse(result.created());
        verify(alertRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_withSameSimulationIdWhileDraft_shouldReturnExistingAlert() {
        UUID simulationId = UUID.randomUUID();
        ScopeRequest scope = new ScopeRequest(ScopeType.CAMPUS, List.of());
        AlertEntity existing = alert(AlertStatus.DRAFT, "Evacuar");
        existing.setSimulationId(simulationId);

        AlertCreateRequest request = simulationRequest(
                simulationId,
                "Simulacro",
                "Evacuar",
                null);

        AlertResponse expected = mock(AlertResponse.class);
        when(expected.scope()).thenReturn(scope);
        when(expected.recipients()).thenReturn(List.of());
        when(alertMapper.normalizeScope(any())).thenReturn(scope);
        when(alertMapper.normalizeRecipients(any())).thenReturn(List.of());
        when(alertRepository.findFirstBySimulationIdAndStatusInOrderByCreatedAtDesc(
                eq(simulationId),
                argThat(statuses -> statuses.contains(AlertStatus.DRAFT))))
                .thenReturn(Optional.of(existing));
        when(alertMapper.toResponse(eq(existing), any(), any()))
                .thenReturn(expected);

        AlertCreationResult result = service.create(request, ACTOR);

        assertSame(expected, result.alert());
        assertFalse(result.created());
        verify(alertRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_withSameSimulationIdAndDifferentData_shouldThrowDuplicate() {
        UUID simulationId = UUID.randomUUID();
        ScopeRequest scope = new ScopeRequest(ScopeType.CAMPUS, List.of());
        AlertEntity existing = alert(AlertStatus.ACTIVE, "Evacuar");
        existing.setSimulationId(simulationId);

        AlertCreateRequest request = simulationRequest(
                simulationId,
                "Simulacro",
                "Otra instruccion",
                null);

        AlertResponse current = mock(AlertResponse.class);
        when(alertMapper.normalizeScope(any())).thenReturn(scope);
        when(alertMapper.normalizeRecipients(any())).thenReturn(List.of());
        when(alertRepository.findFirstBySimulationIdAndStatusInOrderByCreatedAtDesc(
                eq(simulationId), any())).thenReturn(Optional.of(existing));
        when(alertMapper.toResponse(eq(existing), any(), any()))
                .thenReturn(current);

        assertThrows(
                DuplicateSimulationAlertException.class,
                () -> service.create(request, ACTOR));

        verify(alertRepository, never()).saveAndFlush(any());
    }

    private AlertCreateRequest simulationRequest(
            UUID simulationId,
            String title,
            String instructions,
            Instant activationAt) {

        AlertCreateRequest request = mock(AlertCreateRequest.class);
        when(request.origin()).thenReturn(
                new AlertOriginRequest(AlertOrigin.SIMULATION, simulationId));
        when(request.type()).thenReturn(AlertType.EARTHQUAKE);
        when(request.title()).thenReturn(title);
        when(request.instructions()).thenReturn(instructions);
        lenient().when(request.activationAt()).thenReturn(activationAt);
        return request;
    }

    @Test
    void create_shouldValidateAgainstCatalog_whenScopeIsBuilding() {
        AlertCreateRequest request = mock(AlertCreateRequest.class);
        ScopeRequest scope = scopeRequest(ScopeType.BUILDING);
        List<RecipientRequest> recipients = List.of(mock(RecipientRequest.class));
        AlertEntity saved = alert(AlertStatus.DRAFT, "instructions");

        when(alertMapper.normalizeScope(any())).thenReturn(scope);
        when(alertMapper.normalizeRecipients(any())).thenReturn(recipients);
        when(alertMapper.toEntity(request, ACTOR, NOW)).thenReturn(saved);
        when(alertRepository.saveAndFlush(saved)).thenReturn(saved);

        service.create(request, ACTOR);

        verify(catalogClient).validateBuildingsZones(eq(ScopeType.BUILDING), any());
        verify(catalogClient).validateRecipients(recipients);
    }

    @Test
    void create_shouldNotPersistAnything_whenCatalogRejectsScope() {
        AlertCreateRequest request = mock(AlertCreateRequest.class);
        ScopeRequest scope = scopeRequest(ScopeType.ZONE);
        when(alertMapper.normalizeScope(any())).thenReturn(scope);
        when(alertMapper.normalizeRecipients(any())).thenReturn(List.of());
        doThrow(new RuntimeException("invalid scope"))
                .when(catalogClient)
                .validateBuildingsZones(eq(ScopeType.ZONE), any());

        assertThrows(RuntimeException.class, () -> service.create(request, ACTOR));

        verify(alertRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void create_shouldNotPersistAnything_whenCatalogRejectsRecipients() {
        AlertCreateRequest request = mock(AlertCreateRequest.class);
        ScopeRequest scope = scopeRequest(ScopeType.CAMPUS);
        List<RecipientRequest> recipients = List.of(mock(RecipientRequest.class));
        when(alertMapper.normalizeScope(any())).thenReturn(scope);
        when(alertMapper.normalizeRecipients(any())).thenReturn(recipients);
        doThrow(new RuntimeException("invalid recipients"))
                .when(catalogClient)
                .validateRecipients(recipients);

        assertThrows(RuntimeException.class, () -> service.create(request, ACTOR));

        verify(alertRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void update_shouldThrowNotFound_whenAlertDoesNotExist() {
        when(alertRepository.findForUpdate(ID)).thenReturn(Optional.empty());

        assertThrows(
                AlertNotFoundException.class,
                () -> service.update(ID, mock(AlertUpdateRequest.class), ACTOR));

        verifyNoInteractions(stateRules, auditService);
    }

    @Test
    void update_shouldPropagateError_whenAlertIsNotEditable() {
        AlertEntity alert = alert(AlertStatus.ACTIVE, "text");
        when(alertRepository.findForUpdate(ID)).thenReturn(Optional.of(alert));
        doThrow(new IllegalStateException("not editable")).when(stateRules).assertEditable(alert);

        assertThrows(
                IllegalStateException.class,
                () -> service.update(ID, mock(AlertUpdateRequest.class), ACTOR));

        verify(alertRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void update_shouldNotSaveNorAudit_whenThereAreNoChanges() {
        AlertEntity alert = alert(AlertStatus.DRAFT, "text");
        AlertUpdateRequest request = mock(AlertUpdateRequest.class);
        AlertResponse expected = mock(AlertResponse.class);
        when(alertRepository.findForUpdate(ID)).thenReturn(Optional.of(alert));
        when(alertMapper.toResponse(eq(alert), anyList(), anyList())).thenReturn(expected);

        AlertResponse result = service.update(ID, request, ACTOR);

        assertSame(expected, result);
        verify(alertRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void update_shouldNotCountAsChange_whenInstructionsAreEqual() {
        AlertEntity alert = alert(AlertStatus.DRAFT, "text");
        AlertUpdateRequest request = mock(AlertUpdateRequest.class);
        when(request.instructions()).thenReturn("  text  ");
        when(alertRepository.findForUpdate(ID)).thenReturn(Optional.of(alert));

        service.update(ID, request, ACTOR);

        verify(alertRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void update_shouldNormalizeSaveAndAudit_whenInstructionsChange() {
        AlertEntity alert = alert(AlertStatus.DRAFT, "old text");
        AlertUpdateRequest request = mock(AlertUpdateRequest.class);
        AlertResponse expected = mock(AlertResponse.class);
        when(request.instructions()).thenReturn("  new text  ");
        when(alertRepository.findForUpdate(ID)).thenReturn(Optional.of(alert));
        when(alertMapper.toResponse(eq(alert), anyList(), anyList())).thenReturn(expected);

        AlertResponse result = service.update(ID, request, ACTOR);

        assertSame(expected, result);
        assertEquals("new text", alert.getInstructions());
        assertEquals(NOW, alert.getUpdatedAt());
        verify(stateRules).assertEditable(alert);
        verify(alertRepository).saveAndFlush(alert);
        verify(auditService).record(
                ID,
                AuditAction.UPDATE,
                ACTOR,
                AlertStatus.DRAFT,
                AlertStatus.DRAFT,
                "Modified fields: instructions");
    }

    @Test
    void update_shouldNotValidateNorReplace_whenScopeIsTheSame() {
        AlertEntity alert = alert(AlertStatus.DRAFT, "text");
        List<AlertScopeItemEntity> current = List.of(scopeItem(ScopeType.CAMPUS, null));
        AlertUpdateRequest request = mock(AlertUpdateRequest.class);
        ScopeRequest newScope = mock(ScopeRequest.class);
        when(request.scope()).thenReturn(newScope);
        when(alertRepository.findForUpdate(ID)).thenReturn(Optional.of(alert));
        when(scopeItemRepository.findByAlertId(ID)).thenReturn(current);
        when(alertMapper.normalizeScope(newScope)).thenReturn(newScope);
        when(alertMapper.toScopeItems(ID, newScope, NOW)).thenReturn(List.of(scopeItem(ScopeType.CAMPUS, null)));

        service.update(ID, request, ACTOR);

        verify(catalogClient, never()).validateBuildingsZones(any(), any());
        verify(scopeItemRepository, never()).deleteAll(ArgumentMatchers.<List<AlertScopeItemEntity>>any());
        verifyNoInteractions(auditService);
    }

    @Test
    void update_shouldValidateAndReplaceItems_whenScopeChanges() {
        AlertEntity alert = alert(AlertStatus.DRAFT, "text");
        AlertScopeItemEntity oldItem = scopeItem(ScopeType.CAMPUS, null);
        AlertScopeItemEntity newItem = scopeItem(ScopeType.BUILDING, "B1");
        AlertUpdateRequest request = mock(AlertUpdateRequest.class);
        ScopeRequest newScope = scopeRequest(ScopeType.BUILDING);
        when(request.scope()).thenReturn(newScope);
        when(alertRepository.findForUpdate(ID)).thenReturn(Optional.of(alert));
        when(scopeItemRepository.findByAlertId(ID)).thenReturn(List.of(oldItem));
        when(alertMapper.normalizeScope(newScope)).thenReturn(newScope);
        when(alertMapper.toScopeItems(ID, newScope, NOW)).thenReturn(List.of(newItem));

        service.update(ID, request, ACTOR);

        verify(catalogClient).validateBuildingsZones(eq(ScopeType.BUILDING), any());
        verify(scopeItemRepository).deleteAll(List.of(oldItem));
        verify(scopeItemRepository).flush();
        verify(scopeItemRepository).saveAll(List.of(newItem));
        verify(alertRepository).saveAndFlush(alert);
        verify(auditService).record(
                ID,
                AuditAction.UPDATE,
                ACTOR,
                AlertStatus.DRAFT,
                AlertStatus.DRAFT,
                "Modified fields: scope");
    }

    @Test
    void findById_shouldReturnMappedAlert_whenAlertExists() {
        AlertEntity alert = alert(AlertStatus.DRAFT, "text");
        AlertResponse expected = mock(AlertResponse.class);
        when(alertRepository.findById(ID)).thenReturn(Optional.of(alert));
        when(alertMapper.toResponse(eq(alert), anyList(), anyList())).thenReturn(expected);

        assertSame(expected,
                service.findById(ID, new AccessActor("viewer", Set.of())));
    }

    @Test
    void findById_shouldThrowNotFound_whenAlertDoesNotExist() {
        when(alertRepository.findById(ID)).thenReturn(Optional.empty());

        assertThrows(AlertNotFoundException.class,
                () -> service.findById(ID, new AccessActor("viewer", Set.of())));
    }

    @Test
    void search_shouldThrowInvalidPagination_whenPageIsNegative() {
        AlertFilter filter = mock(AlertFilter.class);
        when(filter.page()).thenReturn(-1);

        assertThrows(InvalidPaginationException.class, () -> service.search(filter));

        verifyNoInteractions(alertRepository);
    }

    @Test
    void search_shouldThrowInvalidPagination_whenSizeIsLessThanOne() {
        AlertFilter filter = mock(AlertFilter.class);
        when(filter.size()).thenReturn(0);

        assertThrows(InvalidPaginationException.class, () -> service.search(filter));

        verifyNoInteractions(alertRepository);
    }

    @Test
    void search_shouldThrowInvalidPagination_whenSizeIsGreaterThan100() {
        AlertFilter filter = mock(AlertFilter.class);
        when(filter.size()).thenReturn(101);

        assertThrows(InvalidPaginationException.class, () -> service.search(filter));

        verifyNoInteractions(alertRepository);
    }

    @Test
    void search_shouldThrowInvalidPagination_whenStartDateIsAfterEndDate() {
        AlertFilter filter = filterWith(0, 20);
        when(filter.startDate()).thenReturn(LocalDate.of(2026, 10, 20));
        when(filter.endDate()).thenReturn(LocalDate.of(2026, 10, 10));

        assertThrows(InvalidPaginationException.class, () -> service.search(filter));

        verifyNoInteractions(alertRepository);
    }

    @Test
    void search_shouldNotQueryScopes_whenThereAreNoResults() {
        AlertFilter filter = filterWith(null, null);
        Page<AlertEntity> empty = new PageImpl<>(List.of());
        when(alertRepository.findAll(ArgumentMatchers.<Specification<AlertEntity>>any(), any(Pageable.class)))
                .thenReturn(empty);

        PageResponse<AlertSummaryResponse> result = service.search(filter);

        assertEquals(0, result.content().size());
        verify(scopeItemRepository, never()).findByAlertIdIn(any());
        verify(recipientRepository, never()).findByAlertIdIn(any());
    }

    @Test
    void search_shouldUseDefaultPaginationAndSortByCreatedAtDesc_whenNoPageOrSizeGiven() {
        AlertFilter filter = filterWith(null, null);
        when(alertRepository.findAll(ArgumentMatchers.<Specification<AlertEntity>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.search(filter);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(alertRepository).findAll(ArgumentMatchers.<Specification<AlertEntity>>any(), captor.capture());
        Pageable pageable = captor.getValue();
        assertEquals(0, pageable.getPageNumber());
        assertEquals(20, pageable.getPageSize());
        assertEquals(
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")),
                pageable.getSort());
    }

    @Test
    void search_shouldRespectRequestedPageAndSize_whenValuesAreProvided() {
        AlertFilter filter = mock(AlertFilter.class);
        when(filter.page()).thenReturn(2);
        when(filter.size()).thenReturn(50);
        when(alertRepository.findAll(ArgumentMatchers.<Specification<AlertEntity>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.search(filter);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(alertRepository).findAll(ArgumentMatchers.<Specification<AlertEntity>>any(), captor.capture());
        assertEquals(2, captor.getValue().getPageNumber());
        assertEquals(50, captor.getValue().getPageSize());
    }

    @Test
    void search_shouldGroupScopesAndRecipientsByAlert_whenThereAreResults() {
        AlertFilter filter = filterWith(null, null);
        AlertEntity alert = alert(AlertStatus.DRAFT, "text");
        AlertScopeItemEntity item = scopeItem(ScopeType.CAMPUS, null);
        AlertRecipientEntity recipient = new AlertRecipientEntity();
        recipient.setAlertId(ID);
        recipient.setRecipientType(RecipientType.ROLE);
        recipient.setRecipientRefId("STUDENT");
        AlertSummaryResponse summary = mock(AlertSummaryResponse.class);
        Page<AlertEntity> page = new PageImpl<>(List.of(alert), PageRequest.of(0, 20), 1);
        when(alertRepository.findAll(ArgumentMatchers.<Specification<AlertEntity>>any(), any(Pageable.class)))
                .thenReturn(page);
        when(scopeItemRepository.findByAlertIdIn(List.of(ID))).thenReturn(List.of(item));
        when(recipientRepository.findByAlertIdIn(List.of(ID))).thenReturn(List.of(recipient));
        when(alertMapper.toSummary(alert, List.of(item), List.of(recipient))).thenReturn(summary);

        PageResponse<AlertSummaryResponse> result = service.search(filter);

        assertEquals(1, result.content().size());
        assertSame(summary, result.content().get(0));
    }
}
