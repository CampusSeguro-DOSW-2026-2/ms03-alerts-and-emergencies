package edu.eci.dosw.campusseguro.alertas.client.catalog;

import com.fasterxml.jackson.annotation.JsonProperty;
import edu.eci.dosw.campusseguro.alertas.dto.request.RecipientRequest;
import edu.eci.dosw.campusseguro.alertas.entity.enums.RecipientType;
import edu.eci.dosw.campusseguro.alertas.entity.enums.ScopeType;
import edu.eci.dosw.campusseguro.alertas.exception.ExternalServiceUnavailableException;
import edu.eci.dosw.campusseguro.alertas.exception.InvalidReferenceException;
import edu.eci.dosw.campusseguro.alertas.security.CorrelationIds;
import edu.eci.dosw.campusseguro.alertas.security.UserRole;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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

    private static final String RECIPIENTS_FIELD = "destinatarios";
    private static final String MS01 = "MS01";
    private static final int PAGE_SIZE = 100;

    private final RestClient ms01;
    private final CatalogStubData scopeCatalog;

    public RestCatalogClient(
            RestClient.Builder builder,
            CatalogStubData scopeCatalog,
            @Value("${app.catalog.base-url:http://localhost:8081}") String baseUrl,
            @Value("${app.catalog.technical-user-id:}") String technicalUserId,
            @Value("${app.catalog.technical-user-role:AUDITOR}") String technicalUserRole,
            @Value("${app.catalog.service-key:}") String serviceKey,
            @Value("${app.catalog.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${app.catalog.read-timeout-ms:2000}") long readTimeoutMs) {

        this.scopeCatalog = scopeCatalog;

        String userId = requireTechnicalUserId(technicalUserId);
        String role = technicalUserRole == null || technicalUserRole.isBlank()
                ? UserRole.AUDITOR.name()
                : technicalUserRole.strip().toUpperCase();

        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        RestClient.Builder configured = builder.clone()
                .requestFactory(requestFactory)
                .defaultHeader("X-User-Id", userId)
                .defaultHeader("X-User-Role", role)
                .defaultHeader("X-Service-Name", "ms03");

        if (serviceKey != null && !serviceKey.isBlank()) {
            configured.defaultHeader("X-Service-Key", serviceKey);
        }

        this.ms01 = configured.baseUrl(baseUrl).build();
    }

    @Override
    public void validateBuildingsZones(ScopeType type, List<String> ids) {
        scopeCatalog.validateScope(type, ids);
    }

    @Override
    public void validateRecipients(List<RecipientRequest> recipients) {
        if (recipients == null || recipients.isEmpty()) {
            return;
        }

        Set<String> invalid = new LinkedHashSet<>();
        Set<String> checkedUsers = new HashSet<>();

        for (RecipientRequest recipient : recipients) {
            if (recipient == null
                    || recipient.type() == null
                    || recipient.id() == null
                    || recipient.id().isBlank()) {
                invalid.add(recipient == null ? "null" : recipient.id());
                continue;
            }

            if (recipient.type() == RecipientType.USER) {
                if (!isUuid(recipient.id())) {
                    invalid.add(recipient.id());
                    continue;
                }
                if (checkedUsers.add(recipient.id())
                        && findUserById(recipient.id()).isEmpty()) {
                    invalid.add(recipient.id());
                }
                continue;
            }

            if (UserRole.from(recipient.id()).isEmpty()) {
                invalid.add(recipient.id());
            }
        }

        if (!invalid.isEmpty()) {
            throw new InvalidReferenceException(
                    RECIPIENTS_FIELD,
                    List.copyOf(invalid));
        }
    }

    @Override
    public Optional<ResolvedUser> findUserById(String userId) {
        try {
            Ms01User user = ms01.get()
                    .uri("/api/v1/users/{id}", userId)
                    .header(CorrelationIds.HEADER, CorrelationIds.current())
                    .retrieve()
                    .body(Ms01User.class);
            return Optional.ofNullable(user)
                    .filter(this::isActive)
                    .map(this::toResolvedUser);
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (status == 400 || status == 404) {
                return Optional.empty();
            }
            throw unavailable(ex);
        } catch (RestClientException ex) {
            throw unavailable(ex);
        }
    }

    @Override
    public List<ResolvedUser> getUsersByRole(String role) {
        List<ResolvedUser> users = new ArrayList<>();
        int page = 0;

        while (true) {
            Ms01Page<Ms01User> response;
            int currentPage = page;
            try {
                response = ms01.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/api/v1/users")
                                .queryParam("rol", role)
                                .queryParam("estado", "ACTIVO")
                                .queryParam("page", currentPage)
                                .queryParam("size", PAGE_SIZE)
                                .build())
                        .header(CorrelationIds.HEADER, CorrelationIds.current())
                        .retrieve()
                        .body(new ParameterizedTypeReference<Ms01Page<Ms01User>>() {
                        });
            } catch (RestClientResponseException ex) {
                if (ex.getStatusCode().value() == 400) {
                    throw new InvalidReferenceException(
                            RECIPIENTS_FIELD,
                            List.of(role));
                }
                throw unavailable(ex);
            } catch (RestClientException ex) {
                throw unavailable(ex);
            }

            if (response == null
                    || response.contenido() == null
                    || response.contenido().isEmpty()) {
                break;
            }

            response.contenido().stream()
                    .filter(this::isActive)
                    .map(this::toResolvedUser)
                    .forEach(users::add);

            if (page + 1 >= response.totalPaginas()) {
                break;
            }
            page++;
        }

        return users;
    }

    private boolean isActive(Ms01User user) {
        return user.estado() == null || "ACTIVO".equalsIgnoreCase(user.estado());
    }

    private ResolvedUser toResolvedUser(Ms01User user) {
        return new ResolvedUser(
                user.id(),
                user.correo(),
                displayName(user),
                user.roles() == null ? Set.of() : Set.copyOf(user.roles()),
                user.estado());
    }

    private String displayName(Ms01User user) {
        List<String> parts = new ArrayList<>();
        addPart(parts, user.primerNombre());
        addPart(parts, user.segundoNombre());
        addPart(parts, user.primerApellido());
        addPart(parts, user.segundoApellido());
        return String.join(" ", parts);
    }

    private void addPart(List<String> parts, String value) {
        if (value != null && !value.isBlank()) {
            parts.add(value.strip());
        }
    }

    private boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private String requireTechnicalUserId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Configura app.catalog.technical-user-id con el UUID "
                            + "de la cuenta técnica de MS01");
        }
        String userId = value.strip();
        if (!isUuid(userId)) {
            throw new IllegalStateException(
                    "app.catalog.technical-user-id debe ser un UUID válido");
        }
        return userId;
    }

    private ExternalServiceUnavailableException unavailable(Throwable cause) {
        return new ExternalServiceUnavailableException(MS01, cause);
    }

    public record Ms01User(
            String id,
            @JsonProperty("primer_nombre") String primerNombre,
            @JsonProperty("segundo_nombre") String segundoNombre,
            @JsonProperty("primer_apellido") String primerApellido,
            @JsonProperty("segundo_apellido") String segundoApellido,
            String correo,
            List<String> roles,
            String estado) {
    }

    public record Ms01Page<T>(
            List<T> contenido,
            int page,
            int size,
            @JsonProperty("total_elementos") long totalElementos,
            @JsonProperty("total_paginas") int totalPaginas) {
    }
}
