package edu.eci.dosw.campusseguro.alertas.client.campus;

import edu.eci.dosw.campusseguro.alertas.entity.enums.ScopeType;
import edu.eci.dosw.campusseguro.alertas.exception.ExternalServiceUnavailableException;
import edu.eci.dosw.campusseguro.alertas.exception.InvalidReferenceException;
import edu.eci.dosw.campusseguro.alertas.security.CorrelationIds;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Validación real del alcance contra MS02.
 *
 * <p>MS02 no publica una validación por lotes, así que se consulta cada id con los endpoints que ya
 * expone: {@code GET /api/v1/buildings/{id}} y {@code GET /api/v1/safe-zones/{id}}. Un 404 significa
 * que el id no existe; cualquier otro error se propaga como servicio no disponible.
 */
@Component
@Profile("!local & !test")
public class RestCampusScopeClient implements CampusScopeClient {

    private static final String MS02 = "MS02";

    private static final String SCOPE_FIELD = "alcance.ids";

    private final RestClient ms02;

    private final String buildingsPath;

    private final String safeZonesPath;

    public RestCampusScopeClient(
            RestClient.Builder builder,
            @Value("${app.campus.base-url:http://localhost:8080}") String baseUrl,
            @Value("${app.campus.service-key:}") String serviceKey,
            @Value("${app.campus.buildings-path:/api/v1/buildings/{id}}") String buildingsPath,
            @Value("${app.campus.safe-zones-path:/api/v1/safe-zones/{id}}") String safeZonesPath,
            @Value("${app.campus.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${app.campus.read-timeout-ms:2000}") long readTimeoutMs) {

        this.buildingsPath = buildingsPath;
        this.safeZonesPath = safeZonesPath;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        RestClient.Builder configured = builder.clone()
                .requestFactory(requestFactory)
                .defaultHeader("X-Service-Name", "ms03");

        if (serviceKey != null && !serviceKey.isBlank()) {
            configured.defaultHeader("X-Service-Key", serviceKey);
        }

        this.ms02 = configured.baseUrl(baseUrl).build();
    }

    @Override
    public void validate(ScopeType type, List<String> ids) {
        if (type == null || type == ScopeType.CAMPUS || ids == null || ids.isEmpty()) {
            return;
        }

        String path = type == ScopeType.BUILDING ? buildingsPath : safeZonesPath;
        List<String> invalid = new ArrayList<>();

        for (String id : ids) {
            if (id == null || id.isBlank() || !exists(path, id)) {
                invalid.add(id);
            }
        }

        if (!invalid.isEmpty()) {
            throw new InvalidReferenceException(SCOPE_FIELD, invalid);
        }
    }

    private boolean exists(String path, String id) {
        try {
            ms02.get()
                    .uri(path, id)
                    .header(CorrelationIds.HEADER, CorrelationIds.current())
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (status == 400 || status == 404) {
                return false;
            }
            throw unavailable(ex);
        } catch (RestClientException ex) {
            throw unavailable(ex);
        }
    }

    private ExternalServiceUnavailableException unavailable(Throwable cause) {
        return new ExternalServiceUnavailableException(MS02, cause);
    }
}
