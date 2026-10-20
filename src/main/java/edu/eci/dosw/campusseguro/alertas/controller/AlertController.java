package edu.eci.dosw.campusseguro.alertas.controller;

import edu.eci.dosw.campusseguro.alertas.dto.request.AlertCreateRequest;
import edu.eci.dosw.campusseguro.alertas.dto.request.AlertEndRequest;
import edu.eci.dosw.campusseguro.alertas.dto.request.AlertFilter;
import edu.eci.dosw.campusseguro.alertas.dto.request.AlertUpdateRequest;
import edu.eci.dosw.campusseguro.alertas.dto.response.AlertResponse;
import edu.eci.dosw.campusseguro.alertas.dto.response.AlertSummaryResponse;
import edu.eci.dosw.campusseguro.alertas.dto.response.AuditEventResponse;
import edu.eci.dosw.campusseguro.alertas.dto.response.ConfirmationResponse;
import edu.eci.dosw.campusseguro.alertas.dto.response.PageResponse;
import edu.eci.dosw.campusseguro.alertas.dto.response.StateChangeResponse;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;
import edu.eci.dosw.campusseguro.alertas.security.AccessActor;
import edu.eci.dosw.campusseguro.alertas.security.ActorResolver;
import edu.eci.dosw.campusseguro.alertas.service.AlertCreationResult;
import edu.eci.dosw.campusseguro.alertas.service.AlertService;
import edu.eci.dosw.campusseguro.alertas.service.AuditQueryService;
import edu.eci.dosw.campusseguro.alertas.service.ConfirmationResult;
import edu.eci.dosw.campusseguro.alertas.service.ConfirmationService;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import edu.eci.dosw.campusseguro.alertas.dto.request.AlertScheduleRequest;
import edu.eci.dosw.campusseguro.alertas.service.AlertLifecycleService;

@RestController
@RequestMapping("/api/v1/alertas")
public class AlertController {

    private static final String COORDINATOR_ONLY = "hasRole('COORDINATOR')";
    private static final String COORDINATOR_OR_SERVICE = "hasAnyRole('COORDINATOR', 'SERVICE_MS06')";
    private static final String LIST_ACCESS = "hasAnyRole('COORDINATOR', 'AUDITOR')";

    private final AlertService alertService;
    private final ActorResolver actorResolver;
    private final AlertLifecycleService alertLifecycleService;
    private final AuditQueryService auditQueryService;
    private final ConfirmationService confirmationService;

    public AlertController(
            AlertService alertService,
            AlertLifecycleService alertLifecycleService,
            ActorResolver actorResolver,
            AuditQueryService auditQueryService,
            ConfirmationService confirmationService) {
        this.alertService = alertService;
        this.alertLifecycleService = alertLifecycleService;
        this.actorResolver = actorResolver;
        this.auditQueryService = auditQueryService;
        this.confirmationService = confirmationService;
    }

    @PostMapping
    @PreAuthorize(COORDINATOR_OR_SERVICE)
    public ResponseEntity<AlertResponse> create(@Valid @RequestBody AlertCreateRequest request) {
        AlertCreationResult result = alertService.create(request, actorResolver.resolve());
        if (!result.created()) {
            return ResponseEntity.ok(result.alert());
        }
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(result.alert().id())
                .toUri();
        return ResponseEntity.created(location).body(result.alert());
    }

    @GetMapping
    @PreAuthorize(LIST_ACCESS)
    public PageResponse<AlertSummaryResponse> search(
            @RequestParam(required = false) AlertStatus estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
            @RequestParam(required = false) String simulationId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return alertService.search(new AlertFilter(estado, fechaInicio, fechaFin, simulationId, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("!isAnonymous()")
    public AlertResponse findById(@PathVariable UUID id, Authentication authentication) {
        return alertService.findById(id, AccessActor.from(authentication));
    }

    @GetMapping("/{id}/auditoria")
    @PreAuthorize(LIST_ACCESS)
    public List<AuditEventResponse> audit(@PathVariable UUID id) {
        return auditQueryService.findByAlert(id);
    }

    @PostMapping("/{id}/confirmaciones")
    @PreAuthorize("!isAnonymous()")
    public ResponseEntity<ConfirmationResponse> confirm(@PathVariable UUID id, Authentication authentication) {
        ConfirmationResult result = confirmationService.confirm(id, AccessActor.from(authentication));
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.confirmation());
    }

    @PutMapping("/{id}")
    @PreAuthorize(COORDINATOR_ONLY)
    public AlertResponse update(@PathVariable UUID id, @Valid @RequestBody AlertUpdateRequest request) {
        return alertService.update(id, request, actorResolver.resolve());
    }
    
    @PatchMapping("/{id}/programar")
    @PreAuthorize(COORDINATOR_ONLY)
    public StateChangeResponse schedule(@PathVariable UUID id, @Valid @RequestBody AlertScheduleRequest request) {
        return alertLifecycleService.schedule(id, request, actorResolver.resolve());
    }

    @PatchMapping("/{id}/activar")
    @PreAuthorize(COORDINATOR_OR_SERVICE)
    public StateChangeResponse activate(@PathVariable UUID id) {
        return alertLifecycleService.activate(id, actorResolver.resolve());
    }

    @PatchMapping("/{id}/finalizar")
    @PreAuthorize(COORDINATOR_OR_SERVICE)
    public StateChangeResponse end(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) AlertEndRequest request) {
        return alertLifecycleService.end(id, request, actorResolver.resolve());
    }

    @PatchMapping("/{id}/archivar")
    @PreAuthorize(COORDINATOR_ONLY)
    public StateChangeResponse archive(@PathVariable UUID id) {
        return alertLifecycleService.archive(id, actorResolver.resolve());
    }



}
