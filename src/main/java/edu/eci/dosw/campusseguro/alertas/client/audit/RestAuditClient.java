package edu.eci.dosw.campusseguro.alertas.client.audit;

import edu.eci.dosw.campusseguro.alertas.security.CorrelationIds;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Cliente real de la ingesta de MS08.
 *
 * <p>La llamada va por el API Gateway ({@code POST /api/v1/audit/events} es ruta interna), con la
 * clave de servicio de MS03. El Gateway valida la clave, quita {@code X-Service-Key} e inyecta
 * {@code X-Service-Name}, que es lo que MS08 autoriza.
 */
@Component
public class RestAuditClient implements AuditClient {

    private static final String SERVICE_NAME = "ms03";

    private final RestClient client;
    private final String eventsPath;

    public RestAuditClient(
            RestClient.Builder builder,
            @Value("${app.audit.base-url:http://localhost:8080}") String baseUrl,
            @Value("${app.audit.events-path:/api/v1/audit/events}") String eventsPath,
            @Value("${app.audit.service-key:}") String serviceKey,
            @Value("${app.audit.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${app.audit.read-timeout-ms:5000}") long readTimeoutMs) {

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        RestClient.Builder configured = builder.clone()
                .requestFactory(requestFactory)
                .defaultHeader("X-Service-Name", SERVICE_NAME);

        if (serviceKey != null && !serviceKey.isBlank()) {
            configured.defaultHeader("X-Service-Key", serviceKey);
        }

        this.client = configured.baseUrl(baseUrl).build();
        this.eventsPath = eventsPath;
    }

    @Override
    public void send(String payload) {
        try {
            client.post()
                    .uri(eventsPath)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(CorrelationIds.HEADER, CorrelationIds.current())
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (status >= 400 && status < 500) {
                throw new AuditRejectedException(status);
            }
            throw new AuditDeliveryException("MS08 respondio " + status, ex);
        } catch (RestClientException ex) {
            throw new AuditDeliveryException("No se pudo entregar la auditoria a MS08", ex);
        }
    }
}
