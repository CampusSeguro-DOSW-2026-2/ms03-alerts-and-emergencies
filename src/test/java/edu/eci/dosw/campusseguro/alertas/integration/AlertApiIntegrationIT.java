package edu.eci.dosw.campusseguro.alertas.integration;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.eci.dosw.campusseguro.alertas.client.email.EmailMessage;
import edu.eci.dosw.campusseguro.alertas.client.email.EmailSender;
import edu.eci.dosw.campusseguro.alertas.dto.request.AlertFilter;
import edu.eci.dosw.campusseguro.alertas.dto.response.StateChangeResponse;
import edu.eci.dosw.campusseguro.alertas.entity.AlertConfirmationEntity;
import edu.eci.dosw.campusseguro.alertas.entity.AlertEntity;
import edu.eci.dosw.campusseguro.alertas.entity.AlertRecipientEntity;
import edu.eci.dosw.campusseguro.alertas.entity.NotificationDeliveryEntity;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertOrigin;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertType;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AuditAction;
import edu.eci.dosw.campusseguro.alertas.entity.enums.RecipientType;
import edu.eci.dosw.campusseguro.alertas.repository.AlertConfirmationRepository;
import edu.eci.dosw.campusseguro.alertas.repository.AlertRecipientRepository;
import edu.eci.dosw.campusseguro.alertas.repository.AlertRepository;
import edu.eci.dosw.campusseguro.alertas.repository.AlertSpecifications;
import edu.eci.dosw.campusseguro.alertas.repository.AuditEventRepository;
import edu.eci.dosw.campusseguro.alertas.repository.NotificationDeliveryRepository;
import edu.eci.dosw.campusseguro.alertas.security.local.LocalTokenService;
import edu.eci.dosw.campusseguro.alertas.service.AlertLifecycleService;
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
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(properties = {
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=file:database/001_create_schema.sql,file:database/002_indexes.sql,file:database/003_phase2_contract.sql",
        "app.alerts.activation-check-ms=3600000"
})
@AutoConfigureMockMvc
@ActiveProfiles({"test", "local"})
class AlertApiIntegrationIT {

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
    private AlertConfirmationRepository confirmationRepository;

    @Autowired
    private NotificationDeliveryRepository deliveryRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private AlertLifecycleService alertLifecycleService;

    @Autowired
    private LocalTokenService localTokenService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private EmailSender emailSender;

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
    void search_shouldFilterByStatusDateRangeAndSimulation() {
        alertRepository.save(newAlert(AlertStatus.DRAFT, null, Instant.parse("2026-10-01T10:00:00Z")));
        alertRepository.save(newAlert(AlertStatus.ACTIVE, null, Instant.parse("2026-10-05T10:00:00Z")));
        alertRepository.save(newAlert(
                AlertStatus.SCHEDULED, "sim-001", Instant.parse("2026-10-10T10:00:00Z")));

        AlertFilter filter = new AlertFilter(
                AlertStatus.ACTIVE,
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

        var bySimulation = alertRepository.findAll(
                AlertSpecifications.fromFilter(
                        new AlertFilter(null, null, null, "sim-001", null, null), ZoneOffset.UTC));
        assertEquals(1, bySimulation.size());
        assertEquals("sim-001", bySimulation.getFirst().getSimulationId());
    }

    @Test
    void search_shouldPageAndOrderByCreatedAtDescending() {
        for (int i = 0; i < 5; i++) {
            alertRepository.save(newAlert(
                    AlertStatus.DRAFT, null, Instant.parse("2026-10-0" + (i + 1) + "T10:00:00Z")));
        }

        var firstPage = alertRepository.findAll(
                AlertSpecifications.fromFilter(new AlertFilter(null, null, null, null, null, null), ZoneOffset.UTC),
                PageRequest.of(0, 2, Sort.by(Sort.Order.desc("createdAt"))));

        assertEquals(2, firstPage.getNumberOfElements());
        assertEquals(5, firstPage.getTotalElements());
        assertEquals(Instant.parse("2026-10-05T10:00:00Z"),
                firstPage.getContent().getFirst().getCreatedAt());
    }

    @Test
    void activation_shouldBeIdempotentAndNotifyOnlyOnce() throws Exception {
        AlertEntity alert = alertRepository.save(newAlert(AlertStatus.DRAFT, null, Instant.now()));
        recipientRepository.save(newRecipient(alert.getId(), "usr-001"));

        StateChangeResponse first = alertLifecycleService.activate(alert.getId(), "it-coordinator");
        StateChangeResponse second = alertLifecycleService.activate(alert.getId(), "it-coordinator");

        assertEquals(AlertStatus.DRAFT, first.estadoAnterior());
        assertEquals(AlertStatus.ACTIVE, first.estadoNuevo());
        assertEquals(AlertStatus.ACTIVE, second.estadoNuevo());
        assertEquals(AlertStatus.ACTIVE, alertRepository.findById(alert.getId()).orElseThrow().getStatus());
        assertEquals(1, auditEventRepository.findByAlertIdOrderByCreatedAtAsc(alert.getId()).stream()
                .filter(event -> event.getAction() == AuditAction.ACTIVATE)
                .count());

        verify(emailSender, timeout(5000)).send(any(EmailMessage.class));

        List<NotificationDeliveryEntity> deliveries = awaitDeliveries(alert.getId());
        assertEquals(1, deliveries.size());
        assertEquals("usr-001", deliveries.getFirst().getUserId());

        Thread.sleep(500);
        verify(emailSender, times(1)).send(any(EmailMessage.class));
        assertEquals(1, awaitDeliveries(alert.getId()).size());
    }

    @Test
    void activation_shouldReturn409_whenAnotherLiveAlertUsesTheSameSimulation() throws Exception {
        AlertEntity live = alertRepository.save(newAlert(AlertStatus.ACTIVE, "sim-conflict", Instant.now()));
        AlertEntity draft = alertRepository.save(newAlert(AlertStatus.DRAFT, "sim-conflict", Instant.now()));
        String token = localTokenService.generate("coordinator-1", List.of("COORDINATOR"));

        mockMvc.perform(patch("/api/v1/alertas/{id}/activar", draft.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SIMULATION_ALERT"));

        assertEquals(AlertStatus.ACTIVE, alertRepository.findById(live.getId()).orElseThrow().getStatus());
        assertEquals(AlertStatus.DRAFT, alertRepository.findById(draft.getId()).orElseThrow().getStatus());
    }

    @Test
    void uniqueIndex_shouldRejectSecondLiveAlertForSameSimulation() {
        alertRepository.saveAndFlush(newAlert(AlertStatus.SCHEDULED, "sim-live", Instant.now()));

        AlertEntity duplicate = newAlert(AlertStatus.ACTIVE, "sim-live", Instant.now());

        assertThrows(DataIntegrityViolationException.class, () -> alertRepository.saveAndFlush(duplicate));
    }

    @Test
    void uniqueIndex_shouldAllowDraftAndEndedAlertsForSameSimulation() {
        alertRepository.saveAndFlush(newAlert(AlertStatus.DRAFT, "sim-live", Instant.now()));
        alertRepository.saveAndFlush(newAlert(AlertStatus.SCHEDULED, "sim-live", Instant.now()));
        alertRepository.saveAndFlush(newAlert(AlertStatus.ENDED, "sim-live", Instant.now()));

        assertEquals(3, alertRepository.findAll().stream()
                .filter(alert -> "sim-live".equals(alert.getSimulationId()))
                .count());
    }

    @Test
    void confirmation_shouldBeUniquePerUserAndAlert() {
        AlertEntity alert = alertRepository.save(newAlert(AlertStatus.ACTIVE, null, Instant.now()));
        confirmationRepository.saveAndFlush(newConfirmation(alert.getId(), "usr-001"));

        assertThrows(DataIntegrityViolationException.class,
                () -> confirmationRepository.saveAndFlush(newConfirmation(alert.getId(), "usr-001")));
    }

    @Test
    void create_shouldReturnUnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/alertas"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void create_shouldForbidGenericUser() throws Exception {
        String token = localTokenService.generate("usr-001", List.of("STUDENT"));

        mockMvc.perform(post("/api/v1/alertas")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    void coordinator_shouldCreateAlertAndRecipientShouldViewIt() throws Exception {
        String coordinatorToken = localTokenService.generate("coordinator-1", List.of("COORDINATOR"));
        String recipientToken = localTokenService.generate("usr-001", List.of("STUDENT"));

        String location = mockMvc.perform(post("/api/v1/alertas")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + coordinatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson()))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, notNullValue()))
                .andExpect(jsonPath("$.id").value(notNullValue()))
                .andExpect(jsonPath("$.estado").value("DRAFT"))
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);

        assertNotNull(location);
        String path = location.substring(location.indexOf("/api/"));

        mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + recipientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.destinatarios", hasSize(1)))
                .andExpect(jsonPath("$.destinatarios[0].id").value("usr-001"));

        String strangerToken = localTokenService.generate("usr-999", List.of("STUDENT"));
        mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_A_RECIPIENT"));
    }

    @Test
    void recipient_shouldConfirmOnlyOnce() throws Exception {
        AlertEntity alert = alertRepository.save(newAlert(AlertStatus.ACTIVE, null, Instant.now()));
        recipientRepository.save(newRecipient(alert.getId(), "usr-001"));
        String token = localTokenService.generate("usr-001", List.of("STUDENT"));

        mockMvc.perform(post("/api/v1/alertas/{id}/confirmaciones", alert.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idAlerta").value(alert.getId().toString()));

        mockMvc.perform(post("/api/v1/alertas/{id}/confirmaciones", alert.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        assertEquals(1, confirmationRepository.findAll().size());
    }

    private List<NotificationDeliveryEntity> awaitDeliveries(UUID alertId) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            List<NotificationDeliveryEntity> deliveries = deliveryRepository.findByAlertId(alertId);
            if (!deliveries.isEmpty()) {
                return deliveries;
            }
            Thread.sleep(100);
        }
        return deliveryRepository.findByAlertId(alertId);
    }

    private AlertEntity newAlert(AlertStatus status, String simulationId, Instant createdAt) {
        AlertEntity alert = new AlertEntity();
        alert.setType(AlertType.EARTHQUAKE);
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

    private AlertConfirmationEntity newConfirmation(UUID alertId, String userId) {
        AlertConfirmationEntity confirmation = new AlertConfirmationEntity();
        confirmation.setAlertId(alertId);
        confirmation.setUserId(userId);
        confirmation.setConfirmedAt(Instant.now());
        return confirmation;
    }

    private String createRequestJson() {
        return """
                {
                  "tipo": "EARTHQUAKE",
                  "alcance": {"tipo": "CAMPUS", "ids": []},
                  "instrucciones": "Evacuar el campus",
                  "destinatarios": [{"tipo": "USER", "id": "usr-001"}]
                }
                """;
    }
}
