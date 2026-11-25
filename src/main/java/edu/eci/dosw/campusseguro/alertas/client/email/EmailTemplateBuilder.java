package edu.eci.dosw.campusseguro.alertas.client.email;

import edu.eci.dosw.campusseguro.alertas.client.catalog.ResolvedUser;
import edu.eci.dosw.campusseguro.alertas.entity.AlertEntity;
import edu.eci.dosw.campusseguro.alertas.service.AlertTypeCatalog;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Builds the alert email with the branding of the application: the logo, its red and blue accents and a highlighted
 * message. The HTML uses tables and inline styles because email clients ignore style sheets, and the link is shown as a
 * button so the long address is never part of the visible text (it stays in the plain-text alternative).
 */
@Component
public class EmailTemplateBuilder {

    private static final String BRAND = "CampusSeguro";

    private static final String MESSAGE_BASE = "Ha recibido una alerta oficial de CampusSeguro.";

    private static final String MESSAGE_HINT = "Confirme la recepción y siga las instrucciones.";

    /** Colors of the application palette (frontend styles.css). */
    private static final String RED = "#990000";
    private static final String INK = "#0F172A";
    private static final String MUTED = "#64748B";
    private static final String LINE = "#E2E8F0";
    private static final String PAGE_BG = "#F8FAFC";
    private static final String CREAM = "#FFF7ED";
    private static final String AMBER = "#D97706";
    private static final String BLUE = "#2563EB";
    private static final String BLUE_PALE = "#EFF6FF";

    private static final String FONT =
            "-apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif";

    /** Logo published with the frontend, next to the application the email links to. */
    static final String SITE_LOGO_PATH = "/correo/campusseguro-logo.png";

    private final String appBaseUrl;

    private final String logoUrl;

    private final AlertTypeCatalog alertTypeCatalog;

    public EmailTemplateBuilder(
            @Value("${app.mail.app-base-url:http://localhost:5173}")
            String appBaseUrl,
            @Value("${app.mail.logo-url:}")
            String logoUrl,
            AlertTypeCatalog alertTypeCatalog) {

        this.alertTypeCatalog = alertTypeCatalog;
        this.appBaseUrl = normalize(appBaseUrl);
        this.logoUrl = logoUrl(normalize(logoUrl), this.appBaseUrl);
    }

    /**
     * The configured logo or, if there is none, the one published by the application site: Brevo does not support
     * images embedded in the message, so the logo must be a public address that the email clients can download.
     */
    private static String logoUrl(String configured, String appBaseUrl) {
        if (!configured.isBlank()) {
            return configured;
        }
        return appBaseUrl.isBlank() ? "" : appBaseUrl + SITE_LOGO_PATH;
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
                : emergencyHtml(title, types, instructions, link);

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

    private String emergencyHtml(
            String title,
            List<String> types,
            String instructions,
            String link) {

        String typeLabels = labels(types);
        String body = callout(CREAM, AMBER, "Mensaje oficial", MESSAGE_BASE, instructions)
                + "<p style=\"margin:14px 0 0;font-family:" + FONT
                + ";font-size:13px;line-height:1.6;color:" + MUTED + ";\">" + escape(MESSAGE_HINT) + "</p>";

        return email(
                title + " · " + typeLabels,
                "Alerta oficial · " + typeLabels,
                RED,
                title,
                body,
                link,
                "Ver y confirmar alerta",
                RED);
    }

    private String operationalHtml(
            String title,
            String instructions,
            String link) {

        String body = callout(BLUE_PALE, BLUE, "Información operativa", null, instructions);

        return email(
                title,
                "Aviso operativo",
                BLUE,
                title,
                body,
                link,
                "Ver el aviso",
                BLUE);
    }

    private String email(
            String preheader,
            String eyebrow,
            String accent,
            String heading,
            String bodyHtml,
            String link,
            String buttonLabel,
            String buttonColor) {

        StringBuilder html = new StringBuilder(4096);
        html.append("<!doctype html><html lang=\"es\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
                .append("<title>").append(escape(BRAND)).append("</title></head>")
                .append("<body style=\"margin:0;padding:0;background:").append(PAGE_BG).append(";\">")
                .append("<div style=\"display:none;max-height:0;overflow:hidden;opacity:0;color:transparent;\">")
                .append(escape(preheader)).append("</div>")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"")
                .append(" style=\"background:").append(PAGE_BG).append(";padding:24px 12px;\">")
                .append("<tr><td align=\"center\">")
                .append("<table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"")
                .append(" style=\"width:100%;max-width:600px;background:#ffffff;border:1px solid ")
                .append(LINE).append(";border-radius:14px;\">")
                .append("<tr><td style=\"padding:22px 28px;border-bottom:3px solid ").append(RED).append(";\">")
                .append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr>");

        if (!logoUrl.isBlank()) {
            html.append("<td style=\"vertical-align:middle;\"><img src=\"").append(escape(logoUrl))
                    .append("\" width=\"44\" height=\"44\" alt=\"\" style=\"display:block;border:0;width:44px;height:44px;\"></td>");
        }

        html.append("<td style=\"vertical-align:middle;padding-left:12px;font-family:").append(FONT)
                .append(";font-size:18px;font-weight:700;color:").append(RED).append(";letter-spacing:.2px;\">")
                .append(escape(BRAND)).append("</td></tr></table></td></tr>")
                .append("<tr><td style=\"padding:28px;\">")
                .append("<p style=\"margin:0 0 10px;font-family:").append(FONT)
                .append(";font-size:12px;letter-spacing:1.2px;text-transform:uppercase;color:").append(accent)
                .append(";font-weight:700;\">").append(escape(eyebrow)).append("</p>")
                .append("<h1 style=\"margin:0 0 18px;font-family:").append(FONT)
                .append(";font-size:24px;line-height:1.3;color:").append(INK).append(";font-weight:700;\">")
                .append(escape(heading)).append("</h1>")
                .append(bodyHtml);

        if (link != null) {
            html.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"")
                    .append(" style=\"margin:24px 0 4px;\"><tr><td bgcolor=\"").append(buttonColor)
                    .append("\" style=\"border-radius:8px;background:").append(buttonColor).append(";\">")
                    .append("<a href=\"").append(escape(link))
                    .append("\" style=\"display:inline-block;padding:14px 28px;font-family:").append(FONT)
                    .append(";font-size:15px;font-weight:700;color:#ffffff;text-decoration:none;border-radius:8px;\">")
                    .append(escape(buttonLabel)).append("</a></td></tr></table>");
        }

        html.append("</td></tr>")
                .append("<tr><td style=\"padding:18px 28px;background:").append(PAGE_BG)
                .append(";border-top:1px solid ").append(LINE).append(";\">")
                .append("<p style=\"margin:0;font-family:").append(FONT)
                .append(";font-size:12px;line-height:1.6;color:").append(MUTED).append(";\">")
                .append("Mensaje automático de ").append(escape(BRAND))
                .append(". No responda a este mensaje.</p></td></tr>")
                .append("</table></td></tr></table></body></html>");

        return html.toString();
    }

    private String callout(
            String background,
            String borderColor,
            String label,
            String headline,
            String text) {

        StringBuilder html = new StringBuilder();
        html.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"")
                .append(" style=\"background:").append(background)
                .append(";border-left:4px solid ").append(borderColor).append(";border-radius:10px;\">")
                .append("<tr><td style=\"padding:16px 18px;\">")
                .append("<p style=\"margin:0 0 8px;font-family:").append(FONT)
                .append(";font-size:11px;letter-spacing:1px;text-transform:uppercase;font-weight:700;color:")
                .append(borderColor).append(";\">").append(escape(label)).append("</p>");

        if (headline != null) {
            html.append("<p style=\"margin:0 0 8px;font-family:").append(FONT)
                    .append(";font-size:15px;font-weight:600;color:").append(INK).append(";\">")
                    .append(escape(headline)).append("</p>");
        }

        html.append("<p style=\"margin:0;font-family:").append(FONT)
                .append(";font-size:17px;line-height:1.55;color:").append(INK).append(";\">")
                .append(escape(text)).append("</p></td></tr></table>");

        return html.toString();
    }

    private String emergencyText(String instructions, String link) {
        StringBuilder text = new StringBuilder()
                .append(MESSAGE_BASE)
                .append(System.lineSeparator())
                .append(MESSAGE_HINT)
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
                    .append("Ver el aviso: ")
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

    private String normalize(String value) {
        return value == null
                ? ""
                : value.strip().replaceAll("/+$", "");
    }

    private String escape(String value) {
        return HtmlUtils.htmlEscape(
                value == null ? "" : value,
                "UTF-8");
    }
}
