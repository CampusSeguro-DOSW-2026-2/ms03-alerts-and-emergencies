package edu.eci.dosw.campusseguro.alertas.config;

import edu.eci.dosw.campusseguro.alertas.exception.ErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    OpenAPI campusSeguroAlertApi() {
        Map<String, Schema> schemas =
                ModelConverters.getInstance().read(ErrorResponse.class);

        return new OpenAPI()
                .info(new Info()
                        .title("MS03 - Alertas y Emergencias")
                        .version("1.0.0")
                        .description("API de gestión de alertas, ciclo de vida, "
                                + "confirmaciones y auditoría de CampusSeguro"))
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER_AUTH,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT"))
                        .addSchemas(
                                "ErrorResponse",
                                schemas.get("ErrorResponse")))
                .addSecurityItem(
                        new SecurityRequirement().addList(BEARER_AUTH));
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
