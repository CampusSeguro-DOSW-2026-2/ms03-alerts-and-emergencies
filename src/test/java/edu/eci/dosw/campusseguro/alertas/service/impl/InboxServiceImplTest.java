package edu.eci.dosw.campusseguro.alertas.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import edu.eci.dosw.campusseguro.alertas.dto.request.InboxStatus;
import edu.eci.dosw.campusseguro.alertas.dto.request.ScopeRequest;
import edu.eci.dosw.campusseguro.alertas.dto.response.InboxAlertResponse;
import edu.eci.dosw.campusseguro.alertas.dto.response.PageResponse;
import edu.eci.dosw.campusseguro.alertas.entity.AlertConfirmationEntity;
import edu.eci.dosw.campusseguro.alertas.entity.AlertEntity;
import edu.eci.dosw.campusseguro.alertas.entity.AlertScopeItemEntity;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertOrigin;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertType;
import edu.eci.dosw.campusseguro.alertas.entity.enums.RecipientType;
import edu.eci.dosw.campusseguro.alertas.entity.enums.ScopeType;
import edu.eci.dosw.campusseguro.alertas.exception.InvalidPaginationException;
import edu.eci.dosw.campusseguro.alertas.mapper.AlertMapper;
import edu.eci.dosw.campusseguro.alertas.repository.AlertConfirmationRepository;
import edu.eci.dosw.campusseguro.alertas.repository.AlertRepository;
import edu.eci.dosw.campusseguro.alertas.repository.AlertScopeItemRepository;
import edu.eci.dosw.campusseguro.alertas.security.AccessActor;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InboxServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-15T18:00:00Z");
    private static final UUID ALERT_ID =
            UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final AccessActor ACTOR =
            new AccessActor("usr-001", Set.of("ESTUDIANTE", "PROFESOR"));

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private AlertScopeItemRepository scopeItemRepository;

    @Mock
    private AlertConfirmationRepository confirmationRepository;

    @Mock
    private AlertMapper alertMapper;

    private InboxServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new InboxServiceImpl(
                alertRepository,
                scopeItemRepository,
                confirmationRepository,
                alertMapper);
    }

    private AlertEntity alert(
            AlertStatus status,
            AlertOrigin origin,
            Instant activatedAt) {

        AlertEntity alert = new AlertEntity();
        ReflectionTestUtils.setField(alert, "id", ALERT_ID);
        alert.setTypes(Set.of(AlertType.FIRE, AlertType.EARTHQUAKE));
        alert.setTitle("Evacuar");
        alert.setInstructions("Salir por la escalera");
        alert.setStatus(status);
        alert.setOrigin(origin);
        alert.setActivatedAt(activatedAt);
        alert.setArchivedAt(status == AlertStatus.ARCHIVED ? NOW : null);
        return alert;
    }

    private Page<AlertEntity> page(
            Pageable pageable,
            AlertEntity alert,
            long total) {

        return new PageImpl<>(List.of(alert), pageable, total);
    }

    private AlertConfirmationEntity confirmation() {
        AlertConfirmationEntity confirmation = new AlertConfirmationEntity();
        confirmation.setAlertId(ALERT_ID);
        confirmation.setUserId(ACTOR.userId());
        confirmation.setConfirmedAt(NOW);
        return confirmation;
    }

    private AlertScopeItemEntity scopeItem() {
        AlertScopeItemEntity item = new AlertScopeItemEntity();
        item.setAlertId(ALERT_ID);
        item.setScopeType(ScopeType.CAMPUS);
        return item;
    }

    @Test
    void inbox_shouldUseActivePageZeroAndSizeTwentyByDefault() {
        AlertEntity alert = alert(AlertStatus.ACTIVE, AlertOrigin.MANUAL, NOW);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(alertRepository.findInbox(
                eq(ACTOR.userId()),
                eq(ACTOR.roles()),
                anyCollection(),
                eq(RecipientType.USER),
                eq(RecipientType.ROLE),
                any(Pageable.class)))
                .thenAnswer(invocation -> page(
                        invocation.getArgument(5, Pageable.class),
                        alert,
                        1));

        PageResponse<InboxAlertResponse> response =
                service.inbox(null, null, null, ACTOR);

        assertEquals(0, response.page());
        assertEquals(20, response.size());
        assertEquals(1, response.totalElementos());
        assertEquals(1, response.contenido().size());

        verify(alertRepository).findInbox(
                eq(ACTOR.userId()),
                eq(ACTOR.roles()),
                eq(EnumSet.of(AlertStatus.ACTIVE)),
                eq(RecipientType.USER),
                eq(RecipientType.ROLE),
                pageableCaptor.capture());

        Sort sort = pageableCaptor.getValue().getSort();
        assertEquals(
                List.of(
                        Sort.Order.desc("activatedAt").nullsLast(),
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")),
                sort.toList());
    }

    @Test
    void inbox_shouldRequestArchivedStatus() {
        AlertEntity alert = alert(AlertStatus.ARCHIVED, AlertOrigin.MANUAL, NOW);
        when(alertRepository.findInbox(
                eq(ACTOR.userId()),
                anySet(),
                anyCollection(),
                any(),
                any(),
                any(Pageable.class)))
                .thenAnswer(invocation -> page(
                        invocation.getArgument(5, Pageable.class),
                        alert,
                        1));

        service.inbox(InboxStatus.ARCHIVED, 0, 20, ACTOR);

        verify(alertRepository).findInbox(
                eq(ACTOR.userId()),
                eq(ACTOR.roles()),
                eq(EnumSet.of(AlertStatus.ARCHIVED)),
                eq(RecipientType.USER),
                eq(RecipientType.ROLE),
                any(Pageable.class));
    }

    @Test
    void inbox_shouldRequestActiveAndArchivedForAll() {
        AlertEntity alert = alert(AlertStatus.ACTIVE, AlertOrigin.MANUAL, NOW);
        when(alertRepository.findInbox(
                eq(ACTOR.userId()),
                anySet(),
                anyCollection(),
                any(),
                any(),
                any(Pageable.class)))
                .thenAnswer(invocation -> page(
                        invocation.getArgument(5, Pageable.class),
                        alert,
                        1));

        service.inbox(InboxStatus.ALL, 0, 20, ACTOR);

        verify(alertRepository).findInbox(
                eq(ACTOR.userId()),
                eq(ACTOR.roles()),
                eq(EnumSet.of(AlertStatus.ACTIVE, AlertStatus.ARCHIVED)),
                eq(RecipientType.USER),
                eq(RecipientType.ROLE),
                any(Pageable.class));
    }

    @Test
    void inbox_shouldUseSentinelRole_whenActorHasNoRoles() {
        AccessActor actor = new AccessActor("usr-002", Set.of());
        AlertEntity alert = alert(AlertStatus.ACTIVE, AlertOrigin.MANUAL, NOW);
        when(alertRepository.findInbox(
                eq(actor.userId()),
                anySet(),
                anyCollection(),
                any(),
                any(),
                any(Pageable.class)))
                .thenAnswer(invocation -> page(
                        invocation.getArgument(5, Pageable.class),
                        alert,
                        1));

        service.inbox(InboxStatus.ACTIVE, 0, 20, actor);

        verify(alertRepository).findInbox(
                eq(actor.userId()),
                eq(Set.of("__NO_ROLE__")),
                eq(EnumSet.of(AlertStatus.ACTIVE)),
                eq(RecipientType.USER),
                eq(RecipientType.ROLE),
                any(Pageable.class));
    }

    @Test
    void inbox_shouldMapScopeTypesAndConfirmation() {
        AlertEntity alert = alert(AlertStatus.ACTIVE, AlertOrigin.SIMULATION, NOW);
        AlertScopeItemEntity scopeItem = scopeItem();
        ScopeRequest scope = new ScopeRequest(ScopeType.CAMPUS, List.of());
        AlertConfirmationEntity confirmation = confirmation();
        when(alertRepository.findInbox(
                eq(ACTOR.userId()),
                anySet(),
                anyCollection(),
                any(),
                any(),
                any(Pageable.class)))
                .thenAnswer(invocation -> page(
                        invocation.getArgument(5, Pageable.class),
                        alert,
                        1));
        when(scopeItemRepository.findByAlertIdIn(List.of(ALERT_ID)))
                .thenReturn(List.of(scopeItem));
        when(confirmationRepository.findByUserIdAndAlertIdIn(
                ACTOR.userId(),
                List.of(ALERT_ID)))
                .thenReturn(List.of(confirmation));
        when(alertMapper.sortedTypes(alert.getTypes()))
                .thenReturn(List.of(AlertType.EARTHQUAKE, AlertType.FIRE));
        when(alertMapper.toScope(List.of(scopeItem))).thenReturn(scope);

        InboxAlertResponse response =
                service.inbox(InboxStatus.ACTIVE, 0, 20, ACTOR)
                        .contenido()
                        .get(0);

        assertEquals(ALERT_ID, response.id());
        assertEquals(
                List.of(AlertType.EARTHQUAKE, AlertType.FIRE),
                response.types());
        assertEquals("Evacuar", response.title());
        assertEquals(AlertStatus.ACTIVE, response.status());
        assertEquals(AlertOrigin.SIMULATION, response.origin());
        assertEquals(scope, response.scope());
        assertEquals("Salir por la escalera", response.instructions());
        assertEquals(NOW, response.activatedAt());
        assertNull(response.archivedAt());
        assertEquals(NOW, response.confirmedAt());
    }

    @Test
    void inbox_shouldLeaveConfirmationNull_whenActorHasNotConfirmed() {
        AlertEntity alert = alert(AlertStatus.ACTIVE, AlertOrigin.MANUAL, NOW);
        when(alertRepository.findInbox(
                eq(ACTOR.userId()),
                anySet(),
                anyCollection(),
                any(),
                any(),
                any(Pageable.class)))
                .thenAnswer(invocation -> page(
                        invocation.getArgument(5, Pageable.class),
                        alert,
                        1));
        when(scopeItemRepository.findByAlertIdIn(List.of(ALERT_ID)))
                .thenReturn(List.of());
        when(confirmationRepository.findByUserIdAndAlertIdIn(
                ACTOR.userId(),
                List.of(ALERT_ID)))
                .thenReturn(List.of());
        when(alertMapper.sortedTypes(alert.getTypes()))
                .thenReturn(List.of(AlertType.EARTHQUAKE, AlertType.FIRE));

        InboxAlertResponse response =
                service.inbox(InboxStatus.ACTIVE, 0, 20, ACTOR)
                        .contenido()
                        .get(0);

        assertNull(response.confirmedAt());
        assertNull(response.scope());
    }

    @Test
    void inbox_shouldAvoidBatchQueries_whenPageIsEmpty() {
        when(alertRepository.findInbox(
                eq(ACTOR.userId()),
                anySet(),
                anyCollection(),
                any(),
                any(),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(
                        List.of(),
                        PageRequest.of(0, 20),
                        0));

        PageResponse<InboxAlertResponse> response =
                service.inbox(InboxStatus.ACTIVE, 0, 20, ACTOR);

        assertTrue(response.contenido().isEmpty());
        verifyNoInteractions(scopeItemRepository, confirmationRepository);
    }

    @Test
    void inbox_shouldRejectNegativePage() {
        assertThrows(
                InvalidPaginationException.class,
                () -> service.inbox(InboxStatus.ACTIVE, -1, 20, ACTOR));

        verifyNoInteractions(
                alertRepository,
                scopeItemRepository,
                confirmationRepository);
    }

    @Test
    void inbox_shouldRejectSizeGreaterThanMaximum() {
        assertThrows(
                InvalidPaginationException.class,
                () -> service.inbox(InboxStatus.ACTIVE, 0, 101, ACTOR));

        verifyNoInteractions(
                alertRepository,
                scopeItemRepository,
                confirmationRepository);
    }

    @Test
    void inbox_shouldRejectNonPositiveSize() {
        assertThrows(
                InvalidPaginationException.class,
                () -> service.inbox(InboxStatus.ACTIVE, 0, 0, ACTOR));

        verifyNoInteractions(
                alertRepository,
                scopeItemRepository,
                confirmationRepository);
    }
}
