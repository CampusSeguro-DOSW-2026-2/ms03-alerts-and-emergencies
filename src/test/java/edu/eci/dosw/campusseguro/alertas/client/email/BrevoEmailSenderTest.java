package edu.eci.dosw.campusseguro.alertas.client.email;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import edu.eci.dosw.campusseguro.alertas.exception.EmailSendException;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class BrevoEmailSenderTest {

    private static final EmailMessage MESSAGE = new EmailMessage(
            "ana@example.edu.co",
            "Ana",
            "Alerta CampusSeguro: Terremoto",
            "<p>Evacuar</p>",
            "Evacuar");

    private WireMockServer server;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    private BrevoEmailSender sender(String apiKey, int maxAttempts) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(500));
        factory.setReadTimeout(Duration.ofMillis(1000));
        return new BrevoEmailSender(
                RestClient.builder().requestFactory(factory),
                apiKey,
                server.baseUrl(),
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
                          "subject": "Alerta CampusSeguro: Terremoto",
                          "htmlContent": "<p>Evacuar</p>",
                          "textContent": "Evacuar"
                        }
                        """)));
    }

    @Test
    void send_shouldThrowWithoutCallingBrevo_whenApiKeyIsMissing() {
        EmailSendException ex = assertThrows(
                EmailSendException.class,
                () -> sender("", 3).send(MESSAGE));

        assertTrue(ex.getMessage().contains("API key"));
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
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(200));
        factory.setReadTimeout(Duration.ofMillis(100));

        BrevoEmailSender sender = new BrevoEmailSender(
                RestClient.builder().requestFactory(factory),
                "api-key",
                server.baseUrl(),
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
}
