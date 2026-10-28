package edu.eci.dosw.campusseguro.alertas.config;

import edu.eci.dosw.campusseguro.alertas.exception.ErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI campusSeguroAlertApi() {
        Map<String, Schema> schemas =
                ModelConverters.getInstance().read(ErrorResponse.class);

        return new OpenAPI()
                .info(new Info()
                        .title("MS03 - Alertas y Emergencias")
                        .version("2.0.0")
                        .description("API de gestión de alertas, ciclo de vida, "
                                + "confirmaciones y auditoría de CampusSeguro. "
                                + "La identidad llega del API Gateway en cabeceras; "
                                + "MS03 no valida JWT."))
                .components(new Components()
                        .addParameters("UserIdHeader", identityHeader(
                                "X-User-Id", "UUID del usuario emisor.", true))
                        .addParameters("UserRoleHeader", identityHeader(
                                "X-User-Role", "Roles oficiales separados por coma.", true))
                        .addParameters("ServiceNameHeader", identityHeader(
                                "X-Service-Name", "Origen de servicio: ms03 o ms06.", false))
                        .addParameters("CorrelationIdHeader", correlationHeader())
                        .addSchemas(
                                "ErrorResponse",
                                schemas.get("ErrorResponse")));
    }

    @Bean
    OpenApiCustomizer identityHeadersCustomizer() {
        List<Parameter> headers = List.of(
                new Parameter().$ref("#/components/parameters/UserIdHeader"),
                new Parameter().$ref("#/components/parameters/UserRoleHeader"),
                new Parameter().$ref("#/components/parameters/ServiceNameHeader"),
                new Parameter().$ref("#/components/parameters/CorrelationIdHeader"));

        return openApi -> openApi.getPaths().values().forEach(path ->
                path.readOperations().forEach(operation ->
                        headers.forEach(operation::addParametersItem)));
    }

    private static Parameter identityHeader(String name, String description, boolean required) {
        return new Parameter()
                .in("header")
                .name(name)
                .description(description)
                .required(required)
                .schema(new StringSchema());
    }

    private static Parameter correlationHeader() {
        return new Parameter()
                .in("header")
                .name("X-Correlation-ID")
                .description("UUID v4 de correlación. Si falta o no es válido, "
                        + "MS03 genera uno y lo devuelve en la respuesta.")
                .required(false)
                .schema(new StringSchema());
    }

    @Bean
    OpenApiCustomizer errorResponsesCustomizer() {
        return openApi -> {
            Schema<?> errorSchema = new Schema<>()
                    .$ref("#/components/schemas/ErrorResponse");
            Content errorContent = new Content().addMediaType(
                    "application/json",
                    new MediaType().schema(errorSchema));

            openApi.getPaths().values().forEach(path ->
                    path.readOperations().forEach(operation ->
                            operation.getResponses().forEach(
                                    (status, response) -> {
                                        if (!status.startsWith("2")) {
                                            response.setContent(errorContent);
                                        }
                                    })));
        };
    }
}
