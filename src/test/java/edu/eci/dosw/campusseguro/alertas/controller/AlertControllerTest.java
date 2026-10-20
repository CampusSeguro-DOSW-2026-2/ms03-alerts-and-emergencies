package edu.eci.dosw.campusseguro.alertas.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import edu.eci.dosw.campusseguro.alertas.dto.response.AlertResponse;
import edu.eci.dosw.campusseguro.alertas.dto.response.AlertSummaryResponse;
import edu.eci.dosw.campusseguro.alertas.dto.response.AuditEventResponse;
import edu.eci.dosw.campusseguro.alertas.dto.response.ConfirmationResponse;
import edu.eci.dosw.campusseguro.alertas.dto.response.PageResponse;
import edu.eci.dosw.campusseguro.alertas.dto.response.StateChangeResponse;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AuditAction;
import edu.eci.dosw.campusseguro.alertas.security.AccessActor;
import edu.eci.dosw.campusseguro.alertas.security.ActorResolver;
import edu.eci.dosw.campusseguro.alertas.service.AlertCreationResult;
import edu.eci.dosw.campusseguro.alertas.service.AlertLifecycleService;
import edu.eci.dosw.campusseguro.alertas.service.AlertService;
import edu.eci.dosw.campusseguro.alertas.service.AuditQueryService;
import edu.eci.dosw.campusseguro.alertas.service.ConfirmationResult;
import edu.eci.dosw.campusseguro.alertas.service.ConfirmationService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
    private static final UUID ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final String ACTOR = "coordinator";

    // ADJUST these JSON bodies to your real DTO field names / constraints if they differ.
    private static final String VALID_CREATE_JSON = """
            {
              "tipo": "EARTHQUAKE",
              "instrucciones": "Evacuar hacia los puntos de encuentro",
              "alcance": { "tipo": "CAMPUS" },
              "destinatarios": [ { "tipo": "ROLE", "id": "STUDENT" } ]
            }
            """;
    private static final String VALID_UPDATE_JSON = """
            { "instrucciones": "Nuevas instrucciones" }
            """;
    private static final String VALID_SCHEDULE_JSON = """
            { "activationAt": "2030-10-16T18:00:00Z" }
            """;

    @Mock
    private AlertService alertService;

    @Mock
    private AlertLifecycleService alertLifecycleService;

    @Mock
    private ActorResolver actorResolver;

    @Mock
    private AuditQueryService auditQueryService;

    @Mock
    private ConfirmationService confirmationService;

    private MockMvc mockMvc;

    private AlertResponse response;

    private StateChangeResponse stateChange;

    @BeforeEach
    void setUp() {
        lenient().when(actorResolver.resolve()).thenReturn(ACTOR);
        response = mock(AlertResponse.class);
        lenient().when(response.id()).thenReturn(ID);
        stateChange = new StateChangeResponse(ID, AlertStatus.DRAFT, AlertStatus.SCHEDULED, java.time.Instant.EPOCH);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AlertController(
                        alertService,
                        alertLifecycleService,
                        actorResolver,
                        auditQueryService,
                        confirmationService))
                .build();
    }

    // helpers 

    private PageResponse<AlertSummaryResponse> emptyPage() {
        return PageResponse.of(List.<AlertSummaryResponse>of(), new PageImpl<>(List.of()));
    }

    // create 

    @Test
    void create_shouldReturn201WithLocationHeader_whenRequestIsValid() throws Exception {
        when(alertService.create(any(AlertCreateRequest.class), eq(ACTOR)))
                .thenReturn(new AlertCreationResult(response, true));

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CREATE_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith(BASE_URL + "/" + ID)))
                .andExpect(jsonPath("$.id").value(ID.toString()));

        verify(alertService).create(any(AlertCreateRequest.class), eq(ACTOR));
    }

    @Test
    void create_shouldReturn200WithoutLocation_whenSimulationIsIdempotent() throws Exception {
        when(alertService.create(any(AlertCreateRequest.class), eq(ACTOR)))
                .thenReturn(new AlertCreationResult(response, false));

        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CREATE_JSON))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.id").value(ID.toString()));
    }

    @Test
    void create_shouldReturn400AndNotCallService_whenBodyIsInvalid() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(alertService);
    }

    @Test
    void create_shouldReturn400AndNotCallService_whenBodyIsMissing() throws Exception {
        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(alertService);
    }

    // search 

    @Test
    void search_shouldReturn200AndEmptyFilter_whenNoParamsAreGiven() throws Exception {
        when(alertService.search(any(AlertFilter.class))).thenReturn(emptyPage());

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido").isEmpty());

        ArgumentCaptor<AlertFilter> captor = ArgumentCaptor.forClass(AlertFilter.class);
        verify(alertService).search(captor.capture());
        AlertFilter filter = captor.getValue();
        assertNull(filter.estado());
        assertNull(filter.fechaInicio());
        assertNull(filter.fechaFin());
        assertNull(filter.simulationId());
        assertNull(filter.page());
        assertNull(filter.size());
    }

    @Test
    void search_shouldBuildFilterFromQueryParams_whenAllParamsAreGiven() throws Exception {
        when(alertService.search(any(AlertFilter.class))).thenReturn(emptyPage());

        mockMvc.perform(get(BASE_URL)
                        .param("estado", "ACTIVE")
                        .param("fechaInicio", "2026-10-01")
                        .param("fechaFin", "2026-10-10")
                        .param("simulationId", "SIM-1")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk());

        ArgumentCaptor<AlertFilter> captor = ArgumentCaptor.forClass(AlertFilter.class);
        verify(alertService).search(captor.capture());
        AlertFilter filter = captor.getValue();
        assertEquals(AlertStatus.ACTIVE, filter.estado());
        assertEquals(LocalDate.of(2026, 10, 1), filter.fechaInicio());
        assertEquals(LocalDate.of(2026, 10, 10), filter.fechaFin());
        assertEquals("SIM-1", filter.simulationId());
        assertEquals(1, filter.page());
        assertEquals(10, filter.size());
    }

    @Test
    void search_shouldReturn400AndNotCallService_whenEstadoIsInvalid() throws Exception {
        mockMvc.perform(get(BASE_URL).param("estado", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(alertService);
    }

    @Test
    void search_shouldReturn400AndNotCallService_whenDateFormatIsInvalid() throws Exception {
        mockMvc.perform(get(BASE_URL).param("fechaInicio", "15-10-2026"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(alertService);
    }

    @Test
    void search_shouldReturn400AndNotCallService_whenPageIsNotANumber() throws Exception {
        mockMvc.perform(get(BASE_URL).param("page", "abc"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(alertService);
    }

    // findById 

    @Test
    void findById_shouldReturn200WithAlert_whenAlertExists() throws Exception {
        when(alertService.findById(eq(ID), any(AccessActor.class))).thenReturn(response);

        mockMvc.perform(get(BASE_URL + "/{id}", ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID.toString()));
    }

    @Test
    void findById_shouldReturn400AndNotCallService_whenIdIsNotAUuid() throws Exception {
        mockMvc.perform(get(BASE_URL + "/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(alertService);
    }

    // update 

    @Test
    void update_shouldReturn200AndPassResolvedActor_whenRequestIsValid() throws Exception {
        when(alertService.update(eq(ID), any(AlertUpdateRequest.class), eq(ACTOR))).thenReturn(response);

        mockMvc.perform(put(BASE_URL + "/{id}", ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_UPDATE_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID.toString()));

        verify(alertService).update(eq(ID), any(AlertUpdateRequest.class), eq(ACTOR));
    }

    @Test
    void update_shouldReturn400AndNotCallService_whenBodyIsMissing() throws Exception {
        mockMvc.perform(put(BASE_URL + "/{id}", ID).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(alertService);
    }

    // schedule 

    @Test
    void schedule_shouldReturn200AndPassResolvedActor_whenRequestIsValid() throws Exception {
        when(alertLifecycleService.schedule(eq(ID), any(AlertScheduleRequest.class), eq(ACTOR)))
                .thenReturn(stateChange);

        mockMvc.perform(patch(BASE_URL + "/{id}/programar", ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_SCHEDULE_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idAlerta").value(ID.toString()));

        verify(alertLifecycleService).schedule(eq(ID), any(AlertScheduleRequest.class), eq(ACTOR));
    }

    @Test
    void schedule_shouldReturn400AndNotCallService_whenBodyIsMissing() throws Exception {
        mockMvc.perform(patch(BASE_URL + "/{id}/programar", ID).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(alertLifecycleService);
    }

    // activate 

    @Test
    void activate_shouldReturn200AndPassResolvedActor_whenAlertExists() throws Exception {
        when(alertLifecycleService.activate(ID, ACTOR)).thenReturn(stateChange);

        mockMvc.perform(patch(BASE_URL + "/{id}/activar", ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idAlerta").value(ID.toString()));

        verify(alertLifecycleService).activate(ID, ACTOR);
    }

    @Test
    void activate_shouldReturn400AndNotCallService_whenIdIsNotAUuid() throws Exception {
        mockMvc.perform(patch(BASE_URL + "/{id}/activar", "not-a-uuid"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(alertLifecycleService);
    }

    // end 

    @Test
    void end_shouldReturn200AndPassResolvedActor_whenAlertExists() throws Exception {
        when(alertLifecycleService.end(eq(ID), any(), eq(ACTOR))).thenReturn(stateChange);

        mockMvc.perform(patch(BASE_URL + "/{id}/finalizar", ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idAlerta").value(ID.toString()));

        verify(alertLifecycleService).end(eq(ID), any(), eq(ACTOR));
    }

    @Test
    void end_shouldBindSimulationId_whenBodyIsProvided() throws Exception {
        when(alertLifecycleService.end(eq(ID), any(), eq(ACTOR))).thenReturn(stateChange);

        mockMvc.perform(patch(BASE_URL + "/{id}/finalizar", ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"simulationId\":\"SIM-9\"}"))
                .andExpect(status().isOk());

        verify(alertLifecycleService).end(eq(ID),
                org.mockito.ArgumentMatchers.argThat(request -> "SIM-9".equals(request.simulationId())),
                eq(ACTOR));
    }

    @Test
    void end_shouldReturn400AndNotCallService_whenSimulationIdIsTooLong() throws Exception {
        String tooLong = "x".repeat(65);
        mockMvc.perform(patch(BASE_URL + "/{id}/finalizar", ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"simulationId\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(alertLifecycleService);
    }

    // confirmations 

    @Test
    void confirm_shouldReturn201_whenConfirmationIsCreated() throws Exception {
        ConfirmationResponse confirmation =
                new ConfirmationResponse(UUID.randomUUID(), ID, "usr-001", Instant.EPOCH);
        when(confirmationService.confirm(eq(ID), any(AccessActor.class)))
                .thenReturn(new ConfirmationResult(confirmation, true));

        mockMvc.perform(post(BASE_URL + "/{id}/confirmaciones", ID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idAlerta").value(ID.toString()))
                .andExpect(jsonPath("$.usuarioId").value("usr-001"));
    }

    @Test
    void confirm_shouldReturn200_whenConfirmationAlreadyExists() throws Exception {
        ConfirmationResponse confirmation =
                new ConfirmationResponse(UUID.randomUUID(), ID, "usr-001", Instant.EPOCH);
        when(confirmationService.confirm(eq(ID), any(AccessActor.class)))
                .thenReturn(new ConfirmationResult(confirmation, false));

        mockMvc.perform(post(BASE_URL + "/{id}/confirmaciones", ID))
                .andExpect(status().isOk());
    }

    // audit 

    @Test
    void audit_shouldReturnEvents_whenAlertExists() throws Exception {
        AuditEventResponse event = new AuditEventResponse(UUID.randomUUID(), ID, ACTOR, "USER",
                AuditAction.CREATE, null, AlertStatus.DRAFT, "detalle", Instant.EPOCH);
        when(auditQueryService.findByAlert(ID)).thenReturn(List.of(event));

        mockMvc.perform(get(BASE_URL + "/{id}/auditoria", ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("CREATE"))
                .andExpect(jsonPath("$[0].actor").value(ACTOR));
    }

    @Test
    void audit_shouldReturn400_whenIdIsNotAUuid() throws Exception {
        mockMvc.perform(get(BASE_URL + "/{id}/auditoria", "not-a-uuid"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(auditQueryService);
    }

    // archive 

    @Test
    void archive_shouldReturn200AndPassResolvedActor_whenAlertExists() throws Exception {
        when(alertLifecycleService.archive(ID, ACTOR)).thenReturn(stateChange);

        mockMvc.perform(patch(BASE_URL + "/{id}/archivar", ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idAlerta").value(ID.toString()));

        verify(alertLifecycleService).archive(ID, ACTOR);
    }
}
