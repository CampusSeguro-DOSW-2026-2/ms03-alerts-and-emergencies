package edu.eci.dosw.campusseguro.alertas.client.catalog;

import edu.eci.dosw.campusseguro.alertas.dto.request.RecipientRequest;
import edu.eci.dosw.campusseguro.alertas.entity.enums.ScopeType;
import edu.eci.dosw.campusseguro.alertas.exception.ExternalServiceUnavailableException;
import edu.eci.dosw.campusseguro.alertas.exception.InvalidReferenceException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
@Profile("!local & !test")
public class RestCatalogClient implements CatalogClient {

    private static final String SCOPE_FIELD = "alcance.ids";
    private static final String RECIPIENTS_FIELD = "destinatarios";
    private static final String MS01 = "MS01";
    private static final String MS02 = "MS02";

    private final RestClient ms01;
    private final RestClient ms02;
    private final String scopeValidationPath;
    private final String recipientValidationPath;
    private final String userPath;
    private final String roleUsersPath;

    public RestCatalogClient(
            RestClient.Builder builder,
            @Value("${app.catalog.ms01-url:http://localhost:8081}") String ms01Url,
            @Value("${app.catalog.ms02-url:http://localhost:8082}") String ms02Url,
            @Value("${app.catalog.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${app.catalog.read-timeout-ms:5000}") long readTimeoutMs,
            @Value("${app.catalog.service-token:}") String serviceToken,
            @Value("${app.catalog.ms02.validate-scope-path:/api/v1/catalogo/validar-alcance}")
                    String scopeValidationPath,
            @Value("${app.catalog.ms01.validate-recipients-path:/api/v1/catalogo/validar-destinatarios}")
                    String recipientValidationPath,
            @Value("${app.catalog.ms01.user-path:/api/v1/usuarios/{id}}") String userPath,
            @Value("${app.catalog.ms01.role-users-path:/api/v1/usuarios?rol={role}}") String roleUsersPath) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        RestClient.Builder configured = builder.clone().requestFactory(requestFactory);
        if (serviceToken != null && !serviceToken.isBlank()) {
            configured.defaultHeader("Authorization", "Bearer " + serviceToken);
        }

        this.ms01 = configured.clone().baseUrl(ms01Url).build();
        this.ms02 = configured.clone().baseUrl(ms02Url).build();
        this.scopeValidationPath = scopeValidationPath;
        this.recipientValidationPath = recipientValidationPath;
        this.userPath = userPath;
        this.roleUsersPath = roleUsersPath;
    }

    @Override
    public void validateBuildingsZones(ScopeType type, List<String> ids) {
        if (type == ScopeType.CAMPUS || ids == null || ids.isEmpty()) {
            return;
        }
        ValidationResponse response = postValidation(
                ms02,
                MS02,
                scopeValidationPath,
                new ScopeValidationRequest(type.name(), ids));
        assertValid(response, SCOPE_FIELD);
    }

    @Override
    public void validateRecipients(List<RecipientRequest> recipients) {
        if (recipients == null || recipients.isEmpty()) {
            return;
        }
        List<RecipientPayload> payload = recipients.stream()
                .map(recipient -> new RecipientPayload(recipient.type().name(), recipient.id()))
                .toList();
        ValidationResponse response = postValidation(
                ms01,
                MS01,
                recipientValidationPath,
                new RecipientValidationRequest(payload));
        assertValid(response, RECIPIENTS_FIELD);
    }

    @Override
    public Optional<ResolvedUser> findUserById(String userId) {
        try {
            ResolvedUser user = ms01.get()
                    .uri(userPath, userId)
                    .retrieve()
                    .body(ResolvedUser.class);
            return Optional.ofNullable(user);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            throw unavailable(MS01, ex);
        } catch (RestClientException ex) {
            throw unavailable(MS01, ex);
        }
    }

    @Override
    public List<ResolvedUser> getUsersByRole(String role) {
        try {
            List<ResolvedUser> users = ms01.get()
                    .uri(roleUsersPath, role)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<ResolvedUser>>() {
                    });
            return users == null ? List.of() : users;
        } catch (RestClientException ex) {
            throw unavailable(MS01, ex);
        }
    }

    private ValidationResponse postValidation(
            RestClient client, String service, String path, Object payload) {
        try {
            ValidationResponse response = client.post()
                    .uri(path)
                    .body(payload)
                    .retrieve()
                    .body(ValidationResponse.class);
            return response == null ? new ValidationResponse(List.of()) : response;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 400 || ex.getStatusCode().value() == 422) {
                return parseInvalidReferences(service, ex);
            }
            throw unavailable(service, ex);
        } catch (RestClientException ex) {
            throw unavailable(service, ex);
        }
    }

    private ValidationResponse parseInvalidReferences(String service, RestClientResponseException ex) {
        try {
            ValidationResponse response = ex.getResponseBodyAs(ValidationResponse.class);
            if (response != null && response.invalidIds() != null && !response.invalidIds().isEmpty()) {
                return response;
            }
        } catch (RuntimeException ignored) {
            throw unavailable(service, ex);
        }
        throw unavailable(service, ex);
    }

    private void assertValid(ValidationResponse response, String field) {
        if (response.invalidIds() != null && !response.invalidIds().isEmpty()) {
            throw new InvalidReferenceException(field, response.invalidIds());
        }
    }

    private ExternalServiceUnavailableException unavailable(String service, Throwable cause) {
        return new ExternalServiceUnavailableException(service, cause);
    }

    record ScopeValidationRequest(String tipo, List<String> ids) {
    }

    record RecipientPayload(String tipo, String id) {
    }

    record RecipientValidationRequest(List<RecipientPayload> destinatarios) {
    }

    record ValidationResponse(List<String> invalidIds) {
    }
}
