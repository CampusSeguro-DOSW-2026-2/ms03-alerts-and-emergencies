package edu.eci.dosw.campusseguro.alertas.client.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * How the emails leave MS03: by the Brevo API ({@code api}) or by SMTP ({@code smtp}). Without an API key the SMTP is
 * used, as before. Only by SMTP the logo travels inside the message (Content-ID): the Brevo API does not support
 * embedded images, so there the logo is a public URL that some email clients block.
 */
@Component
public class MailTransport {

    /** Content-ID of the logo attached inline to the message. */
    public static final String LOGO_CID = "campusseguro-logo";

    /** Logo in the classpath, the same image of the application. */
    public static final String LOGO_PATH = "mail/campusseguro-logo.png";

    private final boolean smtp;

    public MailTransport(
            @Value("${app.mail.transport:api}") String transport,
            @Value("${app.mail.brevo.api-key:}") String apiKey) {
        this.smtp = "smtp".equalsIgnoreCase(transport == null ? "" : transport.strip())
                || apiKey == null || apiKey.isBlank();
    }

    /** true if the emails are sent by SMTP, with the logo inside the message. */
    public boolean smtp() {
        return smtp;
    }
}
