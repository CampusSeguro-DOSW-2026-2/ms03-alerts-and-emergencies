package edu.eci.dosw.campusseguro.alertas.client.email;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import edu.eci.dosw.campusseguro.alertas.exception.EmailSendException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.client.RestClient;

class BrevoEmailSenderTest {

    private static final EmailMessage MESSAGE = new EmailMessage(
            "ana@example.edu.co",
            "Ana",
            "Alerta CampusSeguro: Sismo",
            "<p>Evacuar</p>",
            "Evacuar");

    private WireMockServer server;
    private JavaMailSender mailSender;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        mailSender = mock(JavaMailSender.class);
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    private BrevoEmailSender sender(String apiKey, int maxAttempts) {
        return new BrevoEmailSender(
                mailSender,
                RestClient.builder(),
                apiKey,
                server.baseUrl(),
                500,
                1000,
                "no-reply@campusseguro.local",
                "CampusSeguro",
                maxAttempts,
                10);
    }

    @Test
    void send_shouldPostPayloadAndReturn_whenBrevoAcceptsMessage() {
        server.stubFor(post("/v3/smtp/email").willReturn(aResponse().withStatus(201)));

        sender("api-key", 3).send(MESSAGE);

        server.verify(postRequestedFor(urlEqualTo("/v3/smtp/email"))
                .withHeader("api-key", equalTo("api-key"))
                .withRequestBody(equalToJson("""
                        {
                          "sender": {"name": "CampusSeguro", "email": "no-reply@campusseguro.local"},
                          "to": [{"email": "ana@example.edu.co", "name": "Ana"}],
                          "subject": "Alerta CampusSeguro: Sismo",
                          "htmlContent": "<p>Evacuar</p>",
                          "textContent": "Evacuar"
                        }
                        """)));
    }

    @Test
    void send_shouldFallbackToSmtp_whenApiKeyIsMissing() {
        when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getDefaultInstance(new Properties())));

        sender("", 3).send(MESSAGE);

        verify(mailSender).send(any(MimeMessage.class));
        server.verify(0, postRequestedFor(urlEqualTo("/v3/smtp/email")));
    }

    @Test
    void send_shouldUseSmtpWithTheInlineLogo_whenTransportIsSmtp() throws Exception {
        when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getDefaultInstance(new Properties())));
        BrevoEmailSender smtp = new BrevoEmailSender(
                mailSender, RestClient.builder(), "api-key", server.baseUrl(), 500, 1000,
                "no-reply@campusseguro.local", "CampusSeguro", 3, 10, new MailTransport("smtp", "api-key"));

        smtp.send(new EmailMessage("ana@example.edu.co", "Ana", "Alerta CampusSeguro: Sismo",
                "<img src=\"cid:campusseguro-logo\"><p>Evacuar</p>", "Evacuar"));

        org.mockito.ArgumentCaptor<MimeMessage> sent = org.mockito.ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        server.verify(0, postRequestedFor(urlEqualTo("/v3/smtp/email")));
        MimeMessage message = sent.getValue();
        message.saveChanges();
        java.io.ByteArrayOutputStream raw = new java.io.ByteArrayOutputStream();
        message.writeTo(raw);
        String mime = raw.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(mime.contains("Content-ID: <campusseguro-logo>"));
        assertTrue(mime.contains("image/png"));
        assertTrue(mime.contains("multipart/related"));
    }

    @Test
    void transport_shouldBeApiOnlyWithApiKeyAndTransportApi() {
        assertEquals(false, new MailTransport("api", "key").smtp());
        assertEquals(true, new MailTransport("smtp", "key").smtp());
        assertEquals(true, new MailTransport("api", " ").smtp());
    }

    @Test
    void send_shouldThrow_whenSenderIsBlank() {
        BrevoEmailSender sender = new BrevoEmailSender(
                mailSender,
                RestClient.builder(),
                "api-key",
                server.baseUrl(),
                500,
                1000,
                "",
                "CampusSeguro",
                3,
                10);

        EmailSendException ex = assertThrows(EmailSendException.class, () -> sender.send(MESSAGE));

        assertTrue(ex.getMessage().contains("remitente"));
        server.verify(0, postRequestedFor(urlEqualTo("/v3/smtp/email")));
    }

    @Test
    void send_shouldNotRetry_whenBrevoResponds4xx() {
        server.stubFor(post("/v3/smtp/email")
                .willReturn(aResponse().withStatus(400).withBody("destinatario inválido")));

        EmailSendException ex = assertThrows(
                EmailSendException.class,
                () -> sender("api-key", 3).send(MESSAGE));

        assertTrue(ex.getMessage().contains("400"));
        assertTrue(ex.getMessage().contains("destinatario inválido"));
        server.verify(1, postRequestedFor(urlEqualTo("/v3/smtp/email")));
    }

    @Test
    void send_shouldRetryAndThrow_whenBrevoKeepsFailingWith5xx() {
        server.stubFor(post("/v3/smtp/email").willReturn(aResponse().withStatus(500)));

        EmailSendException ex = assertThrows(
                EmailSendException.class,
                () -> sender("api-key", 3).send(MESSAGE));

        assertTrue(ex.getMessage().contains("500"));
        server.verify(3, postRequestedFor(urlEqualTo("/v3/smtp/email")));
    }

    @Test
    void send_shouldRetryAndSucceed_whenBrevoReturns429ThenAccepts() {
        server.stubFor(post("/v3/smtp/email")
                .inScenario("rate-limit")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(429))
                .willSetStateTo("second"));
        server.stubFor(post("/v3/smtp/email")
                .inScenario("rate-limit")
                .whenScenarioStateIs("second")
                .willReturn(aResponse().withStatus(201)));

        sender("api-key", 3).send(MESSAGE);

        server.verify(2, postRequestedFor(urlEqualTo("/v3/smtp/email")));
    }

    @Test
    void send_shouldThrow_when429RetriesAreExhausted() {
        server.stubFor(post("/v3/smtp/email").willReturn(aResponse().withStatus(429)));

        assertThrows(EmailSendException.class, () -> sender("api-key", 2).send(MESSAGE));

        server.verify(2, postRequestedFor(urlEqualTo("/v3/smtp/email")));
    }

    @Test
    void send_shouldRetryAndThrow_whenConnectionTimesOut() {
        server.stubFor(post("/v3/smtp/email")
                .willReturn(aResponse().withStatus(201).withFixedDelay(500)));

        BrevoEmailSender sender = new BrevoEmailSender(
                mailSender,
                RestClient.builder(),
                "api-key",
                server.baseUrl(),
                200,
                100,
                "no-reply@campusseguro.local",
                "CampusSeguro",
                2,
                10);

        EmailSendException ex = assertThrows(EmailSendException.class, () -> sender.send(MESSAGE));

        assertTrue(ex.getMessage().contains("No se pudo contactar"));
    }

    @Test
    void send_shouldTruncateLongErrorBody() {
        server.stubFor(post("/v3/smtp/email")
                .willReturn(aResponse().withStatus(400).withBody("x".repeat(400))));

        EmailSendException ex = assertThrows(
                EmailSendException.class,
                () -> sender("api-key", 1).send(MESSAGE));

        assertEquals(300, ex.getMessage().substring(ex.getMessage().indexOf(": ") + 2).length());
    }

    @Test
    void send_shouldReportStatus_whenErrorResponseHasNoBody() {
        server.stubFor(post("/v3/smtp/email").willReturn(aResponse().withStatus(400)));

        EmailSendException ex = assertThrows(
                EmailSendException.class,
                () -> sender("api-key", 1).send(MESSAGE));

        assertEquals("Brevo respondió 400: ", ex.getMessage());
    }

    private static EmailMessage to(String email) {
        return new EmailMessage(email, null, "Alerta CampusSeguro: Sismo", "<p>Evacuar</p>", "Evacuar");
    }

    private BrevoEmailSender batchSender(int batchSize, MailTransport transport, int maxAttempts) {
        return new BrevoEmailSender(
                mailSender, RestClient.builder(), "api-key", server.baseUrl(), 500, 1000,
                "no-reply@campusseguro.local", "CampusSeguro", maxAttempts, 10, batchSize, 4, transport);
    }

    @Test
    void sendAll_shouldSendOneRequestWithAVersionPerRecipient() {
        server.stubFor(post("/v3/smtp/email").willReturn(aResponse().withStatus(201)));

        Map<Integer, String> failures = sender("api-key", 3).sendAll(List.of(MESSAGE, to("luis@example.edu.co")));

        assertTrue(failures.isEmpty());
        server.verify(1, postRequestedFor(urlEqualTo("/v3/smtp/email")));
        server.verify(postRequestedFor(urlEqualTo("/v3/smtp/email"))
                .withRequestBody(equalToJson("""
                        {
                          "sender": {"name": "CampusSeguro", "email": "no-reply@campusseguro.local"},
                          "subject": "Alerta CampusSeguro: Sismo",
                          "htmlContent": "<p>Evacuar</p>",
                          "textContent": "Evacuar",
                          "messageVersions": [
                            {"to": [{"email": "ana@example.edu.co", "name": "Ana"}]},
                            {"to": [{"email": "luis@example.edu.co"}]}
                          ]
                        }
                        """)));
    }

    @Test
    void sendAll_shouldSplitRecipientsIntoBatchesOfTheConfiguredSize() {
        server.stubFor(post("/v3/smtp/email").willReturn(aResponse().withStatus(201)));
        List<EmailMessage> messages = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            messages.add(to("persona" + i + "@example.edu.co"));
        }

        Map<Integer, String> failures = batchSender(2, new MailTransport("api", "api-key"), 3).sendAll(messages);

        assertTrue(failures.isEmpty());
        server.verify(2, postRequestedFor(urlEqualTo("/v3/smtp/email")).withRequestBody(containing("messageVersions")));
        server.verify(1, postRequestedFor(urlEqualTo("/v3/smtp/email")).withRequestBody(matchingJsonPath("$.to")));
    }

    @Test
    void sendAll_shouldSendOneByOne_whenBrevoRejectsTheBatch() {
        server.stubFor(post("/v3/smtp/email").atPriority(5).willReturn(aResponse().withStatus(201)));
        server.stubFor(post("/v3/smtp/email").atPriority(1)
                .withRequestBody(containing("messageVersions"))
                .willReturn(aResponse().withStatus(400).withBody("{\"code\":\"invalid_parameter\"}")));
        server.stubFor(post("/v3/smtp/email").atPriority(2)
                .withRequestBody(containing("malo@"))
                .willReturn(aResponse().withStatus(400).withBody("correo invalido")));

        Map<Integer, String> failures = sender("api-key", 1)
                .sendAll(List.of(MESSAGE, to("malo@example"), to("luis@example.edu.co")));

        assertEquals(Set.of(1), failures.keySet());
        assertTrue(failures.get(1).startsWith("Brevo respondió 400"));
        server.verify(1, postRequestedFor(urlEqualTo("/v3/smtp/email")).withRequestBody(containing("messageVersions")));
        server.verify(3, postRequestedFor(urlEqualTo("/v3/smtp/email")).withRequestBody(matchingJsonPath("$.to")));
    }

    @Test
    void sendAll_shouldRetryTheBatchAndNotSendOneByOne_whenBrevoIsUnavailable() {
        server.stubFor(post("/v3/smtp/email").willReturn(aResponse().withStatus(503)));

        Map<Integer, String> failures = sender("api-key", 3)
                .sendAll(List.of(MESSAGE, to("luis@example.edu.co"), to("eva@example.edu.co")));

        assertEquals(Set.of(0, 1, 2), failures.keySet());
        assertTrue(failures.get(0).startsWith("Brevo respondió 503"));
        server.verify(3, postRequestedFor(urlEqualTo("/v3/smtp/email")).withRequestBody(containing("messageVersions")));
        server.verify(0, postRequestedFor(urlEqualTo("/v3/smtp/email")).withRequestBody(matchingJsonPath("$.to")));
    }

    @Test
    void sendAll_shouldBatchOnlyMessagesWithTheSameContent() {
        server.stubFor(post("/v3/smtp/email").willReturn(aResponse().withStatus(201)));
        EmailMessage other = new EmailMessage("eva@example.edu.co", null, "[Operativa] Corte", "<p>Corte</p>", "Corte");

        Map<Integer, String> failures = sender("api-key", 3)
                .sendAll(List.of(MESSAGE, other, to("luis@example.edu.co")));

        assertTrue(failures.isEmpty());
        server.verify(1, postRequestedFor(urlEqualTo("/v3/smtp/email")).withRequestBody(containing("messageVersions")));
        server.verify(1, postRequestedFor(urlEqualTo("/v3/smtp/email")).withRequestBody(containing("eva@example.edu.co")));
    }

    @Test
    void sendAll_shouldSendEveryMessageBySmtpInParallel() {
        when(mailSender.createMimeMessage())
                .thenAnswer(invocation -> new MimeMessage(Session.getDefaultInstance(new Properties())));
        List<EmailMessage> messages = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            messages.add(to("persona" + i + "@example.edu.co"));
        }

        Map<Integer, String> failures = batchSender(500, new MailTransport("smtp", "api-key"), 3).sendAll(messages);

        assertTrue(failures.isEmpty());
        verify(mailSender, org.mockito.Mockito.times(6)).send(any(MimeMessage.class));
        server.verify(0, postRequestedFor(urlEqualTo("/v3/smtp/email")));
    }

    @Test
    void sendAll_shouldReportEachSmtpFailureByPosition() {
        when(mailSender.createMimeMessage())
                .thenAnswer(invocation -> new MimeMessage(Session.getDefaultInstance(new Properties())));
        org.mockito.Mockito.doThrow(new org.springframework.mail.MailSendException("SMTP caído"))
                .when(mailSender).send(any(MimeMessage.class));

        Map<Integer, String> failures = batchSender(500, new MailTransport("smtp", "api-key"), 1)
                .sendAll(List.of(MESSAGE, to("luis@example.edu.co")));

        assertEquals(Set.of(0, 1), failures.keySet());
        assertTrue(failures.get(0).contains("SMTP caído"));
    }

    @Test
    void sendAll_shouldFailEveryMessage_whenSenderIsBlank() {
        BrevoEmailSender sender = new BrevoEmailSender(
                mailSender, RestClient.builder(), "api-key", server.baseUrl(), 500, 1000,
                " ", "CampusSeguro", 3, 10);

        Map<Integer, String> failures = sender.sendAll(List.of(MESSAGE, to("luis@example.edu.co")));

        assertEquals(Set.of(0, 1), failures.keySet());
        server.verify(0, postRequestedFor(urlEqualTo("/v3/smtp/email")));
    }

    @Test
    void sendAll_shouldReturnEmpty_whenThereAreNoMessages() {
        assertTrue(sender("api-key", 3).sendAll(List.of()).isEmpty());
        server.verify(0, postRequestedFor(urlEqualTo("/v3/smtp/email")));
    }
}
