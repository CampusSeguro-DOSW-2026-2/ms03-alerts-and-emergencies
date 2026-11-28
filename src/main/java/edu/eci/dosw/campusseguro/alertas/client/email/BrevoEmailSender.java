package edu.eci.dosw.campusseguro.alertas.client.email;

import edu.eci.dosw.campusseguro.alertas.exception.EmailSendException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
@ConditionalOnProperty(name = "app.mail.provider", havingValue = "brevo", matchIfMissing = true)
public class BrevoEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(BrevoEmailSender.class);
    private static final int MAX_ERROR_BODY = 300;
    /** Máximo de versiones por llamada que admite la API de Brevo. */
    static final int MAX_BATCH_SIZE = 1000;
    static final int DEFAULT_BATCH_SIZE = 500;
    static final int DEFAULT_PARALLELISM = 8;

    private final JavaMailSender mailSender;
    private final RestClient restClient;
    private final String apiKey;
    private final boolean smtp;
    private final String fromEmail;
    private final String fromName;
    private final int maxAttempts;
    private final long retryDelayMs;
    private final int batchSize;
    private final int parallelism;

    public BrevoEmailSender(
            JavaMailSender mailSender,
            RestClient.Builder restClientBuilder,
            @Value("${app.mail.brevo.api-key:}") String apiKey,
            @Value("${app.mail.brevo.base-url:https://api.brevo.com}") String baseUrl,
            @Value("${app.mail.brevo.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${app.mail.brevo.read-timeout-ms:10000}") long readTimeoutMs,
            @Value("${app.mail.from:no-reply@campusseguro.local}") String fromEmail,
            @Value("${app.mail.from-name:CampusSeguro}") String fromName,
            @Value("${app.mail.brevo.max-attempts:3}") int maxAttempts,
            @Value("${app.mail.brevo.retry-delay-ms:200}") long retryDelayMs) {
        this(mailSender, restClientBuilder, apiKey, baseUrl, connectTimeoutMs, readTimeoutMs, fromEmail, fromName,
                maxAttempts, retryDelayMs, new MailTransport("api", apiKey));
    }

    public BrevoEmailSender(
            JavaMailSender mailSender,
            RestClient.Builder restClientBuilder,
            String apiKey,
            String baseUrl,
            long connectTimeoutMs,
            long readTimeoutMs,
            String fromEmail,
            String fromName,
            int maxAttempts,
            long retryDelayMs,
            MailTransport transport) {
        this(mailSender, restClientBuilder, apiKey, baseUrl, connectTimeoutMs, readTimeoutMs, fromEmail, fromName,
                maxAttempts, retryDelayMs, DEFAULT_BATCH_SIZE, DEFAULT_PARALLELISM, transport);
    }

    /**
     * @param batchSize   correos por llamada a la API de Brevo (hasta 1000): una alerta a cientos de personas sale
     *                    en una o dos llamadas
     * @param parallelism envíos simultáneos cuando el lote no se puede usar (SMTP o respaldo de un lote rechazado)
     */
    @Autowired
    public BrevoEmailSender(
            JavaMailSender mailSender,
            RestClient.Builder restClientBuilder,
            @Value("${app.mail.brevo.api-key:}") String apiKey,
            @Value("${app.mail.brevo.base-url:https://api.brevo.com}") String baseUrl,
            @Value("${app.mail.brevo.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${app.mail.brevo.read-timeout-ms:10000}") long readTimeoutMs,
            @Value("${app.mail.from:no-reply@campusseguro.local}") String fromEmail,
            @Value("${app.mail.from-name:CampusSeguro}") String fromName,
            @Value("${app.mail.brevo.max-attempts:3}") int maxAttempts,
            @Value("${app.mail.brevo.retry-delay-ms:200}") long retryDelayMs,
            @Value("${app.mail.batch-size:500}") int batchSize,
            @Value("${app.mail.parallelism:8}") int parallelism,
            MailTransport transport) {
        this.smtp = transport.smtp();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.restClient = restClientBuilder.clone()
                .requestFactory(requestFactory)
                .baseUrl(baseUrl)
                .build();
        this.mailSender = mailSender;
        this.apiKey = apiKey;
        this.fromEmail = fromEmail;
        this.fromName = fromName;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryDelayMs = Math.max(0, retryDelayMs);
        this.batchSize = Math.clamp(batchSize, 1, MAX_BATCH_SIZE);
        this.parallelism = Math.max(1, parallelism);
    }

    @Override
    public void send(EmailMessage message) {
        if (fromEmail == null || fromEmail.isBlank()) {
            throw new EmailSendException("El remitente de correo no está configurado");
        }
        if (!smtp) {
            sendHttp(message);
            return;
        }
        sendSmtp(message);
    }

    /**
     * Por la API, los correos con el mismo contenido salen en una sola llamada por cada {@code batchSize}
     * ({@code messageVersions}, una versión por destinatario), así Brevo los entrega a todos a la vez. Si Brevo
     * rechaza los datos del lote (4xx), se envía correo por correo en paralelo para que solo falle el inválido. Si
     * Brevo no responde (5xx, 429 o red, ya reintentado), el lote queda fallido de inmediato: enviar uno por uno
     * fallaría igual y tardaría minutos; el reintento programado vuelve a enviar el lote. Por SMTP se envían en
     * paralelo.
     */
    @Override
    public Map<Integer, String> sendAll(List<EmailMessage> messages) {
        if (messages.isEmpty()) {
            return Map.of();
        }
        if (fromEmail == null || fromEmail.isBlank()) {
            Map<Integer, String> failures = new TreeMap<>();
            for (int i = 0; i < messages.size(); i++) {
                failures.put(i, "El remitente de correo no está configurado");
            }
            return failures;
        }
        List<Integer> all = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            all.add(i);
        }
        if (smtp) {
            return sendInParallel(messages, all);
        }

        Map<Integer, String> failures = new TreeMap<>();
        for (List<Integer> group : groupBySameContent(messages, all)) {
            for (int from = 0; from < group.size(); from += batchSize) {
                List<Integer> chunk = group.subList(from, Math.min(from + batchSize, group.size()));
                if (chunk.size() == 1) {
                    failures.putAll(sendInParallel(messages, chunk));
                    continue;
                }
                try {
                    post(batchPayload(messages, chunk));
                } catch (EmailSendException ex) {
                    if (rejectedByBrevo(ex)) {
                        log.warn("Brevo rechazó un lote de {} correos ({}); se envían uno por uno",
                                chunk.size(), ex.getMessage());
                        failures.putAll(sendInParallel(messages, chunk));
                    } else {
                        log.warn("Brevo no recibió un lote de {} correos ({}); queda para el reintento",
                                chunk.size(), ex.getMessage());
                        chunk.forEach(index -> failures.put(index, ex.getMessage()));
                    }
                }
            }
        }
        return failures;
    }

    private void sendHttp(EmailMessage message) {
        post(payload(message));
    }

    private void post(Map<String, Object> body) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                restClient.post()
                        .uri("/v3/smtp/email")
                        .header("api-key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .toBodilessEntity();
                return;
            } catch (RestClientResponseException ex) {
                if (!retryable(ex.getStatusCode().value()) || attempt == maxAttempts) {
                    throw new EmailSendException(errorMessage(ex), ex);
                }
                log.warn("Brevo respondió {}; reintentando intento {}/{}",
                        ex.getStatusCode().value(), attempt, maxAttempts);
                waitBeforeRetry(attempt);
            } catch (RestClientException ex) {
                if (attempt == maxAttempts) {
                    throw new EmailSendException("No se pudo contactar el servicio de correo", ex);
                }
                log.warn("Fallo de red contactando Brevo; reintentando intento {}/{}", attempt, maxAttempts);
                waitBeforeRetry(attempt);
            }
        }
    }

    private void sendSmtp(EmailMessage message) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                mailSender.send(buildMessage(message));
                return;
            } catch (MailException | MessagingException | UnsupportedEncodingException ex) {
                if (attempt == maxAttempts) {
                    throw new EmailSendException("No se pudo enviar el correo: " + ex.getMessage(), ex);
                }
                log.warn("Fallo enviando correo; reintentando intento {}/{}", attempt, maxAttempts);
                waitBeforeRetry(attempt);
            }
        }
    }

    /** Envía cada correo por separado, con hasta {@code parallelism} envíos al mismo tiempo. */
    private Map<Integer, String> sendInParallel(List<EmailMessage> messages, List<Integer> indexes) {
        Map<Integer, String> failures = new ConcurrentHashMap<>();
        int threads = Math.min(parallelism, indexes.size());
        Map<String, String> context = MDC.getCopyOfContextMap();
        AtomicInteger counter = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threads, runnable -> {
            Thread thread = new Thread(runnable, "mail-send-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        try {
            List<Future<?>> tasks = new ArrayList<>();
            for (Integer index : indexes) {
                tasks.add(pool.submit(() -> sendOne(messages.get(index), index, failures, context)));
            }
            for (int i = 0; i < tasks.size(); i++) {
                try {
                    tasks.get(i).get();
                } catch (ExecutionException ex) {
                    failures.putIfAbsent(indexes.get(i), "No se pudo enviar el correo: " + ex.getCause());
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            for (Integer index : indexes) {
                failures.putIfAbsent(index, "Envío de correo interrumpido");
            }
        } finally {
            pool.shutdown();
        }
        return new TreeMap<>(failures);
    }

    private void sendOne(EmailMessage message, int index, Map<Integer, String> failures, Map<String, String> context) {
        if (context != null) {
            MDC.setContextMap(context);
        }
        try {
            send(message);
        } catch (EmailSendException ex) {
            failures.put(index, ex.getMessage());
        } catch (RuntimeException ex) {
            failures.put(index, "No se pudo enviar el correo: " + ex.getMessage());
        } finally {
            MDC.clear();
        }
    }

    /** Agrupa los correos que solo cambian de destinatario: son los que pueden ir en un mismo lote. */
    private static List<List<Integer>> groupBySameContent(List<EmailMessage> messages, List<Integer> indexes) {
        Map<List<String>, List<Integer>> groups = new LinkedHashMap<>();
        for (Integer index : indexes) {
            EmailMessage message = messages.get(index);
            List<String> key = Arrays.asList(message.subject(), message.html(), message.text());
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(index);
        }
        return List.copyOf(groups.values());
    }

    private MimeMessage buildMessage(EmailMessage message)
            throws MessagingException, UnsupportedEncodingException {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(
                mimeMessage, true, StandardCharsets.UTF_8.name());
        helper.setFrom(new InternetAddress(fromEmail, fromName, StandardCharsets.UTF_8.name()));
        if (message.toName() == null || message.toName().isBlank()) {
            helper.setTo(message.to());
        } else {
            helper.setTo(new InternetAddress(
                    message.to(), message.toName(), StandardCharsets.UTF_8.name()));
        }
        helper.setSubject(message.subject());
        helper.setText(message.text(), message.html());
        if (message.html() != null && message.html().contains("cid:" + MailTransport.LOGO_CID)) {
            helper.addInline(MailTransport.LOGO_CID, "campusseguro-logo.png",
                    new ClassPathResource(MailTransport.LOGO_PATH), "image/png");
        }
        return mimeMessage;
    }

    private Map<String, Object> batchPayload(List<EmailMessage> messages, List<Integer> indexes) {
        EmailMessage first = messages.get(indexes.get(0));
        List<Map<String, Object>> versions = new ArrayList<>();
        for (Integer index : indexes) {
            versions.add(Map.of("to", List.of(recipient(messages.get(index)))));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sender", sender());
        body.put("subject", first.subject());
        body.put("htmlContent", first.html());
        body.put("textContent", first.text());
        body.put("messageVersions", versions);
        return body;
    }

    private Map<String, Object> sender() {
        Map<String, Object> sender = new LinkedHashMap<>();
        sender.put("name", fromName);
        sender.put("email", fromEmail);
        return sender;
    }

    private static Map<String, Object> recipient(EmailMessage message) {
        Map<String, Object> recipient = new LinkedHashMap<>();
        recipient.put("email", message.to());
        if (message.toName() != null && !message.toName().isBlank()) {
            recipient.put("name", message.toName());
        }
        return recipient;
    }

    private Map<String, Object> payload(EmailMessage message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sender", sender());
        body.put("to", List.of(recipient(message)));
        body.put("subject", message.subject());
        body.put("htmlContent", message.html());
        body.put("textContent", message.text());
        return body;
    }

    /** true si Brevo respondió y rechazó los datos: repetir el mismo lote no serviría. */
    private boolean rejectedByBrevo(EmailSendException ex) {
        return ex.getCause() instanceof RestClientResponseException response
                && !retryable(response.getStatusCode().value());
    }

    private boolean retryable(int status) {
        return status == 429 || status >= 500;
    }

    private void waitBeforeRetry(int attempt) {
        long delay = retryDelayMs * (1L << Math.min(attempt - 1, 5));
        try {
            Thread.sleep(delay);
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
