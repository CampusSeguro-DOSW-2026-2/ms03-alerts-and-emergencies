package edu.eci.dosw.campusseguro.alertas.client.email;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.eci.dosw.campusseguro.alertas.client.catalog.ResolvedUser;
import edu.eci.dosw.campusseguro.alertas.entity.AlertEntity;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertType;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class EmailTemplateBuilderTest {

    private static final UUID ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private AlertEntity alert(AlertType type, String instructions) {
        AlertEntity alert = new AlertEntity();
        ReflectionTestUtils.setField(alert, "id", ID);
        alert.setTypes(Set.of(type));
        alert.setTitle("Titulo de prueba");
        alert.setInstructions(instructions);
        return alert;
    }

    @Test
    void build_shouldContainTypeInstructionsAndLink() {
        EmailTemplateBuilder builder = new EmailTemplateBuilder("https://campus.example/");
        ResolvedUser user = new ResolvedUser("usr-001", "ana@example.edu.co", "Ana");

        EmailMessage message = builder.build(alert(AlertType.FIRE, "Salir por la escalera"), user);

        assertEquals("ana@example.edu.co", message.to());
        assertEquals("Ana", message.toName());
        assertTrue(message.subject().contains("Incendio"));
        assertTrue(message.html().contains("Salir por la escalera"));
        assertTrue(message.html().contains(
                "https://campus.example/bandeja?alerta=" + ID));
        assertTrue(message.text().contains("Salir por la escalera"));
        assertTrue(message.text().contains(
                "https://campus.example/bandeja?alerta=" + ID));
    }

    @Test
    void build_shouldEscapeUserControlledHtmlAndQuotes() {
        EmailTemplateBuilder builder = new EmailTemplateBuilder("http://localhost:5173");
        ResolvedUser user = new ResolvedUser("usr-001", "ana@example.edu.co", "<Ana & \"Luis\">");

        EmailMessage message = builder.build(
                alert(AlertType.OTHER, "<script>alert('x')</script> \"instrucción\""),
                user);

        assertFalse(message.html().contains("<script>"));
        assertTrue(message.html().contains("&lt;script&gt;"));
        assertTrue(message.html().contains("&quot;instrucción&quot;"));
        assertEquals("<Ana & \"Luis\">", message.toName());
        assertFalse(message.html().contains("<Ana"));
    }

    @Test
    void build_shouldUsePlainTextInstructions_butEscapeHtmlVersion() {
        EmailTemplateBuilder builder = new EmailTemplateBuilder("http://localhost:5173");
        ResolvedUser user = new ResolvedUser("usr-001", "ana@example.edu.co", null);

        EmailMessage message = builder.build(alert(AlertType.EARTHQUAKE, "<b>Evacuar</b>"), user);

        assertTrue(message.text().contains("<b>Evacuar</b>"));
        assertTrue(message.html().contains("&lt;b&gt;Evacuar&lt;/b&gt;"));
        assertNull(message.toName());
    }

    @Test
    void build_shouldTranslateEveryEmergencyTypeInCanonicalOrder() {
        EmailTemplateBuilder builder = new EmailTemplateBuilder("http://localhost:5173");
        ResolvedUser user = new ResolvedUser("usr-001", "ana@example.edu.co", "Ana");
        AlertEntity alert = alert(AlertType.OTHER, "x");
        alert.setTypes(Set.of(AlertType.OTHER, AlertType.FIRE, AlertType.EARTHQUAKE));

        assertEquals("Terremoto", subjectType(builder.build(alert(AlertType.EARTHQUAKE, "x"), user)));
        assertEquals("Incendio", subjectType(builder.build(alert(AlertType.FIRE, "x"), user)));
        assertEquals("Otra emergencia", subjectType(builder.build(alert(AlertType.OTHER, "x"), user)));
        assertEquals(
                "Terremoto, Incendio, Otra emergencia",
                subjectType(builder.build(alert, user)));
    }

    @Test
    void build_shouldUseOperationalSubjectAndOmitBaseMessage() {
        EmailTemplateBuilder builder = new EmailTemplateBuilder("http://localhost:5173");
        ResolvedUser user = new ResolvedUser("usr-001", "ana@example.edu.co", "Ana");

        EmailMessage message = builder.build(alert(AlertType.OPERATIVA, "Cierre de cafetería"), user);

        assertEquals("[Operativa] Titulo de prueba", message.subject());
        assertFalse(message.text().contains("Has recibido una alerta oficial"));
        assertFalse(message.html().contains("Has recibido una alerta oficial"));
        assertTrue(message.text().contains("Cierre de cafetería"));
        assertTrue(message.html().contains("Cierre de cafetería"));
    }

    @Test
    void build_shouldOmitLink_whenBaseUrlIsBlank() {
        EmailTemplateBuilder builder = new EmailTemplateBuilder("  ");
        ResolvedUser user = new ResolvedUser("usr-001", "ana@example.edu.co", "Ana");

        EmailMessage message = builder.build(alert(AlertType.EARTHQUAKE, "Evacuar"), user);

        assertFalse(message.html().contains("href="));
        assertFalse(message.text().contains("/bandeja?alerta="));
    }

    private String subjectType(EmailMessage message) {
        return message.subject().substring("Alerta CampusSeguro: ".length());
    }
}
