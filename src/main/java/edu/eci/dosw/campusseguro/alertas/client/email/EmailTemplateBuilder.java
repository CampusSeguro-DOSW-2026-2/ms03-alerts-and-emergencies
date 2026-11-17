package edu.eci.dosw.campusseguro.alertas.client.email;

import edu.eci.dosw.campusseguro.alertas.client.catalog.ResolvedUser;
import edu.eci.dosw.campusseguro.alertas.entity.AlertEntity;
import edu.eci.dosw.campusseguro.alertas.service.AlertTypeCatalog;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
public class EmailTemplateBuilder {

    private static final String MESSAGE_BASE =
            "Has recibido una alerta oficial de CampusSeguro. "
                    + "Confirma la recepción y sigue los pasos indicados: ";

    private final String appBaseUrl;

    private final AlertTypeCatalog alertTypeCatalog;

    public EmailTemplateBuilder(
            @Value("${app.mail.app-base-url:http://localhost:5173}")
            String appBaseUrl,
            AlertTypeCatalog alertTypeCatalog) {

        this.alertTypeCatalog = alertTypeCatalog;
        this.appBaseUrl = appBaseUrl == null
                ? ""
                : appBaseUrl.strip().replaceAll("/+$", "");
    }

    public EmailMessage build(AlertEntity alert, ResolvedUser recipient) {
        List<String> types = sortedTypes(alert.getTypes());
        boolean operational = types.contains(AlertTypeCatalog.OPERATIONAL);
        String link = link(alert);
        String instructions = alert.getInstructions();
        String title = alert.getTitle();

        String subject = operational
                ? "[Operativa] " + title
                : "Alerta CampusSeguro: " + labels(types);

        String html = operational
                ? operationalHtml(title, instructions, link)
                : emergencyHtml(instructions, link);

        String text = operational
                ? operationalText(title, instructions, link)
                : emergencyText(instructions, link);

        return new EmailMessage(
                recipient.email(),
                recipient.name(),
                subject,
                html,
                text);
    }

    private String emergencyHtml(String instructions, String link) {
        String escapedInstructions = escape(instructions);

        StringBuilder html = new StringBuilder();
        html.append("<p>")
                .append(escape(MESSAGE_BASE))
                .append(escapedInstructions)
                .append(".</p>");

        if (link != null) {
            html.append("<p>Ver y confirmar alerta: <a href=\"")
                    .append(escape(link))
                    .append("\">")
                    .append(escape(link))
                    .append("</a></p>");
        }

        return html.toString();
    }

    private String operationalHtml(
            String title,
            String instructions,
            String link) {

        StringBuilder html = new StringBuilder();
        html.append("<p><strong>")
                .append(escape(title))
                .append("</strong></p>")
                .append("<p>")
                .append(escape(instructions))
                .append("</p>");

        if (link != null) {
            html.append("<p><a href=\"")
                    .append(escape(link))
                    .append("\">Ver y confirmar alerta</a></p>");
        }

        return html.toString();
    }

    private String emergencyText(String instructions, String link) {
        StringBuilder text = new StringBuilder()
                .append(MESSAGE_BASE)
                .append(instructions)
                .append(".");

        if (link != null) {
            text.append(System.lineSeparator())
                    .append("Ver y confirmar alerta: ")
                    .append(link);
        }

        return text.toString();
    }

    private String operationalText(
            String title,
            String instructions,
            String link) {

        StringBuilder text = new StringBuilder()
                .append(title)
                .append(System.lineSeparator())
                .append(System.lineSeparator())
                .append(instructions);

        if (link != null) {
            text.append(System.lineSeparator())
                    .append(System.lineSeparator())
                    .append("Ver y confirmar alerta: ")
                    .append(link);
        }

        return text.toString();
    }

    private String link(AlertEntity alert) {
        if (appBaseUrl.isBlank()) {
            return null;
        }

        return appBaseUrl + "/bandeja?alerta=" + alert.getId();
    }

    private List<String> sortedTypes(
            java.util.Collection<String> types) {

        return alertTypeCatalog.sorted(types);
    }

    private String labels(List<String> types) {
        return types.stream()
                .map(alertTypeCatalog::labelOf)
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }

    private String escape(String value) {
        return HtmlUtils.htmlEscape(
                value == null ? "" : value,
                "UTF-8");
    }
}
