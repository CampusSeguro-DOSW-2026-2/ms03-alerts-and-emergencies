package edu.eci.dosw.campusseguro.alertas.controller;

import edu.eci.dosw.campusseguro.alertas.dto.response.AuditEventResponse;
import edu.eci.dosw.campusseguro.alertas.service.AuditQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
        value = "/api/v1/alertas",
        produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Auditoria", description = "Consulta de eventos de auditoria")
public class AuditController {

    private final AuditQueryService auditQueryService;

    public AuditController(AuditQueryService auditQueryService) {
        this.auditQueryService = auditQueryService;
    }

    @GetMapping("/{id}/auditoria")
    @PreAuthorize("hasAnyRole('AUDITOR', 'COORDINATOR')")
    @Operation(summary = "Consultar auditoria", description = "Eventos de auditoria de la alerta.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Eventos de auditoria"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "Sin permiso"),
            @ApiResponse(responseCode = "404", description = "Alerta no encontrada")
    })
    public List<AuditEventResponse> findByAlert(@PathVariable UUID id) {
        return auditQueryService.findByAlert(id);
    }
}
