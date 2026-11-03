package edu.eci.dosw.campusseguro.alertas.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.eci.dosw.campusseguro.alertas.dto.request.AlertCreateRequest;
import edu.eci.dosw.campusseguro.alertas.dto.request.AlertFilter;
import edu.eci.dosw.campusseguro.alertas.dto.request.AlertScheduleRequest;
import edu.eci.dosw.campusseguro.alertas.dto.request.AlertUpdateRequest;
import edu.eci.dosw.campusseguro.alertas.dto.request.InboxStatus;
import edu.eci.dosw.campusseguro.alertas.dto.response.AlertResponse;
import edu.eci.dosw.campusseguro.alertas.dto.response.AlertSummaryResponse;
import edu.eci.dosw.campusseguro.alertas.dto.response.InboxAlertResponse;
import edu.eci.dosw.campusseguro.alertas.dto.response.PageResponse;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertType;
import edu.eci.dosw.campusseguro.alertas.exception.GlobalExceptionHandler;
import edu.eci.dosw.campusseguro.alertas.security.AccessActor;
import edu.eci.dosw.campusseguro.alertas.security.ActorResolver;
import edu.eci.dosw.campusseguro.alertas.security.RequesterContext;
import edu.eci.dosw.campusseguro.alertas.security.UserRole;
import edu.eci.dosw.campusseguro.alertas.service.AlertCreationResult;
import edu.eci.dosw.campusseguro.alertas.service.AlertLifecycleService;
import edu.eci.dosw.campusseguro.alertas.service.AlertService;
import edu.eci.dosw.campusseguro.alertas.service.InboxService;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Controller tests with a standalone MockMvc (no Spring context, no Spring Security).
 * They verify HTTP mapping, binding, validation, status codes and delegation to the services.
 * Role-based access (@PreAuthorize) is NOT covered here.
 */
@ExtendWith(MockitoExtension.class)
class AlertControllerTest {

    private static final String BASE_URL = "/api/v1/alertas";

    private static final UUID ID =
            UUID.fromString("55555555-5555-5555-5555-555555555555");

    private static final UUID SIMULATION_ID =
            UUID.fromString("88888888-8888-8888-8888-888888888888");

    private static final String ACTOR = "coordinator";

    private static final RequesterContext REQUESTER = new RequesterContext(
            ACTOR,
            Set.of(UserRole.COORDINADOR),
            null);

    private static final String VALID_CREATE_JSON = """
            {
              "types": ["EARTHQUAKE", "FIRE"],
              "title": "Simulacro Sismico Nacional",
              "scope": { "type": "CAMPUS" },
              "instructions": "Evacuar hacia los puntos de encuentro",
              "recipients": [ { "type": "ROLE", "id": "ESTUDIANTE" } ]
            }
            """;

    private static final String VALID_UPDATE_JSON = """
            { "instructions": "Nuevas instrucciones" }
            """;

    private static final String VALID_SCHEDULE_JSON = """
            { "activationAt": "2030-10-16T18:00:00Z" }
            """;

    @Mock
    private AlertService alertService;

    @Mock
    private AlertLifecycleService alertLifecycleService;

    @Mock
    private InboxService inboxService;

    @Mock
    private ActorResolver actorResolver;

    private MockMvc mockMvc;

    private AlertResponse response;

    @BeforeEach
    void setUp() {
        lenient().when(actorResolver.resolve()).thenReturn(ACTOR);
        lenient().when(actorResolver.requester()).thenReturn(REQUESTER);

        response = mock(AlertResponse.class);
        lenient().when(response.id()).thenReturn(ID);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new AlertController(
                        alertService,
                        alertLifecycleService,
                        inboxService,
                        actorResolver))
                .setControllerAdvice(
                        new GlobalExceptionHandler())
                .build();
    }

    // Helpers

    private PageResponse<AlertSummaryResponse> emptyPage() {
        return PageResponse.of(
                List.<AlertSummaryResponse>of(),
                new PageImpl<>(List.of()));
    }

    // Create

    @Test
    void create_shouldReturn201WithLocationHeader_whenRequestIsValid()
            throws Exception {

        when(alertService.create(
                any(AlertCreateRequest.class), eq(REQUESTER)))
                .thenReturn(new AlertCreationResult(response, true));

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CREATE_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        org.hamcrest.Matchers.endsWith(
                                BASE_URL + "/" + ID)))
                .andExpect(jsonPath("$.id").value(ID.toString()));

        ArgumentCaptor<AlertCreateRequest> captor =
                ArgumentCaptor.forClass(AlertCreateRequest.class);

        verify(alertService).create(captor.capture(), eq(REQUESTER));

        assertEquals(
                List.of(AlertType.EARTHQUAKE, AlertType.FIRE),
                captor.getValue().types());
    }

    @Test
    void create_shouldReturn400AndNotCallService_whenBodyIsInvalid()
            throws Exception {

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(alertService);
    }

    @Test
    void create_shouldReturn400AndNotCallService_whenTitleIsMissing()
            throws Exception {

        String withoutTitle = """
                {
                  "types": ["EARTHQUAKE"],
                  "scope": { "type": "CAMPUS" },
                  "instructions": "Evacuar",
                  "recipients": [
                    { "type": "ROLE", "id": "ESTUDIANTE" }
                  ]
                }
                """;

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withoutTitle))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo")
                        .value("title"));

        verifyNoInteractions(alertService);
    }

    @Test
    void create_shouldReturn400AndNotCallService_whenBodyIsMissing()
            throws Exception {

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(alertService);
    }

    // Search

    @Test
    void search_shouldReturn200AndEmptyFilter_whenNoParamsAreGiven()
            throws Exception {

        when(alertService.search(any(AlertFilter.class)))
                .thenReturn(emptyPage());

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido").isEmpty());

        ArgumentCaptor<AlertFilter> captor =
                ArgumentCaptor.forClass(AlertFilter.class);

        verify(alertService).search(captor.capture());

        AlertFilter filter = captor.getValue();

        assertNull(filter.status());
        assertNull(filter.types());
        assertNull(filter.startDate());
        assertNull(filter.endDate());
        assertNull(filter.simulationId());
        assertNull(filter.page());
        assertNull(filter.size());
    }

    @Test
    void search_shouldBuildFilterFromQueryParams_whenAllParamsAreGiven()
            throws Exception {

        when(alertService.search(any(AlertFilter.class)))
                .thenReturn(emptyPage());

        mockMvc.perform(get(BASE_URL)
                        .param("status", "ACTIVE")
                        .param("type", "EARTHQUAKE")
                        .param("startDate", "2026-10-01")
                        .param("endDate", "2026-10-10")
                        .param("simulationId", SIMULATION_ID.toString())
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk());

        ArgumentCaptor<AlertFilter> captor =
                ArgumentCaptor.forClass(AlertFilter.class);

        verify(alertService).search(captor.capture());

        AlertFilter filter = captor.getValue();

        assertEquals(AlertStatus.ACTIVE, filter.status());
        assertEquals(List.of(AlertType.EARTHQUAKE), filter.types());
        assertEquals(
                LocalDate.of(2026, 10, 1),
                filter.startDate());
        assertEquals(
                LocalDate.of(2026, 10, 10),
                filter.endDate());
        assertEquals(SIMULATION_ID, filter.simulationId());
        assertEquals(1, filter.page());
        assertEquals(10, filter.size());
    }

    @Test
    void search_shouldReturn400AndNotCallService_whenStatusIsInvalid()
            throws Exception {

        mockMvc.perform(get(BASE_URL)
                        .param("status", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(alertService);
    }

    @Test
    void search_shouldReturn400AndNotCallService_whenDateFormatIsInvalid()
            throws Exception {

        mockMvc.perform(get(BASE_URL)
                        .param("startDate", "15-10-2026"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(alertService);
    }

    @Test
    void search_shouldReturn400AndNotCallService_whenSimulationIdIsNotAUuid()
            throws Exception {

        mockMvc.perform(get(BASE_URL)
                        .param("simulationId", "SIM-1"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(alertService);
    }

    @Test
    void search_shouldReturn400AndNotCallService_whenPageIsNotANumber()
            throws Exception {

        mockMvc.perform(get(BASE_URL)
                        .param("page", "abc"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(alertService);
    }

    // Find by ID

    @Test
    void findById_shouldReturn200WithAlert_whenAlertExists()
            throws Exception {

        when(alertService.findById(eq(ID), any(AccessActor.class)))
                .thenReturn(response);

        mockMvc.perform(get(BASE_URL + "/{id}", ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID.toString()));
    }

    @Test
    void findById_shouldReturn400AndNotCallService_whenIdIsNotAUuid()
            throws Exception {

        mockMvc.perform(get(BASE_URL + "/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(alertService);
    }

    // Update

    @Test
    void update_shouldReturn200AndPassResolvedActor_whenRequestIsValid()
            throws Exception {

        when(alertService.update(
                eq(ID), any(AlertUpdateRequest.class), eq(REQUESTER)))
                .thenReturn(response);

        mockMvc.perform(put(BASE_URL + "/{id}", ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_UPDATE_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID.toString()));

        verify(alertService).update(
                eq(ID), any(AlertUpdateRequest.class), eq(REQUESTER));
    }

    @Test
    void update_shouldBindMultipleTypes_whenRequestContainsTypes() throws Exception {
        when(alertService.update(
                eq(ID), any(AlertUpdateRequest.class), eq(REQUESTER)))
                .thenReturn(response);

        String body = """
                { "types": ["FIRE", "EARTHQUAKE"] }
                """;

        mockMvc.perform(put(BASE_URL + "/{id}", ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        ArgumentCaptor<AlertUpdateRequest> captor =
                ArgumentCaptor.forClass(AlertUpdateRequest.class);

        verify(alertService).update(
                eq(ID), captor.capture(), eq(REQUESTER));

        assertEquals(
                List.of(AlertType.FIRE, AlertType.EARTHQUAKE),
                captor.getValue().types());
    }

    @Test
    void update_shouldReturn400AndNotCallService_whenBodyIsMissing()
            throws Exception {

        mockMvc.perform(put(BASE_URL + "/{id}", ID)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(alertService);
    }

    // Schedule

    @Test
    void schedule_shouldReturn200AndPassResolvedActor_whenRequestIsValid()
            throws Exception {

        when(alertLifecycleService.schedule(
                eq(ID), any(AlertScheduleRequest.class), eq(REQUESTER)))
                .thenReturn(response);

        mockMvc.perform(patch(BASE_URL + "/{id}/programar", ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_SCHEDULE_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID.toString()));

        verify(alertLifecycleService).schedule(
                eq(ID), any(AlertScheduleRequest.class), eq(REQUESTER));
    }

    @Test
    void schedule_shouldReturn400AndNotCallService_whenBodyIsMissing()
            throws Exception {

        mockMvc.perform(patch(BASE_URL + "/{id}/programar", ID)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(alertLifecycleService);
    }

    // Activate

    @Test
    void activate_shouldReturn200AndPassResolvedActor_whenAlertExists()
            throws Exception {

        when(alertLifecycleService.activate(ID, REQUESTER))
                .thenReturn(response);

        mockMvc.perform(patch(BASE_URL + "/{id}/activar", ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID.toString()));

        verify(alertLifecycleService).activate(ID, REQUESTER);
    }

    @Test
    void activate_shouldReturn400AndNotCallService_whenIdIsNotAUuid()
            throws Exception {

        mockMvc.perform(
                        patch(BASE_URL + "/{id}/activar", "not-a-uuid"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(alertLifecycleService);
    }

    // Archive

    @Test
    void archive_shouldReturn200AndPassRequester_whenAlertExists()
            throws Exception {

        when(alertLifecycleService.archive(ID, REQUESTER))
                .thenReturn(response);

        mockMvc.perform(patch(BASE_URL + "/{id}/archivar", ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID.toString()));

        verify(alertLifecycleService).archive(ID, REQUESTER);
    }

    // Inbox

    @Test
    void inbox_shouldReturn200AndUseDefaults_whenNoParamsAreGiven()
            throws Exception {

        PageResponse<InboxAlertResponse> page = PageResponse.of(
                List.of(),
                new PageImpl<>(List.<InboxAlertResponse>of()));

        when(inboxService.inbox(isNull(), isNull(), isNull(), any(AccessActor.class)))
                .thenReturn(page);

        mockMvc.perform(get(BASE_URL + "/mias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido").isEmpty());

        verify(inboxService).inbox(
                isNull(),
                isNull(),
                isNull(),
                any(AccessActor.class));
    }

    @Test
    void inbox_shouldBindStatusAndPagination_whenParamsAreGiven()
            throws Exception {

        PageResponse<InboxAlertResponse> page = PageResponse.of(
                List.of(),
                new PageImpl<>(List.<InboxAlertResponse>of()));

        when(inboxService.inbox(
                eq(InboxStatus.ARCHIVED),
                eq(1),
                eq(5),
                any(AccessActor.class)))
                .thenReturn(page);

        mockMvc.perform(get(BASE_URL + "/mias")
                        .param("status", "ARCHIVED")
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk());

        verify(inboxService).inbox(
                eq(InboxStatus.ARCHIVED),
                eq(1),
                eq(5),
                any(AccessActor.class));
    }

}
