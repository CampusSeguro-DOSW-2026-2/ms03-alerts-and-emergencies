package edu.eci.dosw.campusseguro.alertas.controller;

import edu.eci.dosw.campusseguro.alertas.dto.request.AlertCreateRequest;
import edu.eci.dosw.campusseguro.alertas.dto.request.AlertFilter;
import edu.eci.dosw.campusseguro.alertas.dto.request.AlertUpdateRequest;
import edu.eci.dosw.campusseguro.alertas.dto.response.AlertResponse;
import edu.eci.dosw.campusseguro.alertas.dto.response.AlertSummaryResponse;
import edu.eci.dosw.campusseguro.alertas.dto.response.PageResponse;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertStatus;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertType;
import edu.eci.dosw.campusseguro.alertas.security.ActorResolver;
import edu.eci.dosw.campusseguro.alertas.service.AlertService;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
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
    private static final String READ_ACCESS = "hasAnyRole('COORDINATOR', 'AUDITOR', 'SERVICE_MS06')";

    private final AlertService alertService;
    private final ActorResolver actorResolver;

    private final AlertLifecycleService alertLifecycleService;

    public AlertController(AlertService alertService, AlertLifecycleService alertLifecycleService, ActorResolver actorResolver) {
        this.alertService = alertService;
        this.alertLifecycleService = alertLifecycleService;
        this.actorResolver = actorResolver;
    }

    @PostMapping
    @PreAuthorize(COORDINATOR_ONLY)
    public ResponseEntity<AlertResponse> create(@Valid @RequestBody AlertCreateRequest request) {
        AlertResponse response = alertService.create(request, actorResolver.resolve());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    @PreAuthorize(READ_ACCESS)
    public PageResponse<AlertSummaryResponse> search(
            @RequestParam(required = false) AlertStatus estado,
            @RequestParam(required = false) AlertType tipo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
            @RequestParam(required = false) String simulationId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return alertService.search(new AlertFilter(estado, tipo, fechaInicio, fechaFin, simulationId, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ_ACCESS)
    public AlertResponse findById(@PathVariable UUID id) {
        return alertService.findById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize(COORDINATOR_ONLY)
    public AlertResponse update(@PathVariable UUID id, @Valid @RequestBody AlertUpdateRequest request) {
        return alertService.update(id, request, actorResolver.resolve());
    }
    
    @PutMapping("/{id}/programacion")
    @PreAuthorize(COORDINATOR_ONLY)
    public AlertResponse schedule(@PathVariable UUID id, @Valid @RequestBody AlertScheduleRequest request) {
        return alertLifecycleService.schedule(id, request, actorResolver.resolve());
    }

    @PutMapping("/{id}/activacion")
    @PreAuthorize(COORDINATOR_ONLY)
    public AlertResponse activate(@PathVariable UUID id) {
        return alertLifecycleService.activate(id, actorResolver.resolve());
    }

    
    @PutMapping("/{id}/finalizacion")
    @PreAuthorize(COORDINATOR_ONLY)
    public AlertResponse end(@PathVariable UUID id) {
        return alertLifecycleService.end(id, actorResolver.resolve());
    }

    @PutMapping("/{id}/archivado")
    @PreAuthorize(COORDINATOR_ONLY)
    public AlertResponse archive(@PathVariable UUID id) {
        return alertLifecycleService.archive(id, actorResolver.resolve());
    }



}
