package edu.eci.dosw.campusseguro.alertas.client.alerttype;

import edu.eci.dosw.campusseguro.alertas.exception.ExternalServiceUnavailableException;
import edu.eci.dosw.campusseguro.alertas.security.CorrelationIds;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Cliente real del catálogo de tipos de alerta.
 *
 * <p>La ruta la publica el API Gateway hacia MS06. Es una ruta interna: la llamada se autoriza con
 * la clave de servicio ({@code X-Service-Key}) y el Gateway inyecta {@code X-Service-Name: ms03};
 * no usa identidad de usuario.
 */
@Component
@Profile("!local & !test")
public class RestAlertTypeSyncClient implements AlertTypeSyncClient {

    private static final String MS06 = "MS06";

    private final RestClient ms06;

    private final String path;

    public RestAlertTypeSyncClient(
            RestClient.Builder builder,
            @Value("${app.alerts.types.base-url:http://localhost:8080}") String baseUrl,
            @Value("${app.alerts.types.path:/api/v1/tipos-alerta}") String path,
            @Value("${app.alerts.types.service-key:}") String serviceKey,
            @Value("${app.alerts.types.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${app.alerts.types.read-timeout-ms:2000}") long readTimeoutMs) {

        this.path = path;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        RestClient.Builder configured = builder.clone()
                .requestFactory(requestFactory)
                .defaultHeader("X-Service-Name", "ms03");

        if (serviceKey != null && !serviceKey.isBlank()) {
            configured.defaultHeader("X-Service-Key", serviceKey);
        }

        this.ms06 = configured.baseUrl(baseUrl).build();
    }

    @Override
    public List<RemoteAlertType> fetch() {
        try {
            List<RemoteAlertType> types = ms06.get()
                    .uri(path)
                    .headers(headers -> headers.set(CorrelationIds.HEADER, CorrelationIds.current()))
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<RemoteAlertType>>() {
                    });
            return types == null ? List.of() : types;
        } catch (RestClientException ex) {
            throw new ExternalServiceUnavailableException(MS06, ex);
        }
    }
}
