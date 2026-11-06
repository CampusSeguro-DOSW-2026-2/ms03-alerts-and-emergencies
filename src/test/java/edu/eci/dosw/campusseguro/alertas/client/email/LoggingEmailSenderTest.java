package edu.eci.dosw.campusseguro.alertas.client.email;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class LoggingEmailSenderTest {

    private static final EmailMessage MESSAGE = new EmailMessage(
            "andres.rojas@example.edu.co", "Andrés Rojas", "Alerta activa", "<p>html</p>", "texto");

    @Test
    void send_shouldLogSubjectWithoutExposingTheFullAddress(CapturedOutput output) {
        assertDoesNotThrow(() -> new LoggingEmailSender().send(MESSAGE));

        assertTrue(output.getOut().contains("a***@example.edu.co"));
        assertTrue(output.getOut().contains("Alerta activa"));
        assertFalse(output.getOut().contains("andres.rojas@"));
    }

    @Test
    void maskEmail_shouldHandleMissingOrMalformedAddresses() {
        assertEquals("", LoggingEmailSender.maskEmail(null));
        assertEquals("***", LoggingEmailSender.maskEmail("sin-arroba"));
        assertEquals("***", LoggingEmailSender.maskEmail("@dominio.com"));
    }
}
