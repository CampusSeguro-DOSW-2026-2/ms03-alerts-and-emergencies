package edu.eci.dosw.campusseguro.alertas.client.email;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.eci.dosw.campusseguro.alertas.client.catalog.ResolvedUser;
import edu.eci.dosw.campusseguro.alertas.entity.AlertEntity;
import edu.eci.dosw.campusseguro.alertas.entity.enums.AlertType;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class EmailTemplateBuilderTest {

    private static final UUID ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private AlertEntity alert(AlertType type, String instructions) {
        AlertEntity alert = new AlertEntity();
        ReflectionTestUtils.setField(alert, "id", ID);
        alert.setType(type);
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
        assertTrue(message.html().contains("https://campus.example/alertas/" + ID + "/ver"));
        assertTrue(message.text().contains("Salir por la escalera"));
        assertTrue(message.text().contains("https://campus.example/alertas/" + ID + "/ver"));
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
        assertTrue(message.html().contains("&lt;Ana &amp; &quot;Luis&quot;&gt;"));
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
    void build_shouldTranslateEveryAlertType() {
        EmailTemplateBuilder builder = new EmailTemplateBuilder("http://localhost:5173");
        ResolvedUser user = new ResolvedUser("usr-001", "ana@example.edu.co", "Ana");

        assertEquals("Terremoto", subjectType(builder.build(alert(AlertType.EARTHQUAKE, "x"), user)));
        assertEquals("Simulacro", subjectType(builder.build(alert(AlertType.SIMULATION, "x"), user)));
        assertEquals("Incendio", subjectType(builder.build(alert(AlertType.FIRE, "x"), user)));
        assertEquals("Otra emergencia", subjectType(builder.build(alert(AlertType.OTHER, "x"), user)));
    }

    private String subjectType(EmailMessage message) {
        return message.subject().substring("Alerta CampusSeguro: ".length());
    }
}
