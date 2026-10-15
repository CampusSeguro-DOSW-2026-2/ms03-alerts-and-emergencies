package edu.eci.dosw.campusseguro.alertas.client.catalog;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import edu.eci.dosw.campusseguro.alertas.client.catalog.StubCatalogClient.CatalogData;
import edu.eci.dosw.campusseguro.alertas.client.catalog.StubCatalogClient.Entry;
import edu.eci.dosw.campusseguro.alertas.client.catalog.StubCatalogClient.UserEntry;
import edu.eci.dosw.campusseguro.alertas.dto.request.RecipientRequest;
import edu.eci.dosw.campusseguro.alertas.entity.enums.RecipientType;
import edu.eci.dosw.campusseguro.alertas.entity.enums.ScopeType;
import edu.eci.dosw.campusseguro.alertas.exception.InvalidReferenceException;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Tests StubCatalogClient against the real catalog-stub.json on the classpath.
 * Valid ids are read from that same file, so the tests do not depend on specific ids.
 */
class StubCatalogClientTest {

    private static final String UNKNOWN_ID = "NO-SUCH-ID";

    private CatalogData data;
    private StubCatalogClient client;

    @BeforeEach
    void setUp() throws Exception {
        ObjectMapper objectMapper = JsonMapper.builder().build();
        try (InputStream input = new ClassPathResource("catalog-stub.json").getInputStream()) {
            data = objectMapper.readValue(input, CatalogData.class);
        }
        assertFalse(data.buildings().isEmpty(), "catalog-stub.json must define at least one building");
        assertFalse(data.zones().isEmpty(), "catalog-stub.json must define at least one zone");
        assertFalse(data.roles().isEmpty(), "catalog-stub.json must define at least one role");
        assertFalse(data.users().isEmpty(), "catalog-stub.json must define at least one user");

        client = new StubCatalogClient(objectMapper);
    }

    // helpers 

    private List<String> idsOf(List<Entry> entries) {
        return entries.stream().map(Entry::id).toList();
    }

    private UserEntry userWithRoles() {
        return data.users().stream()
                .filter(user -> !user.roles().isEmpty())
                .findFirst()
                .orElseThrow(() -> new AssertionError("catalog-stub.json must define a user with at least one role"));
    }

    // validateBuildingsZones 

    @Test
    void validateBuildingsZones_shouldNotThrow_whenScopeIsCampus() {
        assertDoesNotThrow(() -> client.validateBuildingsZones(ScopeType.CAMPUS, List.of(UNKNOWN_ID)));
    }

    @Test
    void validateBuildingsZones_shouldNotThrow_whenBuildingIdsExist() {
        assertDoesNotThrow(() -> client.validateBuildingsZones(ScopeType.BUILDING, idsOf(data.buildings())));
    }

    @Test
    void validateBuildingsZones_shouldNotThrow_whenZoneIdsExist() {
        assertDoesNotThrow(() -> client.validateBuildingsZones(ScopeType.ZONE, idsOf(data.zones())));
    }

    @Test
    void validateBuildingsZones_shouldNotThrow_whenIdsListIsEmpty() {
        assertDoesNotThrow(() -> client.validateBuildingsZones(ScopeType.BUILDING, List.of()));
    }

    @Test
    void validateBuildingsZones_shouldThrowInvalidReference_whenBuildingDoesNotExist() {
        InvalidReferenceException ex = assertThrows(
                InvalidReferenceException.class,
                () -> client.validateBuildingsZones(ScopeType.BUILDING, List.of(UNKNOWN_ID)));

        assertEquals("alcance.ids", ex.getField());
        assertEquals(List.of(UNKNOWN_ID), ex.getInvalidIds());
    }

    @Test
    void validateBuildingsZones_shouldThrowInvalidReference_whenZoneDoesNotExist() {
        InvalidReferenceException ex = assertThrows(
                InvalidReferenceException.class,
                () -> client.validateBuildingsZones(ScopeType.ZONE, List.of(UNKNOWN_ID)));

        assertEquals("alcance.ids", ex.getField());
        assertEquals(List.of(UNKNOWN_ID), ex.getInvalidIds());
    }

    @Test
    void validateBuildingsZones_shouldReportOnlyInvalidIds_whenSomeIdsAreValid() {
        String validBuilding = data.buildings().get(0).id();

        InvalidReferenceException ex = assertThrows(
                InvalidReferenceException.class,
                () -> client.validateBuildingsZones(ScopeType.BUILDING, List.of(validBuilding, UNKNOWN_ID)));

        assertEquals(List.of(UNKNOWN_ID), ex.getInvalidIds());
    }

    @Test
    void validateBuildingsZones_shouldNotAcceptZoneId_whenScopeIsBuilding() {
        String zoneId = data.zones().get(0).id();
        assumeFalse(idsOf(data.buildings()).contains(zoneId), "zone id is also a building id in the catalog");

        InvalidReferenceException ex = assertThrows(
                InvalidReferenceException.class,
                () -> client.validateBuildingsZones(ScopeType.BUILDING, List.of(zoneId)));

        assertEquals(List.of(zoneId), ex.getInvalidIds());
    }

    // validateRecipients 

    @Test
    void validateRecipients_shouldNotThrow_whenUserAndRoleExist() {
        List<RecipientRequest> recipients = List.of(
                new RecipientRequest(RecipientType.USER, data.users().get(0).id()),
                new RecipientRequest(RecipientType.ROLE, data.roles().get(0).id()));

        assertDoesNotThrow(() -> client.validateRecipients(recipients));
    }

    @Test
    void validateRecipients_shouldNotThrow_whenListIsEmpty() {
        assertDoesNotThrow(() -> client.validateRecipients(List.of()));
    }

    @Test
    void validateRecipients_shouldThrowInvalidReference_whenUserDoesNotExist() {
        InvalidReferenceException ex = assertThrows(
                InvalidReferenceException.class,
                () -> client.validateRecipients(List.of(new RecipientRequest(RecipientType.USER, UNKNOWN_ID))));

        assertEquals("destinatarios", ex.getField());
        assertEquals(List.of(UNKNOWN_ID), ex.getInvalidIds());
    }

    @Test
    void validateRecipients_shouldThrowInvalidReference_whenRoleDoesNotExist() {
        InvalidReferenceException ex = assertThrows(
                InvalidReferenceException.class,
                () -> client.validateRecipients(List.of(new RecipientRequest(RecipientType.ROLE, UNKNOWN_ID))));

        assertEquals("destinatarios", ex.getField());
        assertEquals(List.of(UNKNOWN_ID), ex.getInvalidIds());
    }

    @Test
    void validateRecipients_shouldCheckRolesAgainstRoles_whenRecipientTypeIsUser() {
        String roleId = data.roles().get(0).id();
        assumeFalse(
                data.users().stream().anyMatch(user -> user.id().equals(roleId)),
                "role id is also a user id in the catalog");

        InvalidReferenceException ex = assertThrows(
                InvalidReferenceException.class,
                () -> client.validateRecipients(List.of(new RecipientRequest(RecipientType.USER, roleId))));

        assertEquals(List.of(roleId), ex.getInvalidIds());
    }

    @Test
    void validateRecipients_shouldReportAllInvalidIds_whenSeveralRecipientsDoNotExist() {
        List<RecipientRequest> recipients = List.of(
                new RecipientRequest(RecipientType.USER, data.users().get(0).id()),
                new RecipientRequest(RecipientType.USER, "UNKNOWN-1"),
                new RecipientRequest(RecipientType.ROLE, "UNKNOWN-2"));

        InvalidReferenceException ex = assertThrows(
                InvalidReferenceException.class, () -> client.validateRecipients(recipients));

        assertEquals(List.of("UNKNOWN-1", "UNKNOWN-2"), ex.getInvalidIds());
    }

    // getUsersByRole 

    @Test
    void getUsersByRole_shouldReturnOnlyUsersWithThatRole_whenRoleHasUsers() {
        String role = userWithRoles().roles().get(0);
        List<ResolvedUser> expected = data.users().stream()
                .filter(user -> user.roles().contains(role))
                .map(user -> new ResolvedUser(user.id(), user.email(), user.name()))
                .toList();

        List<ResolvedUser> result = client.getUsersByRole(role);

        assertFalse(result.isEmpty());
        assertEquals(expected, result);
    }

    @Test
    void getUsersByRole_shouldReturnEmptyList_whenRoleHasNoUsers() {
        assertTrue(client.getUsersByRole(UNKNOWN_ID).isEmpty());
    }
}