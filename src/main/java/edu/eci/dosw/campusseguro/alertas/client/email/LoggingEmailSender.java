package edu.eci.dosw.campusseguro.alertas.client.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Enviador para desarrollo y demos: no contacta ningún proveedor, solo registra el envío en el log.
 * Se activa con app.mail.provider=log (valor por defecto del perfil local).
 */
@Component
@ConditionalOnProperty(name = "app.mail.provider", havingValue = "log")
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(EmailMessage message) {
        log.info("Correo simulado a {}: {}", maskEmail(message.to()), message.subject());
    }

    static String maskEmail(String email) {
        if (email == null) {
            return "";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
