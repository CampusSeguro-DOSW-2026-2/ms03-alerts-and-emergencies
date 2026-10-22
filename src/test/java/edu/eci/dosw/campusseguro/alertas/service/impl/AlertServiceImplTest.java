package edu.eci.dosw.campusseguro.alertas.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertType;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AuditAction;
import edu.eci.dosw.campusseguro.alertas.entity.enums.ScopeType;
import edu.eci.dosw.campusseguro.alertas.exception.AlertNotFoundException;
import edu.eci.dosw.campusseguro.alertas.exception.InvalidPaginationException;
import edu.eci.dosw.campusseguro.alertas.mapper.AlertMapper;
import edu.eci.dosw.campusseguro.alertas.repository.AlertRecipientRepository;
import edu.eci.dosw.campusseguro.alertas.repository.AlertRepository;
import edu.eci.dosw.campusseguro.alertas.repository.AlertScopeItemRepository;
import edu.eci.dosw.campusseguro.alertas.security.AccessActor;
import edu.eci.dosw.campusseguro.alertas.security.AlertAccessPolicy;
import edu.eci.dosw.campusseguro.alertas.service.AlertLifecycleService;
import edu.eci.dosw.campusseguro.alertas.service.AlertStateRules;
import edu.eci.dosw.campusseguro.alertas.service.AuditService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

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
    private AlertLifecycleService alertLifecycleService;

    private AlertServiceImpl service;

    @BeforeEach
    void setUp() {
        PlatformTransactionManager transactionManager = new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        };
        service = new AlertServiceImpl(
                alertRepository,
                scopeItemRepository,
                recipientRepository,
                catalogClient,
                auditService,
                alertMapper,
                stateRules,
                accessPolicy,
                alertLifecycleService,
                Clock.fixed(NOW, ZoneOffset.UTC),
                transactionManager);
    }

    // some functions to avoid repeating code
    private AlertEntity alert(AlertStatus status, String instructions) {
        AlertEntity alert = new AlertEntity();
        ReflectionTestUtils.setField(alert, "id", ID);
        alert.setStatus(status);
        alert.setType(AlertType.EARTHQUAKE);
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
        when(scope.tipo()).thenReturn(type);
        return scope;
    }

    // Mockito returns 0 (not null) for unstubbed Integer getters, so page/size must be stubbed explicitly
    private AlertFilter filterWith(Integer page, Integer size) {
        AlertFilter filter = mock(AlertFilter.class);
        when(filter.page()).thenReturn(page);
        when(filter.size()).thenReturn(size);
        return filter;
    }

    // create

    @Test
    void create_shouldSaveAndAudit_whenScopeIsCampus() {
        AlertCreateRequest request = mock(AlertCreateRequest.class);
        ScopeRequest scope = scopeRequest(ScopeType.CAMPUS);
        List<RecipientRequest> recipients = List.of(mock(RecipientRequest.class));
        AlertEntity saved = alert(AlertStatus.DRAFT, "instrucciones");
        saved.setOrigin(AlertOrigin.MANUAL);
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

        var result = service.create(request, ACTOR);

        assertTrue(result.created());
        assertSame(expected, result.alert());
        verify(catalogClient, never()).validateBuildingsZones(any(), any());
        verify(catalogClient).validateRecipients(recipients);
        verify(auditService).record(
                ID,
                AuditAction.CREATE,
                ACTOR,
                null,
                AlertStatus.DRAFT,
                "Alerta creada: tipo=EARTHQUAKE, origen=MANUAL, alcance=CAMPUS, destinatarios=1");
    }

    @Test
    void create_shouldValidateAgainstCatalog_whenScopeIsBuilding() {
        AlertCreateRequest request = mock(AlertCreateRequest.class);
        ScopeRequest scope = scopeRequest(ScopeType.BUILDING);
        List<RecipientRequest> recipients = List.of(mock(RecipientRequest.class));
        AlertEntity saved = alert(AlertStatus.DRAFT, "instrucciones");

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

    // update

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
        AlertEntity alert = alert(AlertStatus.ACTIVE, "texto");
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
        AlertEntity alert = alert(AlertStatus.DRAFT, "texto");
        AlertUpdateRequest request = mock(AlertUpdateRequest.class); // all fields null
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
        AlertEntity alert = alert(AlertStatus.DRAFT, "texto");
        AlertUpdateRequest request = mock(AlertUpdateRequest.class);
        when(request.instrucciones()).thenReturn("  texto  ");
        when(alertRepository.findForUpdate(ID)).thenReturn(Optional.of(alert));

        service.update(ID, request, ACTOR);

        verify(alertRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void update_shouldNormalizeSaveAndAudit_whenInstructionsChange() {
        AlertEntity alert = alert(AlertStatus.DRAFT, "texto viejo");
        AlertUpdateRequest request = mock(AlertUpdateRequest.class);
        AlertResponse expected = mock(AlertResponse.class);
        when(request.instrucciones()).thenReturn("  texto nuevo  ");
        when(alertRepository.findForUpdate(ID)).thenReturn(Optional.of(alert));
        when(alertMapper.toResponse(eq(alert), anyList(), anyList())).thenReturn(expected);

        AlertResponse result = service.update(ID, request, ACTOR);

        assertSame(expected, result);
        assertEquals("texto nuevo", alert.getInstructions());
        assertEquals(NOW, alert.getUpdatedAt());
        verify(stateRules).assertEditable(alert);
        verify(alertRepository).saveAndFlush(alert);
        verify(auditService).record(
                ID,
                AuditAction.UPDATE,
                ACTOR,
                AlertStatus.DRAFT,
                AlertStatus.DRAFT,
                "Campos modificados: instrucciones");
    }

    @Test
    void update_shouldNotValidateNorReplace_whenScopeIsTheSame() {
        AlertEntity alert = alert(AlertStatus.DRAFT, "texto");
        List<AlertScopeItemEntity> current = List.of(scopeItem(ScopeType.CAMPUS, null));
        AlertUpdateRequest request = mock(AlertUpdateRequest.class);
        ScopeRequest newScope = mock(ScopeRequest.class);
        when(request.alcance()).thenReturn(newScope);
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
        AlertEntity alert = alert(AlertStatus.DRAFT, "texto");
        AlertScopeItemEntity oldItem = scopeItem(ScopeType.CAMPUS, null);
        AlertScopeItemEntity newItem = scopeItem(ScopeType.BUILDING, "B1");
        AlertUpdateRequest request = mock(AlertUpdateRequest.class);
        ScopeRequest newScope = scopeRequest(ScopeType.BUILDING);
        when(request.alcance()).thenReturn(newScope);
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
                "Campos modificados: alcance");
    }

    // findBy

    @Test
    void findById_shouldReturnMappedAlert_whenAlertExists() {
        AlertEntity alert = alert(AlertStatus.DRAFT, "texto");
        AlertResponse expected = mock(AlertResponse.class);
        when(alertRepository.findById(ID)).thenReturn(Optional.of(alert));
        when(alertMapper.toResponse(eq(alert), anyList(), anyList())).thenReturn(expected);

        assertSame(expected, service.findById(ID, new AccessActor("usr-001", java.util.Set.of("STUDENT"))));
    }

    @Test
    void findById_shouldThrowNotFound_whenAlertDoesNotExist() {
        when(alertRepository.findById(ID)).thenReturn(Optional.empty());

        assertThrows(
                AlertNotFoundException.class,
                () -> service.findById(ID, new AccessActor("usr-001", java.util.Set.of("STUDENT"))));
    }

    // search

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
        when(filter.fechaInicio()).thenReturn(LocalDate.of(2026, 10, 20));
        when(filter.fechaFin()).thenReturn(LocalDate.of(2026, 10, 10));
 
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
 
        assertEquals(0, result.contenido().size());
        verify(scopeItemRepository, never()).findByAlertIdIn(any());
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
    void search_shouldGroupScopesByAlert_whenThereAreResults() {
        AlertFilter filter = filterWith(null, null);
        AlertEntity alert = alert(AlertStatus.DRAFT, "texto");
        AlertScopeItemEntity item = scopeItem(ScopeType.CAMPUS, null);
        AlertSummaryResponse summary = mock(AlertSummaryResponse.class);
        Page<AlertEntity> page = new PageImpl<>(List.of(alert), PageRequest.of(0, 20), 1);
        when(alertRepository.findAll(ArgumentMatchers.<Specification<AlertEntity>>any(), any(Pageable.class)))
                .thenReturn(page);
        when(scopeItemRepository.findByAlertIdIn(List.of(ID))).thenReturn(List.of(item));
        when(alertMapper.toSummary(alert, List.of(item))).thenReturn(summary);
 
        PageResponse<AlertSummaryResponse> result = service.search(filter);
 
        assertEquals(1, result.contenido().size());
        assertSame(summary, result.contenido().get(0));
    }
}