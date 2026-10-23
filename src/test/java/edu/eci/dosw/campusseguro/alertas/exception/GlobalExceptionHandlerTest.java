
package edu.eci.dosw.campusseguro.alertas.exception;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tests GlobalExceptionHandler through a standalone MockMvc and a throwaway controller
 * that throws whatever exception each test needs.
 */
class GlobalExceptionHandlerTest {

    private static final Instant NOW =
            Instant.parse("2026-10-15T18:00:00Z");

    private static final UUID ID =
            UUID.fromString("66666666-6666-6666-6666-666666666666");

    private static final UUID EVENT_ID =
            UUID.fromString("88888888-8888-8888-8888-888888888888");

    private static final UUID OTHER_ID =
            UUID.fromString("99999999-9999-9999-9999-999999999999");

    private static final String FAIL_URL = "/test/fail";

    private ThrowingController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        controller = new ThrowingController();

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(
                        new GlobalExceptionHandler(
                                Clock.fixed(NOW, ZoneOffset.UTC)))
                .build();
    }

    // ---------- Helpers ----------

    private ResultActions performFailingWith(Exception exception)
            throws Exception {
        controller.toThrow = exception;
        return mockMvc.perform(get(FAIL_URL));
    }

    // ---------- Domain exceptions ----------

    @Test
    void handleAlertNotFound_shouldReturn404WithAlertNotFoundCode_whenAlertDoesNotExist()
            throws Exception {

        performFailingWith(new AlertNotFoundException(ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("ALERT_NOT_FOUND"))
                .andExpect(jsonPath("$.message")
                        .value("Alert not found with ID: " + ID))
                .andExpect(jsonPath("$.path").value(FAIL_URL))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.details").isEmpty());
    }

    @Test
    void handleInvalidAlertState_shouldReturn409WithInvalidAlertStateCode_whenStateDoesNotAllowOperation()
            throws Exception {

        performFailingWith(
                new InvalidAlertStateException(AlertStatus.ACTIVE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("INVALID_ALERT_STATE"))
                .andExpect(jsonPath("$.message")
                        .value("The alert is in status ACTIVE and does not allow this operation"))
                .andExpect(jsonPath("$.path").value(FAIL_URL));
    }

    @Test
    void handleInvalidTransition_shouldReturn409WithInvalidTransitionCode_whenTransitionIsNotAllowed()
            throws Exception {

        performFailingWith(
                new InvalidTransitionException(
                        AlertStatus.ACTIVE, AlertStatus.DRAFT))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"))
                .andExpect(jsonPath("$.message")
                        .value("Cannot transition the alert from ACTIVE to DRAFT"));
    }

    @Test
    void handleInvalidActivationDate_shouldReturn400WithInvalidActivationDateCode_whenDateIsNotValid()
            throws Exception {

        performFailingWith(new InvalidActivationDateException())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ACTIVATION_DATE"))
                .andExpect(jsonPath("$.message")
                        .value("The activation date and time must be in the future"));
    }

    @Test
    void handleInvalidReference_shouldReturn400WithOneDetailPerInvalidId_whenReferencesDoNotExist()
            throws Exception {

        performFailingWith(
                new InvalidReferenceException(
                        "alcance.ids", List.of("B1", "B2")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REFERENCE"))
                .andExpect(jsonPath("$.message")
                        .value(ErrorCode.INVALID_REFERENCE.defaultMessage()))
                .andExpect(jsonPath("$.details.length()").value(2))
                .andExpect(jsonPath("$.details[0].field")
                        .value("alcance.ids"))
                .andExpect(jsonPath("$.details[0].message")
                        .value("The referenced ID does not exist: B1"))
                .andExpect(jsonPath("$.details[1].field")
                        .value("alcance.ids"))
                .andExpect(jsonPath("$.details[1].message")
                        .value("The referenced ID does not exist: B2"));
    }

    @Test
    void handleInvalidPagination_shouldReturn400WithFieldDetail_whenQueryParamsAreInvalid()
            throws Exception {

        performFailingWith(
                new InvalidPaginationException(
                        "size", "Page size must be between 1 and 100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAGINATION"))
                .andExpect(jsonPath("$.message")
                        .value(ErrorCode.INVALID_PAGINATION.defaultMessage()))
                .andExpect(jsonPath("$.details.length()").value(1))
                .andExpect(jsonPath("$.details[0].field").value("size"))
                .andExpect(jsonPath("$.details[0].message")
                        .value("Page size must be between 1 and 100"));
    }

    @Test
    void handleExternalServiceUnavailable_shouldReturn503_whenExternalServiceIsDown()
            throws Exception {

        performFailingWith(
                new ExternalServiceUnavailableException("catalogo"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code")
                        .value("EXTERNAL_SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message")
                        .value("Service catalogo is unavailable. Please try again later."));
    }

    @Test
    void handleOptimisticLocking_shouldReturn409WithDefaultMessage_whenAlertWasModifiedConcurrently()
            throws Exception {

        performFailingWith(
                new OptimisticLockingFailureException(
                        "row was updated by another transaction"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"))
                .andExpect(jsonPath("$.message")
                        .value(ErrorCode.CONCURRENT_MODIFICATION.defaultMessage()));
    }

    // ---------- Simulation exceptions ----------

    @Test
    void handleSimulationMismatch_shouldReturn409WithSimulationMismatchCode_whenSimulationDoesNotMatchAlert()
            throws Exception {

        performFailingWith(new SimulationMismatchException(ID, EVENT_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("SIMULATION_MISMATCH"))
                .andExpect(jsonPath("$.message")
                        .value("La alerta " + ID
                                + " no corresponde al simulacro " + EVENT_ID));
    }

    @Test
    void handleDuplicateSimulationAlert_shouldReturn409WithDuplicateCode_whenLiveAlertAlreadyExists()
            throws Exception {

        performFailingWith(
                new DuplicateSimulationAlertException(EVENT_ID, OTHER_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("DUPLICATE_SIMULATION_ALERT"))
                .andExpect(jsonPath("$.message")
                        .value("Ya existe una alerta viva para el simulacro "
                                + EVENT_ID + ": " + OTHER_ID));
    }

    // ---------- Validation / security / unexpected exceptions ----------

    @Test
    void handleConstraintViolation_shouldReturn400WithOneDetailPerViolation_whenConstraintsAreViolated()
            throws Exception {

        Path path = mock(Path.class);
        when(path.toString()).thenReturn("name");

        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> violation =
                mock(ConstraintViolation.class);

        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("must not be blank");

        performFailingWith(
                new ConstraintViolationException(Set.of(violation)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.length()").value(1))
                .andExpect(jsonPath("$.details[0].field").value("name"))
                .andExpect(jsonPath("$.details[0].message")
                        .value("must not be blank"));
    }

    @Test
    void handleAccessDenied_shouldReturn403WithDefaultMessage_whenUserHasNoPermission()
            throws Exception {

        performFailingWith(new AccessDeniedException("role missing"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message")
                        .value(ErrorCode.FORBIDDEN.defaultMessage()));
    }

    @Test
    void handleAuthentication_shouldReturn401WithDefaultMessage_whenAuthenticationFails()
            throws Exception {

        performFailingWith(new BadCredentialsException("bad token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message")
                        .value(ErrorCode.UNAUTHORIZED.defaultMessage()));
    }

    @Test
    void handleUnexpected_shouldReturn500WithoutLeakingExceptionMessage_whenExceptionIsNotHandled()
            throws Exception {

        performFailingWith(
                new IllegalStateException("secret internal detail"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value(ErrorCode.INTERNAL_ERROR.defaultMessage()))
                .andExpect(jsonPath("$.details").isEmpty());
    }

    // ---------- Spring MVC exceptions ----------

    @Test
    void handleMethodArgumentNotValid_shouldReturn400WithFieldDetails_whenBodyFailsValidation()
            throws Exception {

        mockMvc.perform(post("/test/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value(ErrorCode.VALIDATION_ERROR.defaultMessage()))
                .andExpect(jsonPath("$.path").value("/test/body"))
                .andExpect(jsonPath("$.details.length()").value(1))
                .andExpect(jsonPath("$.details[0].field").value("name"));
    }

    @Test
    void handleHttpMessageNotReadable_shouldReturn400_whenBodyIsMalformedJson()
            throws Exception {

        mockMvc.perform(post("/test/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value("The request body is missing, malformed, or contains invalid values"))
                .andExpect(jsonPath("$.path").value("/test/body"));
    }

    @Test
    void handleTypeMismatch_shouldReturn400WithParamName_whenPathVariableHasWrongType()
            throws Exception {

        mockMvc.perform(get("/test/typed/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.length()").value(1))
                .andExpect(jsonPath("$.details[0].field").value("id"))
                .andExpect(jsonPath("$.details[0].message")
                        .value("The supplied value is invalid"));
    }

    @Test
    void handleExceptionInternal_shouldReturn405WithMethodNotAllowedCode_whenHttpMethodIsNotSupported()
            throws Exception {

        mockMvc.perform(post("/test/only-get"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.message")
                        .value(ErrorCode.METHOD_NOT_ALLOWED.defaultMessage()))
                .andExpect(jsonPath("$.path").value("/test/only-get"));
    }

    @Test
    void handleExceptionInternal_shouldReturn415WithUnsupportedMediaTypeCode_whenContentTypeIsNotSupported()
            throws Exception {

        mockMvc.perform(post("/test/body")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("name"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(jsonPath("$.message")
                        .value(ErrorCode.UNSUPPORTED_MEDIA_TYPE.defaultMessage()));
    }

    @Test
    void handleExceptionInternal_shouldReturn404WithResourceNotFoundCode_whenNoHandlerMatchesThePath()
            throws Exception {

        mockMvc.perform(get("/test/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message")
                        .value(ErrorCode.RESOURCE_NOT_FOUND.defaultMessage()));
    }

    // ---------- Throwaway controller ----------

    @RestController
    @RequestMapping("/test")
    static class ThrowingController {

        private Exception toThrow;

        @GetMapping("/fail")
        void fail() throws Exception {
            throw toThrow;
        }

        @GetMapping("/typed/{id}")
        String typed(@PathVariable UUID id) {
            return id.toString();
        }

        @GetMapping("/only-get")
        String onlyGet() {
            return "ok";
        }

        @PostMapping("/body")
        String body(@Valid @RequestBody Body body) {
            return body.name();
        }
    }

    record Body(@NotBlank String name) {
    }
}
