package edu.eci.dosw.campusseguro.alertas.client.email;

import edu.eci.dosw.campusseguro.alertas.exception.EmailSendException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class BrevoEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(BrevoEmailSender.class);
    private static final int MAX_ERROR_BODY = 300;

    private final RestClient restClient;
    private final String apiKey;
    private final String fromEmail;
    private final String fromName;
    private final int maxAttempts;
    private final long retryDelayMs;

    public BrevoEmailSender(
            RestClient.Builder restClientBuilder,
            @Value("${app.mail.brevo.api-key:}") String apiKey,
            @Value("${app.mail.brevo.base-url:https://api.brevo.com}") String baseUrl,
            @Value("${app.mail.from:no-reply@campusseguro.local}") String fromEmail,
            @Value("${app.mail.from-name:CampusSeguro}") String fromName,
            @Value("${app.mail.brevo.max-attempts:3}") int maxAttempts,
            @Value("${app.mail.brevo.retry-delay-ms:200}") long retryDelayMs) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.fromEmail = fromEmail;
        this.fromName = fromName;
        this.maxAttempts = maxAttempts;
        this.retryDelayMs = retryDelayMs;
    }

    @Override
    public void send(EmailMessage message) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new EmailSendException("La API key de Brevo no está configurada");
        }

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                restClient.post()
                        .uri("/v3/smtp/email")
                        .header("api-key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(payload(message))
                        .retrieve()
                        .toBodilessEntity();
                return;
            } catch (RestClientResponseException ex) {
                if (!retryable(ex.getStatusCode().value()) || attempt == maxAttempts) {
                    throw new EmailSendException(errorMessage(ex), ex);
                }
                log.warn("Brevo respondió {}; reintentando intento {}/{}",
                        ex.getStatusCode().value(), attempt, maxAttempts);
                waitBeforeRetry();
            } catch (RestClientException ex) {
                if (attempt == maxAttempts) {
                    throw new EmailSendException("No se pudo contactar el servicio de correo", ex);
                }
                log.warn("Fallo de red contactando Brevo; reintentando intento {}/{}", attempt, maxAttempts);
                waitBeforeRetry();
            }
        }
    }

    private Map<String, Object> payload(EmailMessage message) {
        Map<String, Object> sender = new LinkedHashMap<>();
        sender.put("name", fromName);
        sender.put("email", fromEmail);

        Map<String, Object> recipient = new LinkedHashMap<>();
        recipient.put("email", message.to());
        if (message.toName() != null && !message.toName().isBlank()) {
            recipient.put("name", message.toName());
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sender", sender);
        body.put("to", List.of(recipient));
        body.put("subject", message.subject());
        body.put("htmlContent", message.html());
        body.put("textContent", message.text());
        return body;
    }

    private boolean retryable(int status) {
        return status == 429 || status >= 500;
    }

    private void waitBeforeRetry() {
        try {
            Thread.sleep(retryDelayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new EmailSendException("Envío de correo interrumpido", ex);
        }
    }

    private String errorMessage(RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        if (body.length() > MAX_ERROR_BODY) {
            body = body.substring(0, MAX_ERROR_BODY);
        }
        return "Brevo respondió " + ex.getStatusCode().value() + ": " + body;
    }
}
