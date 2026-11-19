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
 * <p>La ruta la publica el API Gateway hacia MS06. La llamada se autoriza con la clave de servicio
 * ({@code X-Service-Key}) y una identidad de sistema; no depende de un usuario humano.
 */
@Component
@Profile("!local & !test")
public class RestAlertTypeSyncClient implements AlertTypeSyncClient {

    private static final String MS06 = "MS06";

    private final RestClient ms06;

    private final String path;

    private final String systemUserId;

    private final String systemUserRole;

    private final String systemUserPermissions;

    public RestAlertTypeSyncClient(
            RestClient.Builder builder,
            @Value("${app.alerts.types.base-url:http://localhost:8080}") String baseUrl,
            @Value("${app.alerts.types.path:/api/v1/tipos-alerta}") String path,
            @Value("${app.alerts.types.service-key:}") String serviceKey,
            @Value("${app.alerts.types.system-user-id:"
                    + "00000000-0000-4000-8000-000000000000}") String systemUserId,
            @Value("${app.alerts.types.system-user-role:SERVICE_MS03}") String systemUserRole,
            @Value("${app.alerts.types.system-user-permissions:"
                    + "ms06:real_event:read}") String systemUserPermissions,
            @Value("${app.alerts.types.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${app.alerts.types.read-timeout-ms:2000}") long readTimeoutMs) {

        this.path = path;
        this.systemUserId = systemUserId;
        this.systemUserRole = systemUserRole;
        this.systemUserPermissions = systemUserPermissions;

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
                    .headers(headers -> {
                        if (systemUserId != null && !systemUserId.isBlank()) {
                            headers.set("X-User-Id", systemUserId);
                        }
                        if (systemUserRole != null && !systemUserRole.isBlank()) {
                            headers.set("X-User-Role", systemUserRole);
                        }
                        if (systemUserPermissions != null && !systemUserPermissions.isBlank()) {
                            headers.set("X-User-Permission", systemUserPermissions);
                        }
                        headers.set(CorrelationIds.HEADER, CorrelationIds.current());
                    })
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<RemoteAlertType>>() {
                    });
            return types == null ? List.of() : types;
        } catch (RestClientException ex) {
            throw new ExternalServiceUnavailableException(MS06, ex);
        }
    }
}
