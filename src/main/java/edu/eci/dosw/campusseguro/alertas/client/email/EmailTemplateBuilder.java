package edu.eci.dosw.campusseguro.alertas.client.email;

import edu.eci.dosw.campusseguro.alertas.client.catalog.ResolvedUser;
import edu.eci.dosw.campusseguro.alertas.entity.AlertEntity;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
public class EmailTemplateBuilder {

    private final String appBaseUrl;

    public EmailTemplateBuilder(@Value("${app.mail.app-base-url:http://localhost:5173}") String appBaseUrl) {
        this.appBaseUrl = appBaseUrl.endsWith("/")
                ? appBaseUrl.substring(0, appBaseUrl.length() - 1)
                : appBaseUrl;
    }

    public EmailMessage build(AlertEntity alert, ResolvedUser recipient) {
        String link = appBaseUrl + "/alertas/" + alert.getId() + "/ver";
        String type = typeLabel(alert.getType());
        String instructions = alert.getInstructions();
        String safeInstructions = HtmlUtils.htmlEscape(instructions, "UTF-8");
        String safeType = HtmlUtils.htmlEscape(type, "UTF-8");
        String safeLink = HtmlUtils.htmlEscape(link, "UTF-8");
        String safeName = HtmlUtils.htmlEscape(recipient.name() == null ? "" : recipient.name(), "UTF-8");

        String subject = "Alerta CampusSeguro: " + type;
        String html = """
                <!doctype html>
                <html lang="es">
                <body>
                  <p>Hola %s,</p>
                  <p>Se activó una alerta de <strong>%s</strong> en CampusSeguro.</p>
                  <p>%s</p>
                  <p><a href="%s">Ver y confirmar alerta</a></p>
                </body>
                </html>
                """.formatted(safeName, safeType, safeInstructions, safeLink);
        String text = """
                Hola %s,

                Se activó una alerta de %s en CampusSeguro.

                %s

                Ver y confirmar alerta: %s
                """.formatted(recipient.name() == null ? "" : recipient.name(), type, instructions, link);

        return new EmailMessage(recipient.email(), recipient.name(), subject, html, text);
    }

    private String typeLabel(AlertType type) {
        return switch (type) {
            case EARTHQUAKE -> "Terremoto";
            case SIMULATION -> "Simulacro";
            case FIRE -> "Incendio";
            case OTHER -> "Otra emergencia";
        };
    }
}
