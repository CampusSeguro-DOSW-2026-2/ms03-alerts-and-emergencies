package edu.eci.dosw.campusseguro.alertas.controller;

import edu.eci.dosw.campusseguro.alertas.dto.response.AlertFilterCatalogResponse;
import edu.eci.dosw.campusseguro.alertas.service.AlertFilterCatalog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
        value = "/api/v1/alertas",
        produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Filtros de alerta", description = "Catalogo de filtros del listado de alertas")
public class AlertFilterController {

    private static final String CAN_READ_HISTORY =
            "hasAuthority('ms03:alert:read_history')";

    private final AlertFilterCatalog alertFilterCatalog;

    public AlertFilterController(AlertFilterCatalog alertFilterCatalog) {
        this.alertFilterCatalog = alertFilterCatalog;
    }

    @GetMapping("/filtros")
    @PreAuthorize(CAN_READ_HISTORY)
    @Operation(
            summary = "Listar filtros de alerta",
            description = "Devuelve los grupos de filtros del listado con sus opciones. "
                    + "El grupo `status` es de seleccion unica y sale de `alert_status` "
                    + "ordenado por displayOrder; el grupo `type` es de seleccion multiple "
                    + "y sale de los tipos activos. Requiere `ms03:alert:read_history`, "
                    + "el mismo permiso del listado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Grupos de filtros con sus opciones"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "Sin permiso")
    })
    public AlertFilterCatalogResponse list() {
        return new AlertFilterCatalogResponse(alertFilterCatalog.groups());
    }
}
