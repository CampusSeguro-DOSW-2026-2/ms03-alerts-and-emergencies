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
                .setControllerAdvice(new GlobalExceptionHandler())
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
    void handleAlertNotFound_shouldReturn404WithSpanishCode_whenAlertDoesNotExist()
            throws Exception {

        performFailingWith(new AlertNotFoundException(ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.codigo").value("ALERTA_NO_ENCONTRADA"))
                .andExpect(jsonPath("$.mensaje")
                        .value("No se encontró la alerta con ID: " + ID))
                .andExpect(jsonPath("$.errores").isEmpty())
                .andExpect(jsonPath("$.correlation_id").exists());
    }

    @Test
    void handleInvalidAlertState_shouldReturn409WithSpanishCode_whenStateDoesNotAllowOperation()
            throws Exception {

        performFailingWith(
                new InvalidAlertStateException(AlertStatus.ACTIVE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.codigo").value("ESTADO_ALERTA_INVALIDO"))
                .andExpect(jsonPath("$.mensaje")
                        .value("La alerta está en estado ACTIVE "
                                + "y no permite esta operación"));
    }

    @Test
    void handleInvalidTransition_shouldReturn409WithSpanishCode_whenTransitionIsNotAllowed()
            throws Exception {

        performFailingWith(
                new InvalidTransitionException(
                        AlertStatus.ACTIVE, AlertStatus.DRAFT))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"))
                .andExpect(jsonPath("$.mensaje")
                        .value("No se puede cambiar la alerta de ACTIVE a DRAFT"));
    }

    @Test
    void handleInvalidActivationDate_shouldReturn400WithSpanishCode_whenDateIsNotValid()
            throws Exception {

        performFailingWith(new InvalidActivationDateException())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("FECHA_ACTIVACION_INVALIDA"))
                .andExpect(jsonPath("$.mensaje")
                        .value("La fecha de activación debe ser futura"));
    }

    @Test
    void handleInvalidReference_shouldReturn400WithOneDetailPerInvalidId_whenReferencesDoNotExist()
            throws Exception {

        performFailingWith(
                new InvalidReferenceException(
                        "alcance.ids", List.of("B1", "B2")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("REFERENCIA_INVALIDA"))
                .andExpect(jsonPath("$.mensaje")
                        .value(ErrorCode.INVALID_REFERENCE.defaultMessage()))
                .andExpect(jsonPath("$.errores.length()").value(2))
                .andExpect(jsonPath("$.errores[0].campo")
                        .value("alcance.ids"))
                .andExpect(jsonPath("$.errores[0].mensaje")
                        .value("La referencia no existe: B1"))
                .andExpect(jsonPath("$.errores[1].campo")
                        .value("alcance.ids"))
                .andExpect(jsonPath("$.errores[1].mensaje")
                        .value("La referencia no existe: B2"));
    }

    @Test
    void handleInvalidPagination_shouldReturn400WithFieldDetail_whenQueryParamsAreInvalid()
            throws Exception {

        performFailingWith(
                new InvalidPaginationException(
                        "size", "Page size must be between 1 and 100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PAGINACION_INVALIDA"))
                .andExpect(jsonPath("$.mensaje")
                        .value(ErrorCode.INVALID_PAGINATION.defaultMessage()))
                .andExpect(jsonPath("$.errores.length()").value(1))
                .andExpect(jsonPath("$.errores[0].campo").value("size"))
                .andExpect(jsonPath("$.errores[0].mensaje")
                        .value("Page size must be between 1 and 100"));
    }

    @Test
    void handleExternalServiceUnavailable_shouldReturn503_whenExternalServiceIsDown()
            throws Exception {

        performFailingWith(
                new ExternalServiceUnavailableException("catalogo"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo")
                        .value("SERVICIO_EXTERNO_NO_DISPONIBLE"))
                .andExpect(jsonPath("$.mensaje")
                        .value(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE.defaultMessage()));
    }

    @Test
    void handleForbiddenOperation_shouldReturn403WithOperationMessage_whenOperationIsNotAllowed()
            throws Exception {

        performFailingWith(
                new ForbiddenOperationException(
                        "Los organizadores solo pueden crear alertas de simulacro"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"))
                .andExpect(jsonPath("$.mensaje")
                        .value("Los organizadores solo pueden crear alertas de simulacro"));
    }

    @Test
    void handleOptimisticLocking_shouldReturn409WithDefaultMessage_whenAlertWasModifiedConcurrently()
            throws Exception {

        performFailingWith(
                new OptimisticLockingFailureException(
                        "row was updated by another transaction"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("MODIFICACION_CONCURRENTE"))
                .andExpect(jsonPath("$.mensaje")
                        .value(ErrorCode.CONCURRENT_MODIFICATION.defaultMessage()));
    }

    // ---------- Simulation exceptions ----------

    @Test
    void handleSimulationMismatch_shouldReturn409WithSpanishCode_whenSimulationDoesNotMatchAlert()
            throws Exception {

        performFailingWith(new SimulationMismatchException(ID, EVENT_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.codigo").value("CONFLICTO_SIMULACION"))
                .andExpect(jsonPath("$.mensaje")
                        .value("La alerta " + ID
                                + " no corresponde al simulacro " + EVENT_ID));
    }

    @Test
    void handleDuplicateSimulationAlert_shouldReturn409WithSpanishCode_whenLiveAlertAlreadyExists()
            throws Exception {

        performFailingWith(
                new DuplicateSimulationAlertException(EVENT_ID, OTHER_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.codigo").value("SIMULACION_DUPLICADA"))
                .andExpect(jsonPath("$.mensaje")
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
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores.length()").value(1))
                .andExpect(jsonPath("$.errores[0].campo").value("name"))
                .andExpect(jsonPath("$.errores[0].mensaje")
                        .value("must not be blank"));
    }

    @Test
    void handleAccessDenied_shouldReturn403WithDefaultMessage_whenUserHasNoPermission()
            throws Exception {

        performFailingWith(new AccessDeniedException("role missing"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"))
                .andExpect(jsonPath("$.mensaje")
                        .value(ErrorCode.FORBIDDEN.defaultMessage()));
    }

    @Test
    void handleAuthentication_shouldReturn401WithDefaultMessage_whenAuthenticationFails()
            throws Exception {

        performFailingWith(new BadCredentialsException("bad token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.mensaje")
                        .value(ErrorCode.UNAUTHORIZED.defaultMessage()));
    }

    @Test
    void handleUnexpected_shouldReturn500WithoutLeakingExceptionMessage_whenExceptionIsNotHandled()
            throws Exception {

        performFailingWith(
                new IllegalStateException("secret internal detail"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.codigo").value("ERROR_INTERNO"))
                .andExpect(jsonPath("$.mensaje")
                        .value(ErrorCode.INTERNAL_ERROR.defaultMessage()))
                .andExpect(jsonPath("$.errores").isEmpty());
    }

    // ---------- Spring MVC exceptions ----------

    @Test
    void handleMethodArgumentNotValid_shouldReturn400WithFieldDetails_whenBodyFailsValidation()
            throws Exception {

        mockMvc.perform(post("/test/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.mensaje")
                        .value(ErrorCode.VALIDATION_ERROR.defaultMessage()))
                .andExpect(jsonPath("$.errores.length()").value(1))
                .andExpect(jsonPath("$.errores[0].campo").value("name"));
    }

    @Test
    void handleHttpMessageNotReadable_shouldReturn400_whenBodyIsMalformedJson()
            throws Exception {

        mockMvc.perform(post("/test/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.mensaje")
                        .value("El cuerpo de la solicitud falta, está mal formado "
                                + "o contiene valores inválidos"));
    }

    @Test
    void handleTypeMismatch_shouldReturn400WithParamName_whenPathVariableHasWrongType()
            throws Exception {

        mockMvc.perform(get("/test/typed/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.errores.length()").value(1))
                .andExpect(jsonPath("$.errores[0].campo").value("id"))
                .andExpect(jsonPath("$.errores[0].mensaje")
                        .value("El valor enviado no es válido"));
    }

    @Test
    void handleExceptionInternal_shouldReturn405WithSpanishCode_whenHttpMethodIsNotSupported()
            throws Exception {

        mockMvc.perform(post("/test/only-get"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.codigo").value("METODO_NO_PERMITIDO"))
                .andExpect(jsonPath("$.mensaje")
                        .value(ErrorCode.METHOD_NOT_ALLOWED.defaultMessage()));
    }

    @Test
    void handleExceptionInternal_shouldReturn415WithSpanishCode_whenContentTypeIsNotSupported()
            throws Exception {

        mockMvc.perform(post("/test/body")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("name"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.codigo").value("TIPO_NO_SOPORTADO"))
                .andExpect(jsonPath("$.mensaje")
                        .value(ErrorCode.UNSUPPORTED_MEDIA_TYPE.defaultMessage()));
    }

    @Test
    void handleExceptionInternal_shouldReturn404WithSpanishCode_whenNoHandlerMatchesThePath()
            throws Exception {

        mockMvc.perform(get("/test/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"))
                .andExpect(jsonPath("$.mensaje")
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
