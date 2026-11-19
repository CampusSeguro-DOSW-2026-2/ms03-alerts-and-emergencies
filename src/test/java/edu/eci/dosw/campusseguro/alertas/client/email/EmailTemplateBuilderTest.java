package edu.eci.dosw.campusseguro.alertas.client.email;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.eci.dosw.campusseguro.alertas.client.catalog.ResolvedUser;
import edu.eci.dosw.campusseguro.alertas.entity.AlertEntity;
import edu.eci.dosw.campusseguro.alertas.service.AlertTypeCatalogStub;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class EmailTemplateBuilderTest {

    private static final UUID ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static final String LOGO = "https://cdn.example/branding/campusseguro-logo.png";

    private AlertEntity alert(String type, String instructions) {
        AlertEntity alert = new AlertEntity();
        ReflectionTestUtils.setField(alert, "id", ID);
        alert.setTypes(Set.of(type));
        alert.setTitle("Titulo de prueba");
        alert.setInstructions(instructions);
        return alert;
    }

    private EmailTemplateBuilder builder(String appBaseUrl) {
        return new EmailTemplateBuilder(appBaseUrl, LOGO, AlertTypeCatalogStub.create());
    }

    @Test
    void build_shouldContainTypeInstructionsAndLink() {
        EmailTemplateBuilder builder = builder("https://campus.example/");
        ResolvedUser user = new ResolvedUser("usr-001", "ana@example.edu.co", "Ana");

        EmailMessage message = builder.build(alert("FIRE", "Salir por la escalera"), user);

        assertEquals("ana@example.edu.co", message.to());
        assertEquals("Ana", message.toName());
        assertTrue(message.subject().contains("Incendio"));
        assertTrue(message.html().contains("Titulo de prueba"));
        assertTrue(message.html().contains("Salir por la escalera"));
        assertTrue(message.html().contains(
                "https://campus.example/bandeja?alerta=" + ID));
        assertTrue(message.text().contains("Salir por la escalera"));
        assertTrue(message.text().contains(
                "https://campus.example/bandeja?alerta=" + ID));
    }

    @Test
    void build_shouldShowTheLinkAsAButtonInsteadOfTheRawAddress() {
        EmailMessage message = builder("https://campus.example").build(
                alert("EARTHQUAKE", "Evacuar"),
                new ResolvedUser("usr-001", "ana@example.edu.co", "Ana"));

        String url = "https://campus.example/bandeja?alerta=" + ID;
        assertTrue(message.html().contains("href=\"" + url + "\""));
        assertTrue(message.html().contains(">Ver y confirmar alerta</a>"));
        assertFalse(message.html().contains(">" + url + "<"));
    }

    @Test
    void build_shouldUseTheLogoAndTheBrandColors() {
        String html = builder("https://campus.example").build(
                alert("FIRE", "Salir"),
                new ResolvedUser("usr-001", "ana@example.edu.co", "Ana")).html();

        assertTrue(html.contains("<img src=\"" + LOGO + "\""));
        assertTrue(html.contains("CampusSeguro"));
        assertTrue(html.contains("#990000"));
        assertTrue(html.contains("#FFF7ED"));
    }

    @Test
    void build_shouldKeepTheWordmark_whenThereIsNoLogoUrl() {
        EmailTemplateBuilder withoutLogo = new EmailTemplateBuilder(
                "https://campus.example", " ", AlertTypeCatalogStub.create());

        String html = withoutLogo.build(
                alert("FIRE", "Salir"),
                new ResolvedUser("usr-001", "ana@example.edu.co", "Ana")).html();

        assertFalse(html.contains("<img"));
        assertTrue(html.contains("CampusSeguro"));
    }

    @Test
    void build_shouldUseTheBlueAccentForOperationalAlerts() {
        String html = builder("https://campus.example").build(
                alert("OPERATIVA", "Cierre de cafetería"),
                new ResolvedUser("usr-001", "ana@example.edu.co", "Ana")).html();

        assertTrue(html.contains("#2563EB"));
        assertTrue(html.contains("bgcolor=\"#2563EB\""));
        assertTrue(html.contains(">Ver el aviso</a>"));
    }

    @Test
    void build_shouldEscapeUserControlledHtmlAndQuotes() {
        EmailTemplateBuilder builder = builder("http://localhost:5173");
        ResolvedUser user = new ResolvedUser("usr-001", "ana@example.edu.co", "<Ana & \"Luis\">");

        EmailMessage message = builder.build(
                alert("OTHER", "<script>alert('x')</script> \"instrucción\""),
                user);

        assertFalse(message.html().contains("<script>"));
        assertTrue(message.html().contains("&lt;script&gt;"));
        assertTrue(message.html().contains("&quot;instrucción&quot;"));
        assertEquals("<Ana & \"Luis\">", message.toName());
        assertFalse(message.html().contains("<Ana"));
    }

    @Test
    void build_shouldUsePlainTextInstructions_butEscapeHtmlVersion() {
        EmailTemplateBuilder builder = builder("http://localhost:5173");
        ResolvedUser user = new ResolvedUser("usr-001", "ana@example.edu.co", null);

        EmailMessage message = builder.build(alert("EARTHQUAKE", "<b>Evacuar</b>"), user);

        assertTrue(message.text().contains("<b>Evacuar</b>"));
        assertTrue(message.html().contains("&lt;b&gt;Evacuar&lt;/b&gt;"));
        assertNull(message.toName());
    }

    @Test
    void build_shouldTranslateEveryEmergencyTypeInCanonicalOrder() {
        EmailTemplateBuilder builder = builder("http://localhost:5173");
        ResolvedUser user = new ResolvedUser("usr-001", "ana@example.edu.co", "Ana");
        AlertEntity alert = alert("OTHER", "x");
        alert.setTypes(Set.of("OTHER", "FIRE", "EARTHQUAKE"));

        assertEquals("Terremoto", subjectType(builder.build(alert("EARTHQUAKE", "x"), user)));
        assertEquals("Incendio", subjectType(builder.build(alert("FIRE", "x"), user)));
        assertEquals("Otra emergencia", subjectType(builder.build(alert("OTHER", "x"), user)));
        assertEquals(
                "Terremoto, Incendio, Otra emergencia",
                subjectType(builder.build(alert, user)));
    }

    @Test
    void build_shouldUseOperationalSubjectAndOmitBaseMessage() {
        EmailTemplateBuilder builder = builder("http://localhost:5173");
        ResolvedUser user = new ResolvedUser("usr-001", "ana@example.edu.co", "Ana");

        EmailMessage message = builder.build(alert("OPERATIVA", "Cierre de cafetería"), user);

        assertEquals("[Operativa] Titulo de prueba", message.subject());
        assertFalse(message.text().contains("Has recibido una alerta oficial"));
        assertFalse(message.html().contains("Has recibido una alerta oficial"));
        assertTrue(message.text().contains("Cierre de cafetería"));
        assertTrue(message.html().contains("Cierre de cafetería"));
    }

    @Test
    void build_shouldOmitLink_whenBaseUrlIsBlank() {
        EmailTemplateBuilder builder = builder("  ");
        ResolvedUser user = new ResolvedUser("usr-001", "ana@example.edu.co", "Ana");

        EmailMessage message = builder.build(alert("EARTHQUAKE", "Evacuar"), user);

        assertFalse(message.html().contains("href="));
        assertFalse(message.text().contains("/bandeja?alerta="));
    }

    private String subjectType(EmailMessage message) {
        return message.subject().substring("Alerta CampusSeguro: ".length());
    }
}
