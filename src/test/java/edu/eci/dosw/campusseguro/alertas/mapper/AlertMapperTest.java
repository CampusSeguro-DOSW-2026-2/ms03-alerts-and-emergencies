package edu.eci.dosw.campusseguro.alertas.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertType;
import edu.eci.dosw.campusseguro.alertas.entity.enums.RecipientType;
import edu.eci.dosw.campusseguro.alertas.entity.enums.ScopeType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AlertMapperTest {

    private static final Instant NOW = Instant.parse("2026-10-15T18:00:00Z");
    private static final UUID ALERT_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final UUID SIMULATION_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");
    private static final String ACTOR = "admin";
    private static final String TITLE = "Simulacro Sismico";

    private AlertMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new AlertMapper();
    }

    // helpers

    private AlertScopeItemEntity scopeItem(ScopeType type, String refId) {
        AlertScopeItemEntity item = new AlertScopeItemEntity();
        item.setAlertId(ALERT_ID);
        item.setScopeType(type);
        item.setScopeRefId(refId);
        return item;
    }

    private AlertRecipientEntity recipient(RecipientType type, String refId) {
        AlertRecipientEntity entity = new AlertRecipientEntity();
        entity.setAlertId(ALERT_ID);
        entity.setRecipientType(type);
        entity.setRecipientRefId(refId);
        return entity;
    }

    private AlertEntity fullAlert() {
        AlertEntity alert = new AlertEntity();
        ReflectionTestUtils.setField(alert, "id", ALERT_ID);
        alert.setType(AlertType.EARTHQUAKE);
        alert.setTitle(TITLE);
        alert.setStatus(AlertStatus.ACTIVE);
        alert.setOrigin(AlertOrigin.SIMULATION);
        alert.setSimulationId(SIMULATION_ID);
        alert.setInstructions("Evacuar");
        alert.setCreatedBy(ACTOR);
        alert.setCreatedAt(NOW.minusSeconds(300));
        alert.setUpdatedAt(NOW.minusSeconds(200));
        alert.setActivationAt(NOW.minusSeconds(100));
        alert.setActivatedAt(NOW.minusSeconds(90));
        alert.setEndedAt(NOW.minusSeconds(80));
        alert.setArchivedAt(NOW.minusSeconds(70));
        return alert;
    }

    // normalizeScope

    @Test
    void normalizeScope_shouldReturnCampusWithEmptyIds_whenScopeIsCampus() {
        ScopeRequest result = mapper.normalizeScope(new ScopeRequest(ScopeType.CAMPUS, List.of("B1")));

        assertEquals(new ScopeRequest(ScopeType.CAMPUS, List.of()), result);
    }

    @Test
    void normalizeScope_shouldStripAndDeduplicateIds_whenScopeIsBuilding() {
        ScopeRequest result = mapper.normalizeScope(
                new ScopeRequest(ScopeType.BUILDING, List.of(" B1 ", "B2", "B1")));

        assertEquals(new ScopeRequest(ScopeType.BUILDING, List.of("B1", "B2")), result);
    }

    @Test
    void normalizeScope_shouldKeepScopeType_whenScopeIsZone() {
        ScopeRequest result = mapper.normalizeScope(new ScopeRequest(ScopeType.ZONE, List.of("Z1")));

        assertEquals(new ScopeRequest(ScopeType.ZONE, List.of("Z1")), result);
    }

    // normalizeRecipients

    @Test
    void normalizeRecipients_shouldStripIds_whenIdsHaveWhitespace() {
        List<RecipientRequest> result = mapper.normalizeRecipients(
                List.of(new RecipientRequest(RecipientType.USER, "  u1  ")));

        assertEquals(List.of(new RecipientRequest(RecipientType.USER, "u1")), result);
    }

    @Test
    void normalizeRecipients_shouldRemoveDuplicates_whenIdsAreEqualAfterStripping() {
        List<RecipientRequest> result = mapper.normalizeRecipients(List.of(
                new RecipientRequest(RecipientType.USER, "u1"),
                new RecipientRequest(RecipientType.USER, " u1 ")));

        assertEquals(List.of(new RecipientRequest(RecipientType.USER, "u1")), result);
    }

    @Test
    void normalizeRecipients_shouldNotRemoveRecipients_whenSameIdHasDifferentType() {
        List<RecipientRequest> result = mapper.normalizeRecipients(List.of(
                new RecipientRequest(RecipientType.USER, "x"),
                new RecipientRequest(RecipientType.ROLE, "x")));

        assertEquals(2, result.size());
    }

    @Test
    void normalizeRecipients_shouldReturnEmptyList_whenThereAreNoRecipients() {
        assertTrue(mapper.normalizeRecipients(List.of()).isEmpty());
    }

    // toEntity

    @Test
    void toEntity_shouldBuildDraftManualAlert_whenRequestIsValid() {
        AlertCreateRequest request = mock(AlertCreateRequest.class);
        when(request.type()).thenReturn(AlertType.EARTHQUAKE);
        when(request.title()).thenReturn("  " + TITLE + "  ");
        when(request.instructions()).thenReturn("  Evacuar el edificio  ");

        AlertEntity result = mapper.toEntity(request, ACTOR, NOW);

        assertEquals(AlertType.EARTHQUAKE, result.getType());
        assertEquals(TITLE, result.getTitle());
        assertEquals(AlertStatus.DRAFT, result.getStatus());
        assertEquals(AlertOrigin.MANUAL, result.getOrigin());
        assertEquals("Evacuar el edificio", result.getInstructions());
        assertEquals(ACTOR, result.getCreatedBy());
        assertEquals(NOW, result.getCreatedAt());
        assertEquals(NOW, result.getUpdatedAt());
        assertNull(result.getId());
        assertNull(result.getSimulationId());
        assertNull(result.getActivationAt());
    }

    // toScopeItems

    @Test
    void toScopeItems_shouldCreateSingleItemWithoutRef_whenScopeIsCampus() {
        List<AlertScopeItemEntity> result = mapper.toScopeItems(
                ALERT_ID, new ScopeRequest(ScopeType.CAMPUS, List.of()), NOW);

        assertEquals(1, result.size());
        AlertScopeItemEntity item = result.get(0);
        assertEquals(ALERT_ID, item.getAlertId());
        assertEquals(ScopeType.CAMPUS, item.getScopeType());
        assertNull(item.getScopeRefId());
        assertEquals(NOW, item.getCreatedAt());
        assertEquals(NOW, item.getUpdatedAt());
    }

    @Test
    void toScopeItems_shouldCreateOneItemPerId_whenScopeIsBuilding() {
        List<AlertScopeItemEntity> result = mapper.toScopeItems(
                ALERT_ID, new ScopeRequest(ScopeType.BUILDING, List.of("B1", "B2")), NOW);

        assertEquals(2, result.size());
        assertEquals(ScopeType.BUILDING, result.get(0).getScopeType());
        assertEquals("B1", result.get(0).getScopeRefId());
        assertEquals(ScopeType.BUILDING, result.get(1).getScopeType());
        assertEquals("B2", result.get(1).getScopeRefId());
        assertEquals(ALERT_ID, result.get(0).getAlertId());
        assertEquals(NOW, result.get(1).getCreatedAt());
    }

    // toRecipients

    @Test
    void toRecipients_shouldMapEachRecipientToEntity_whenListIsNotEmpty() {
        List<AlertRecipientEntity> result = mapper.toRecipients(ALERT_ID, List.of(
                new RecipientRequest(RecipientType.USER, "u1"),
                new RecipientRequest(RecipientType.ROLE, "STUDENT")), NOW);

        assertEquals(2, result.size());
        assertEquals(ALERT_ID, result.get(0).getAlertId());
        assertEquals(RecipientType.USER, result.get(0).getRecipientType());
        assertEquals("u1", result.get(0).getRecipientRefId());
        assertEquals(RecipientType.ROLE, result.get(1).getRecipientType());
        assertEquals("STUDENT", result.get(1).getRecipientRefId());
        assertEquals(NOW, result.get(0).getCreatedAt());
        assertEquals(NOW, result.get(1).getUpdatedAt());
    }

    @Test
    void toRecipients_shouldReturnEmptyList_whenThereAreNoRecipients() {
        assertTrue(mapper.toRecipients(ALERT_ID, List.of(), NOW).isEmpty());
    }

    // toScope

    @Test
    void toScope_shouldReturnNull_whenThereAreNoItems() {
        assertNull(mapper.toScope(List.of()));
    }

    @Test
    void toScope_shouldReturnCampusWithEmptyIds_whenItemIsCampus() {
        ScopeRequest result = mapper.toScope(List.of(scopeItem(ScopeType.CAMPUS, null)));

        assertEquals(new ScopeRequest(ScopeType.CAMPUS, List.of()), result);
    }

    @Test
    void toScope_shouldReturnSortedIds_whenItemsAreBuildings() {
        ScopeRequest result = mapper.toScope(List.of(
                scopeItem(ScopeType.BUILDING, "B2"),
                scopeItem(ScopeType.BUILDING, "B1")));

        assertEquals(new ScopeRequest(ScopeType.BUILDING, List.of("B1", "B2")), result);
    }

    @Test
    void toScope_shouldThrowIllegalState_whenItemsHaveMixedTypes() {
        List<AlertScopeItemEntity> mixed = List.of(
                scopeItem(ScopeType.ZONE, "Z1"),
                scopeItem(ScopeType.BUILDING, "B1"));

        assertThrows(IllegalStateException.class, () -> mapper.toScope(mixed));
    }

    // toRecipientRequests

    @Test
    void toRecipientRequests_shouldSortByTypeThenId_whenRecipientsAreUnordered() {
        List<RecipientRequest> result = mapper.toRecipientRequests(List.of(
                recipient(RecipientType.ROLE, "b"),
                recipient(RecipientType.USER, "b"),
                recipient(RecipientType.ROLE, "a"),
                recipient(RecipientType.USER, "a")));

        assertEquals(List.of(
                new RecipientRequest(RecipientType.USER, "a"),
                new RecipientRequest(RecipientType.USER, "b"),
                new RecipientRequest(RecipientType.ROLE, "a"),
                new RecipientRequest(RecipientType.ROLE, "b")), result);
    }

    // toResponse

    @Test
    void toResponse_shouldMapAllAlertFields_whenAlertHasScopeAndRecipients() {
        AlertEntity alert = fullAlert();

        AlertResponse result = mapper.toResponse(
                alert,
                List.of(scopeItem(ScopeType.BUILDING, "B1")),
                List.of(recipient(RecipientType.USER, "u1")));

        AlertResponse expected = new AlertResponse(
                ALERT_ID,
                AlertType.EARTHQUAKE,
                TITLE,
                AlertStatus.ACTIVE,
                AlertOrigin.SIMULATION,
                SIMULATION_ID,
                new ScopeRequest(ScopeType.BUILDING, List.of("B1")),
                "Evacuar",
                List.of(new RecipientRequest(RecipientType.USER, "u1")),
                ACTOR,
                NOW.minusSeconds(300),
                NOW.minusSeconds(200),
                NOW.minusSeconds(100),
                NOW.minusSeconds(90),
                NOW.minusSeconds(80),
                NOW.minusSeconds(70));
        assertEquals(expected, result);
    }

    // toSummary

    @Test
    void toSummary_shouldMapSummaryFields_whenAlertHasScope() {
        AlertEntity alert = fullAlert();

        AlertSummaryResponse result = mapper.toSummary(
                alert,
                List.of(scopeItem(ScopeType.CAMPUS, null)),
                List.of(recipient(RecipientType.USER, "usr-1"), recipient(RecipientType.ROLE, "STUDENT")));

        AlertSummaryResponse expected = new AlertSummaryResponse(
                ALERT_ID,
                AlertType.EARTHQUAKE,
                TITLE,
                new ScopeRequest(ScopeType.CAMPUS, List.of()),
                AlertStatus.ACTIVE,
                AlertOrigin.SIMULATION,
                SIMULATION_ID,
                NOW.minusSeconds(300),
                "Evacuar",
                List.of(
                        new RecipientRequest(RecipientType.USER, "usr-1"),
                        new RecipientRequest(RecipientType.ROLE, "STUDENT")));
        assertEquals(expected, result);
    }

    @Test
    void toSummary_shouldReturnNullScope_whenAlertHasNoScopeItems() {
        AlertEntity alert = fullAlert();

        AlertSummaryResponse result = mapper.toSummary(alert, List.of(), List.of());

        AlertSummaryResponse expected = new AlertSummaryResponse(
                ALERT_ID,
                AlertType.EARTHQUAKE,
                TITLE,
                null,
                AlertStatus.ACTIVE,
                AlertOrigin.SIMULATION,
                SIMULATION_ID,
                NOW.minusSeconds(300),
                "Evacuar",
                List.of());
        assertEquals(expected, result);
    }
}