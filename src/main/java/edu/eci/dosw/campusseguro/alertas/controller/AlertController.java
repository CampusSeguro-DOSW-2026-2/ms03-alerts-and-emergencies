package edu.eci.dosw.campusseguro.alertas.controller;

import edu.eci.dosw.campusseguro.alertas.dto.request.AlertCreateRequest;
import edu.eci.dosw.campusseguro.alertas.dto.request.AlertFilter;
import edu.eci.dosw.campusseguro.alertas.dto.request.AlertScheduleRequest;
import edu.eci.dosw.campusseguro.alertas.dto.request.AlertUpdateRequest;
import edu.eci.dosw.campusseguro.alertas.dto.request.InboxStatus;
import edu.eci.dosw.campusseguro.alertas.dto.response.AlertResponse;
import edu.eci.dosw.campusseguro.alertas.dto.response.AlertSummaryResponse;
import edu.eci.dosw.campusseguro.alertas.dto.response.InboxAlertResponse;
import edu.eci.dosw.campusseguro.alertas.dto.response.PageResponse;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertOrigin;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertType;
import edu.eci.dosw.campusseguro.alertas.entity.enums.ScopeType;
import edu.eci.dosw.campusseguro.alertas.security.AccessActor;
import edu.eci.dosw.campusseguro.alertas.security.ActorResolver;
import edu.eci.dosw.campusseguro.alertas.security.RequesterContext;
import edu.eci.dosw.campusseguro.alertas.service.AlertCreationResult;
import edu.eci.dosw.campusseguro.alertas.service.AlertLifecycleService;
import edu.eci.dosw.campusseguro.alertas.service.AlertService;
import edu.eci.dosw.campusseguro.alertas.service.InboxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
        value = "/api/v1/alertas",
        produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Alertas", description = "Gestion y ciclo de vida de alertas")
public class AlertController {

    private static final String CAN_MANAGE_ALERTS =
            "hasAnyAuthority('ms03:alert:manage', 'ms03:alert:manage_drill')";

    private static final String CAN_READ_HISTORY =
            "hasAuthority('ms03:alert:read_history')";

    private static final String CAN_VIEW =
            "hasAnyAuthority('ms03:alert:receive', 'ms03:alert:read_history')";

    private final AlertService alertService;
    private final AlertLifecycleService alertLifecycleService;
    private final InboxService inboxService;
    private final ActorResolver actorResolver;

    public AlertController(
            AlertService alertService,
            AlertLifecycleService alertLifecycleService,
            InboxService inboxService,
            ActorResolver actorResolver) {
        this.alertService = alertService;
        this.alertLifecycleService = alertLifecycleService;
        this.inboxService = inboxService;
        this.actorResolver = actorResolver;
    }

    @PostMapping
    @PreAuthorize(CAN_MANAGE_ALERTS)
    @Operation(
            summary = "Crear alerta",
            description = "Crea una alerta manual o de simulacro con uno o varios "
                    + "tipos. Si ya existe una alerta SCHEDULED o ACTIVE para el "
                    + "mismo simulationId con los mismos datos, devuelve la "
                    + "existente con 200. Si los datos difieren, responde 409 "
                    + "SIMULACION_DUPLICADA.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Alerta creada"),
            @ApiResponse(responseCode = "200", description = "Alerta existente para el simulationId"),
            @ApiResponse(responseCode = "400", description = "Datos invalidos o fecha de activacion invalida"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "Sin permiso"),
            @ApiResponse(responseCode = "409", description = "Datos distintos para un simulationId con alerta viva"),
            @ApiResponse(responseCode = "503", description = "Servicio externo no disponible")
    })
    public ResponseEntity<AlertResponse> create(
            @Valid @RequestBody AlertCreateRequest request) {

        RequesterContext requester = actorResolver.requester();
        AlertCreationResult result =
                alertService.create(request, requester);

        if (!result.created()) {
            return ResponseEntity.ok(result.alert());
        }

        URI location = URI.create(
                "/api/v1/alertas/" + result.alert().id());

        return ResponseEntity.created(location).body(result.alert());
    }

    @GetMapping
    @PreAuthorize(CAN_READ_HISTORY)
    @Operation(summary = "Listar alertas", description = "Listado paginado con filtros.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listado paginado"),
            @ApiResponse(responseCode = "400", description = "Paginacion o filtros invalidos"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "Sin permiso")
    })
    public PageResponse<AlertSummaryResponse> search(
            @RequestParam(required = false) AlertStatus status,
            @RequestParam(name = "type", required = false)
            List<AlertType> types,
            @RequestParam(required = false) AlertOrigin origin,
            @RequestParam(required = false) ScopeType scope,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant activeAt,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate,
            @RequestParam(required = false) UUID simulationId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {

        AlertFilter filter = new AlertFilter(
                status,
                types,
                origin,
                scope,
                activeAt,
                startDate,
                endDate,
                simulationId,
                page,
                size);

        return alertService.search(filter);
    }

    @GetMapping("/mias")
    @PreAuthorize("hasAuthority('ms03:alert:receive')")
    @Operation(
            summary = "Consultar bandeja",
            description = "Devuelve las alertas dirigidas al actor por usuario, "
                    + "rol o entrega registrada. Por defecto solo ACTIVE; acepta "
                    + "ARCHIVED y ALL. Nunca devuelve DRAFT ni SCHEDULED.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bandeja paginada"),
            @ApiResponse(responseCode = "400", description = "Paginacion invalida"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "Sin permiso")
    })
    public PageResponse<InboxAlertResponse> inbox(
            @RequestParam(required = false) InboxStatus status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            Authentication authentication) {

        return inboxService.inbox(
                status,
                page,
                size,
                AccessActor.from(authentication));
    }

    @GetMapping("/{id}")
    @PreAuthorize(CAN_VIEW)
    @Operation(
            summary = "Consultar alerta",
            description = "Visible para roles elevados y para destinatarios autorizados "
                    + "por AlertAccessPolicy.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Alerta encontrada"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "El usuario no es destinatario"),
            @ApiResponse(responseCode = "404", description = "Alerta no encontrada")
    })
    public AlertResponse findById(
            @PathVariable UUID id,
            Authentication authentication) {

        return alertService.findById(
                id,
                AccessActor.from(authentication));
    }

    @PutMapping("/{id}")
    @PreAuthorize(CAN_MANAGE_ALERTS)
    @Operation(summary = "Editar alerta", description = "Solo alertas DRAFT o SCHEDULED.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Alerta actualizada"),
            @ApiResponse(responseCode = "400", description = "Datos invalidos"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "Sin permiso"),
            @ApiResponse(responseCode = "404", description = "Alerta no encontrada"),
            @ApiResponse(responseCode = "409", description = "Estado no editable o modificacion concurrente"),
            @ApiResponse(responseCode = "503", description = "Servicio externo no disponible")
    })
    public AlertResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody AlertUpdateRequest request) {

        return alertService.update(
                id,
                request,
                actorResolver.requester());
    }

    @PatchMapping("/{id}/programar")
    @PreAuthorize(CAN_MANAGE_ALERTS)
    @Operation(summary = "Programar alerta", description = "DRAFT -> SCHEDULED con fecha futura.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Alerta programada"),
            @ApiResponse(responseCode = "400", description = "Fecha de activacion invalida"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "Sin permiso"),
            @ApiResponse(responseCode = "404", description = "Alerta no encontrada"),
            @ApiResponse(responseCode = "409", description = "Transicion invalida")
    })
    public AlertResponse schedule(
            @PathVariable UUID id,
            @Valid @RequestBody AlertScheduleRequest request) {

        return alertLifecycleService.schedule(
                id,
                request,
                actorResolver.requester());
    }

    @PatchMapping("/{id}/activar")
    @PreAuthorize(CAN_MANAGE_ALERTS)
    @Operation(
            summary = "Activar alerta",
            description = "Permite DRAFT -> ACTIVE y SCHEDULED -> ACTIVE, incluso "
                    + "antes de la fecha programada. Si ya esta ACTIVE es idempotente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Alerta activa"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "Sin permiso"),
            @ApiResponse(responseCode = "404", description = "Alerta no encontrada"),
            @ApiResponse(responseCode = "409", description = "Transicion invalida")
    })
    public AlertResponse activate(@PathVariable UUID id) {
        return alertLifecycleService.activate(
                id,
                actorResolver.requester());
    }

    @PatchMapping("/{id}/archivar")
    @PreAuthorize(CAN_MANAGE_ALERTS)
    @Operation(
            summary = "Archivar alerta",
            description = "DRAFT, SCHEDULED o ACTIVE -> ARCHIVED. Es idempotente "
                    + "si ya esta ARCHIVED.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Alerta archivada"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "Sin permiso"),
            @ApiResponse(responseCode = "404", description = "Alerta no encontrada"),
            @ApiResponse(responseCode = "409", description = "Transicion invalida")
    })
    public AlertResponse archive(@PathVariable UUID id) {
        return alertLifecycleService.archive(
                id,
                actorResolver.requester());
    }
}
