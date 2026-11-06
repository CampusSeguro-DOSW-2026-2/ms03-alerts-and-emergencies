package edu.eci.dosw.campusseguro.alertas.controller;

import edu.eci.dosw.campusseguro.alertas.dto.response.ConfirmationResponse;
import edu.eci.dosw.campusseguro.alertas.security.AccessActor;
import edu.eci.dosw.campusseguro.alertas.service.ConfirmationResult;
import edu.eci.dosw.campusseguro.alertas.service.ConfirmationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
        value = "/api/v1/alertas",
        produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Confirmaciones", description = "Confirmacion de recepcion de alertas")
public class ConfirmationController {

    private final ConfirmationService confirmationService;

    public ConfirmationController(ConfirmationService confirmationService) {
        this.confirmationService = confirmationService;
    }

    @PostMapping("/{id}/confirmaciones")
    @PreAuthorize("hasAuthority('ms03:alert:receive')")
    @Operation(
            summary = "Confirmar recepcion",
            description = "Solo para destinatarios autorizados. La primera "
                    + "confirmacion devuelve 201; un reintento devuelve 200.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Confirmacion creada"),
            @ApiResponse(responseCode = "200", description = "Confirmacion ya existente"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "El usuario no es destinatario"),
            @ApiResponse(responseCode = "404", description = "Alerta no encontrada"),
            @ApiResponse(responseCode = "409", description = "La alerta no esta activa")
    })
    public ResponseEntity<ConfirmationResponse> confirm(
            @PathVariable UUID id,
            Authentication authentication) {

        ConfirmationResult result = confirmationService.confirm(
                id,
                AccessActor.from(authentication));

        HttpStatus status = result.created()
                ? HttpStatus.CREATED
                : HttpStatus.OK;

        return ResponseEntity.status(status).body(result.confirmation());
    }
}
