package edu.eci.dosw.campusseguro.alertas.controller;

import edu.eci.dosw.campusseguro.alertas.dto.response.AlertTypeResponse;
import edu.eci.dosw.campusseguro.alertas.entity.AlertTypeEntity;
import edu.eci.dosw.campusseguro.alertas.service.AlertTypeCatalog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
        value = "/api/v1/alertas",
        produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Tipos de alerta", description = "Catalogo de tipos de alerta")
public class AlertTypeController {

    private static final String CAN_VIEW_TYPES =
            "hasAnyAuthority('ms03:alert:manage', 'ms03:alert:manage_drill', "
                    + "'ms03:alert:receive')";

    private final AlertTypeCatalog alertTypeCatalog;

    public AlertTypeController(AlertTypeCatalog alertTypeCatalog) {
        this.alertTypeCatalog = alertTypeCatalog;
    }

    @GetMapping("/tipos")
    @PreAuthorize(CAN_VIEW_TYPES)
    @Operation(
            summary = "Listar tipos de alerta",
            description = "Devuelve el catalogo activo ordenado por displayOrder. MS06 es la "
                    + "fuente de verdad y MS03 conserva una replica local; OPERATIVA es un tipo "
                    + "reservado de MS03 y no se sincroniza.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tipos activos ordenados"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "Sin permiso")
    })
    public List<AlertTypeResponse> list() {
        return alertTypeCatalog.active().stream()
                .map(AlertTypeController::toResponse)
                .toList();
    }

    private static AlertTypeResponse toResponse(AlertTypeEntity type) {
        return new AlertTypeResponse(
                type.getCode(),
                type.getLabel(),
                type.getDisplayOrder());
    }
}
