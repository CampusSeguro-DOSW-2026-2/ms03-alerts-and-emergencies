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
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(properties = {
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=file:database/001_create_schema.sql,file:database/002_indexes.sql,file:database/003_phase2_contract.sql"
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
    private AuditEventRepository auditEventRepository;

    @Autowired
    private AlertLifecycleService alertLifecycleService;

    @Autowired
    private LocalTokenService localTokenService;

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
    void search_shouldFilterByEventId() {
        UUID eventId = UUID.randomUUID();
        alertRepository.save(newAlert(AlertStatus.DRAFT, Instant.now(), eventId));
        alertRepository.save(newAlert(AlertStatus.DRAFT, Instant.now(), null));

        var result = alertRepository.findAll(
                AlertSpecifications.fromFilter(
                        new AlertFilter(null, null, null, null, eventId, null, null), ZoneOffset.UTC));

        assertEquals(1, result.size());
        assertEquals(eventId, result.getFirst().getEventId());
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
    void scheduledAlert_shouldRejectManualActivationBeforeItsDate() {
        AlertEntity alert = newAlert(AlertStatus.SCHEDULED, Instant.now(), null);
        alert.setActivationAt(Instant.now().plusSeconds(3600));
        AlertEntity saved = alertRepository.save(alert);

        assertThrows(IllegalStateException.class,
                () -> alertLifecycleService.activate(saved.getId(), "it-coordinator"));
    }

    @Test
    void uniqueConstraint_shouldRejectDuplicatedConfirmation() {
        AlertEntity alert = alertRepository.save(newAlert(AlertStatus.ACTIVE, Instant.now(), null));
        jdbcTemplate.update(
                "INSERT INTO alert_confirmations (alert_id, user_id, confirmed_at) VALUES (?, ?, now())",
                alert.getId(), "usr-001");

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO alert_confirmations (alert_id, user_id, confirmed_at) VALUES (?, ?, now())",
                alert.getId(), "usr-001"));
    }

    @Test
    void create_shouldReturnUnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(post("/api/v1/alertas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson()))
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
    void coordinator_shouldCreateAlertAndAuditorShouldViewIt() throws Exception {
        String coordinatorToken = localTokenService.generate("coordinator-1", List.of("COORDINATOR"));
        String auditorToken = localTokenService.generate("auditor-1", List.of("AUDITOR"));

        String location = mockMvc.perform(post("/api/v1/alertas")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + coordinatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson()))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, notNullValue()))
                .andExpect(jsonPath("$.id").value(notNullValue()))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.recipients", hasSize(1)))
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);

        assertNotNull(location);
        String path = location.substring(location.indexOf("/api/"));

        mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + auditorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipients[0].id").value("usr-001"));
    }

    private AlertEntity newAlert(AlertStatus status, Instant createdAt, UUID eventId) {
        AlertEntity alert = new AlertEntity();
        alert.setType(AlertType.EARTHQUAKE);
        alert.setTitle("Simulacro sismico");
        alert.setStatus(status);
        alert.setInstructions("Evacuar el campus");
        alert.setOrigin(eventId == null ? AlertOrigin.MANUAL : AlertOrigin.EVENT);
        alert.setEventId(eventId);
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
                  "recipients": [{"type": "USER", "id": "usr-001"}]
                }
                """;
    }
}
