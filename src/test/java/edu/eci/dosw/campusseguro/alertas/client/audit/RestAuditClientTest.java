package edu.eci.dosw.campusseguro.alertas.client.audit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class RestAuditClientTest {

    private static final String EVENTS_PATH = "/api/v1/audit/events";

    private static final String BODY = "{\"modulo\":\"MS03\",\"accion\":\"ACTIVATE\"}";

    private WireMockServer server;

    private RestAuditClient client;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        client = new RestAuditClient(
                RestClient.builder(),
                server.baseUrl(),
                EVENTS_PATH,
                "service-key",
                2000,
                2000);
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void send_shouldPostTheStoredBodyWithTheServiceIdentity() {
        server.stubFor(post(urlPathEqualTo(EVENTS_PATH))
                .willReturn(aResponse().withStatus(202)));

        assertDoesNotThrow(() -> client.send(BODY));

        server.verify(postRequestedFor(urlPathEqualTo(EVENTS_PATH))
                .withHeader("X-Service-Name", equalTo("ms03"))
                .withHeader("X-Service-Key", equalTo("service-key"))
                .withHeader("X-Correlation-ID", containing("-"))
                .withHeader("Content-Type", containing("application/json"))
                .withRequestBody(equalTo(BODY)));
    }

    @Test
    void send_shouldRejectTheFact_whenMs08AnswersAClientError() {
        server.stubFor(post(urlPathEqualTo(EVENTS_PATH))
                .willReturn(aResponse().withStatus(400)));

        assertThrows(AuditRejectedException.class, () -> client.send(BODY));
    }

    @Test
    void send_shouldAskForARetry_whenMs08AnswersAServerError() {
        server.stubFor(post(urlPathEqualTo(EVENTS_PATH))
                .willReturn(aResponse().withStatus(503)));

        assertThrows(AuditDeliveryException.class, () -> client.send(BODY));
    }

    @Test
    void send_shouldAskForARetry_whenTheGatewayIsNotReachable() {
        RestAuditClient unreachable = new RestAuditClient(
                RestClient.builder(),
                "http://localhost:1",
                EVENTS_PATH,
                "service-key",
                500,
                500);

        assertThrows(AuditDeliveryException.class, () -> unreachable.send(BODY));
    }

    @Test
    void send_shouldOmitTheServiceKey_whenItIsNotConfigured() {
        RestAuditClient anonymous = new RestAuditClient(
                RestClient.builder(),
                server.baseUrl(),
                EVENTS_PATH,
                "",
                2000,
                2000);
        server.stubFor(post(urlPathEqualTo(EVENTS_PATH))
                .willReturn(aResponse().withStatus(202)));

        anonymous.send(BODY);

        server.verify(postRequestedFor(urlPathEqualTo(EVENTS_PATH))
                .withHeader("X-Service-Name", equalTo("ms03"))
                .withoutHeader("X-Service-Key"));
    }
}
