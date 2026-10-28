package edu.eci.dosw.campusseguro.alertas.integration;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.eci.dosw.campusseguro.alertas.dto.request.AlertFilter;
import edu.eci.dosw.campusseguro.alertas.dto.response.AlertResponse;
import edu.eci.dosw.campusseguro.alertas.entity.AlertEntity;
import edu.eci.dosw.campusseguro.alertas.entity.AlertRecipientEntity;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertOrigin;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertType;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AuditAction;
import edu.eci.dosw.campusseguro.alertas.entity.enums.RecipientType;
import edu.eci.dosw.campusseguro.alertas.repository.AlertRecipientRepository;
import edu.eci.dosw.campusseguro.alertas.repository.AlertRepository;
import edu.eci.dosw.campusseguro.alertas.repository.AlertSpecifications;
import edu.eci.dosw.campusseguro.alertas.repository.AuditEventRepository;
import edu.eci.dosw.campusseguro.alertas.scheduler.AlertActivationScheduler;
import edu.eci.dosw.campusseguro.alertas.service.AlertLifecycleService;
import edu.eci.dosw.campusseguro.alertas.service.NotificationService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(properties = {
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=file:database/001_create_schema.sql,file:database/002_indexes.sql,file:database/003_phase2_contract.sql,file:database/004_align_simulation_contract.sql",
        "app.alerts.activation-check-ms=3600000"
})
@AutoConfigureMockMvc
@ActiveProfiles({"test", "local"})
class AlertApiIntegrationIT {

    private static final UUID COORDINATOR_ID =
            UUID.fromString("77777777-7777-4777-8777-777777777777");

    private static final UUID ORGANIZER_ID =
            UUID.fromString("99999999-9999-4999-8999-999999999999");

    private static final UUID AUDITOR_ID =
            UUID.fromString("88888888-8888-4888-8888-888888888888");

    private static final UUID STUDENT_ID =
            UUID.fromString("11111111-1111-4111-8111-111111111111");

    private static final String STUB_USER_ID =
            "11111111-1111-4111-8111-111111111111";

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("campusseguro_alerts_it")
            .withUsername("campusseguro")
            .withPassword("campusseguro");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private AlertRecipientRepository recipientRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private AlertLifecycleService alertLifecycleService;

    @MockitoBean
    private AlertActivationScheduler alertActivationScheduler;

    @MockitoBean
    private NotificationService notificationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE audit_events, notification_deliveries, alert_confirmations, "
                + "alert_recipients, alert_scope_items, alerts CASCADE");
    }

    @Test
    void contextLoadsWithValidatedSchema() {
        assertEquals(0, alertRepository.count());
    }

    @Test
    void search_shouldFilterByStatusTypeAndDateRange() {
        alertRepository.save(newAlert(AlertStatus.DRAFT, Instant.parse("2026-10-01T10:00:00Z"), null));
        alertRepository.save(newAlert(AlertStatus.ACTIVE, Instant.parse("2026-10-05T10:00:00Z"), null));
        alertRepository.save(newAlert(AlertStatus.SCHEDULED, Instant.parse("2026-10-10T10:00:00Z"), null));

        AlertFilter filter = new AlertFilter(
                AlertStatus.ACTIVE,
                AlertType.EARTHQUAKE,
                LocalDate.parse("2026-10-04"),
                LocalDate.parse("2026-10-06"),
                null,
                null,
                null);

        var result = alertRepository.findAll(
                AlertSpecifications.fromFilter(filter, ZoneOffset.UTC),
                PageRequest.of(0, 10, Sort.by(Sort.Order.desc("createdAt"))));

        assertEquals(1, result.getTotalElements());
        assertEquals(AlertStatus.ACTIVE, result.getContent().getFirst().getStatus());
    }

    @Test
    void search_shouldFilterBySimulationId() {
        UUID simulationId = UUID.randomUUID();
        alertRepository.save(newAlert(AlertStatus.DRAFT, Instant.now(), simulationId));
        alertRepository.save(newAlert(AlertStatus.DRAFT, Instant.now(), null));

        var result = alertRepository.findAll(
                AlertSpecifications.fromFilter(
                        new AlertFilter(null, null, null, null, simulationId, null, null), ZoneOffset.UTC));

        assertEquals(1, result.size());
        assertEquals(simulationId, result.getFirst().getSimulationId());
    }

    @Test
    void search_shouldPageAndOrderByCreatedAtDescending() {
        for (int i = 0; i < 5; i++) {
            alertRepository.save(newAlert(
                    AlertStatus.DRAFT, Instant.parse("2026-10-0" + (i + 1) + "T10:00:00Z"), null));
        }

        var firstPage = alertRepository.findAll(
                AlertSpecifications.fromFilter(
                        new AlertFilter(null, null, null, null, null, null, null), ZoneOffset.UTC),
                PageRequest.of(0, 2, Sort.by(Sort.Order.desc("createdAt"))));

        assertEquals(2, firstPage.getNumberOfElements());
        assertEquals(5, firstPage.getTotalElements());
        assertEquals(Instant.parse("2026-10-05T10:00:00Z"),
                firstPage.getContent().getFirst().getCreatedAt());
    }

    @Test
    void activation_shouldMoveAlertToActiveAndAuditOnce() {
        AlertEntity alert = alertRepository.save(newAlert(AlertStatus.DRAFT, Instant.now(), null));

        AlertResponse response = alertLifecycleService.activate(alert.getId(), "it-coordinator");

        assertEquals(AlertStatus.ACTIVE, response.status());
        assertEquals(AlertStatus.ACTIVE, alertRepository.findById(alert.getId()).orElseThrow().getStatus());
        assertEquals(1, auditEventRepository.findByAlertIdOrderByCreatedAtAsc(alert.getId()).stream()
                .filter(event -> event.getAction() == AuditAction.ACTIVATE)
                .count());
    }

    @Test
    void scheduledAlert_shouldAllowManualActivationBeforeItsDate() {
        AlertEntity alert = newAlert(AlertStatus.SCHEDULED, Instant.now(), null);
        alert.setActivationAt(Instant.now().plusSeconds(3600));
        AlertEntity saved = alertRepository.save(alert);

        AlertResponse response = alertLifecycleService.activate(saved.getId(), "it-coordinator");

        assertEquals(AlertStatus.ACTIVE, response.status());
        assertEquals(AlertStatus.ACTIVE,
                alertRepository.findById(saved.getId()).orElseThrow().getStatus());
    }

    @Test
    void uniqueConstraint_shouldRejectDuplicatedConfirmation() {
        AlertEntity alert = alertRepository.save(newAlert(AlertStatus.ACTIVE, Instant.now(), null));
        jdbcTemplate.update(
                "INSERT INTO alert_confirmations (alert_id, user_id, confirmed_at) VALUES (?, ?, now())",
                alert.getId(), STUB_USER_ID);

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO alert_confirmations (alert_id, user_id, confirmed_at) VALUES (?, ?, now())",
                alert.getId(), STUB_USER_ID));
    }

    @Test
    void create_shouldReturnUnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(post("/api/v1/alertas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void create_shouldForbidGenericUser() throws Exception {
        mockMvc.perform(as(
                        post("/api/v1/alertas")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createRequestJson()),
                        STUDENT_ID,
                        "ESTUDIANTE"))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_shouldRequireSimulationOriginForOrganizer() throws Exception {
        mockMvc.perform(as(
                        post("/api/v1/alertas")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createRequestJson()),
                        ORGANIZER_ID,
                        "ORGANIZADOR"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
    }

    @Test
    void create_withSameSimulationId_shouldBeIdempotentAndRejectDifferentData()
            throws Exception {

        UUID simulationId = UUID.randomUUID();
        String payload = simulationCreateRequestJson(
                simulationId, "Evacuar el campus");

        mockMvc.perform(as(
                        post("/api/v1/alertas")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload),
                        ORGANIZER_ID,
                        "ORGANIZADOR"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.origin").value("SIMULATION"))
                .andExpect(jsonPath("$.simulationId").value(simulationId.toString()))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));

        mockMvc.perform(as(
                        post("/api/v1/alertas")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload),
                        ORGANIZER_ID,
                        "ORGANIZADOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"));

        mockMvc.perform(as(
                        post("/api/v1/alertas")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(simulationCreateRequestJson(
                                        simulationId, "Otra instruccion")),
                        ORGANIZER_ID,
                        "ORGANIZADOR"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo")
                        .value("SIMULACION_DUPLICADA"));
    }

    @Test
    void coordinator_shouldCreateAlertAndAuditorShouldViewIt() throws Exception {
        String location = mockMvc.perform(as(
                        post("/api/v1/alertas")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createRequestJson()),
                        COORDINATOR_ID,
                        "COORDINADOR"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, notNullValue()))
                .andExpect(jsonPath("$.id").value(notNullValue()))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.recipients", hasSize(1)))
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);

        assertNotNull(location);
        String path = location.substring(location.indexOf("/api/"));

        mockMvc.perform(as(get(path), AUDITOR_ID, "AUDITOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipients[0].id").value(STUB_USER_ID));
    }

    private MockHttpServletRequestBuilder as(
            MockHttpServletRequestBuilder builder,
            UUID userId,
            String roles) {
        return builder
                .header("X-User-Id", userId.toString())
                .header("X-User-Role", roles);
    }

    private AlertEntity newAlert(AlertStatus status, Instant createdAt, UUID simulationId) {
        AlertEntity alert = new AlertEntity();
        alert.setType(AlertType.EARTHQUAKE);
        alert.setTitle("Simulacro sismico");
        alert.setStatus(status);
        alert.setInstructions("Evacuar el campus");
        alert.setOrigin(simulationId == null ? AlertOrigin.MANUAL : AlertOrigin.SIMULATION);
        alert.setSimulationId(simulationId);
        alert.setCreatedBy("it-user");
        alert.setCreatedAt(createdAt);
        alert.setUpdatedAt(createdAt);
        if (status == AlertStatus.SCHEDULED) {
            alert.setActivationAt(createdAt.plusSeconds(3600));
        }
        if (status == AlertStatus.ACTIVE || status == AlertStatus.ENDED || status == AlertStatus.ARCHIVED) {
            alert.setActivatedAt(createdAt);
        }
        if (status == AlertStatus.ENDED || status == AlertStatus.ARCHIVED) {
            alert.setEndedAt(createdAt);
        }
        if (status == AlertStatus.ARCHIVED) {
            alert.setArchivedAt(createdAt);
        }
        return alert;
    }

    private AlertRecipientEntity newRecipient(UUID alertId, String userId) {
        AlertRecipientEntity recipient = new AlertRecipientEntity();
        recipient.setAlertId(alertId);
        recipient.setRecipientType(RecipientType.USER);
        recipient.setRecipientRefId(userId);
        return recipient;
    }

    private String createRequestJson() {
        return """
                {
                  "type": "EARTHQUAKE",
                  "title": "Simulacro sismico",
                  "scope": {"type": "CAMPUS"},
                  "instructions": "Evacuar el campus",
                  "recipients": [{"type": "USER", "id": "%s"}]
                }
                """.formatted(STUB_USER_ID);
    }

    private String simulationCreateRequestJson(
            UUID simulationId,
            String instructions) {
        return """
                {
                  "type": "EARTHQUAKE",
                  "title": "Simulacro sismico",
                  "scope": {"type": "CAMPUS"},
                  "instructions": "%s",
                  "recipients": [{"type": "USER", "id": "%s"}],
                  "origin": {
                    "type": "SIMULATION",
                    "simulationId": "%s"
                  },
                  "activationAt": "2030-10-16T18:00:00Z"
                }
                """.formatted(instructions, STUB_USER_ID, simulationId);
    }
}
