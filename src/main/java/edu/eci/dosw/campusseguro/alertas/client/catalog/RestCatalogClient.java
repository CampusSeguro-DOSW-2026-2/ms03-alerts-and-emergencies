package edu.eci.dosw.campusseguro.alertas.client.catalog;

import com.fasterxml.jackson.annotation.JsonProperty;
import edu.eci.dosw.campusseguro.alertas.client.campus.CampusScopeClient;
import edu.eci.dosw.campusseguro.alertas.dto.request.RecipientRequest;
import edu.eci.dosw.campusseguro.alertas.entity.enums.RecipientType;
import edu.eci.dosw.campusseguro.alertas.entity.enums.ScopeType;
import edu.eci.dosw.campusseguro.alertas.exception.ExternalServiceUnavailableException;
import edu.eci.dosw.campusseguro.alertas.exception.InvalidReferenceException;
import edu.eci.dosw.campusseguro.alertas.security.CorrelationIds;
import edu.eci.dosw.campusseguro.alertas.security.UserRole;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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

/**
 * Cliente real del catálogo.
 *
 * <p>Los usuarios se resuelven con las rutas internas de MS01
 * ({@code POST /api/v1/users/batch} y {@code GET /api/v1/users/by-role}), que se autorizan por el
 * servicio llamante ({@code X-Service-Name}) y no por la matriz Rol-Permiso. Por eso ya no se envía
 * identidad de usuario ni se necesita una cuenta técnica.
 *
 * <p>El alcance (edificios y zonas) se valida contra MS02 a través de {@link CampusScopeClient}.
 */
@Component
@Profile("!local & !test")
public class RestCatalogClient implements CatalogClient {

    private static final String RECIPIENTS_FIELD = "destinatarios";

    private static final String MS01 = "MS01";

    /** Tamaño máximo de una página de MS01 y de una consulta por lotes. */
    private static final int PAGE_SIZE = 100;

    private final RestClient ms01;

    private final CampusScopeClient campusScopeClient;

    public RestCatalogClient(
            RestClient.Builder builder,
            CampusScopeClient campusScopeClient,
            @Value("${app.catalog.base-url:http://localhost:8080}") String baseUrl,
            @Value("${app.catalog.service-key:}") String serviceKey,
            @Value("${app.catalog.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${app.catalog.read-timeout-ms:2000}") long readTimeoutMs) {

        this.campusScopeClient = campusScopeClient;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        RestClient.Builder configured = builder.clone()
                .requestFactory(requestFactory)
                .defaultHeader("X-Service-Name", "ms03");

        if (serviceKey != null && !serviceKey.isBlank()) {
            configured.defaultHeader("X-Service-Key", serviceKey);
        }

        this.ms01 = configured.baseUrl(baseUrl).build();
    }

    @Override
    public void validateBuildingsZones(ScopeType type, List<String> ids) {
        campusScopeClient.validate(type, ids);
    }

    @Override
    public void validateRecipients(List<RecipientRequest> recipients) {
        if (recipients == null || recipients.isEmpty()) {
            return;
        }

        Set<String> invalid = new LinkedHashSet<>();
        Set<String> requestedUsers = new LinkedHashSet<>();

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
                } else {
                    requestedUsers.add(recipient.id());
                }
                continue;
            }

            if (UserRole.from(recipient.id()).isEmpty()) {
                invalid.add(recipient.id());
            }
        }

        Map<String, ResolvedUser> found = findUsersByIds(requestedUsers);
        for (String userId : requestedUsers) {
            if (!found.containsKey(userId)) {
                invalid.add(userId);
            }
        }

        if (!invalid.isEmpty()) {
            throw new InvalidReferenceException(RECIPIENTS_FIELD, List.copyOf(invalid));
        }
    }

    @Override
    public Optional<ResolvedUser> findUserById(String userId) {
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(findUsersByIds(List.of(userId)).get(userId));
    }

    /**
     * Consulta por lotes los usuarios activos. Los ids inexistentes o inactivos no aparecen en el
     * mapa resultante.
     */
    private Map<String, ResolvedUser> findUsersByIds(Collection<String> userIds) {
        Map<String, ResolvedUser> found = new LinkedHashMap<>();
        List<String> ids = new ArrayList<>(userIds);

        for (int from = 0; from < ids.size(); from += PAGE_SIZE) {
            List<String> chunk = ids.subList(from, Math.min(from + PAGE_SIZE, ids.size()));
            BatchUsersResponse response = callBatch(chunk);
            if (response == null || response.usuarios() == null) {
                continue;
            }
            response.usuarios().stream()
                    .filter(this::isActive)
                    .map(this::toResolvedUser)
                    .forEach(user -> found.put(user.id(), user));
        }

        return found;
    }

    private BatchUsersResponse callBatch(List<String> ids) {
        try {
            return ms01.post()
                    .uri("/api/v1/users/batch")
                    .header(CorrelationIds.HEADER, CorrelationIds.current())
                    .body(new BatchUsersRequest(ids))
                    .retrieve()
                    .body(BatchUsersResponse.class);
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (status == 400) {
                throw new InvalidReferenceException(RECIPIENTS_FIELD, ids);
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
            Ms01Page<Ms01User> response = callByRole(role, page);
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

    private Ms01Page<Ms01User> callByRole(String role, int page) {
        try {
            return ms01.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/users/by-role")
                            .queryParam("rol", role)
                            .queryParam("page", page)
                            .queryParam("size", PAGE_SIZE)
                            .build())
                    .header(CorrelationIds.HEADER, CorrelationIds.current())
                    .retrieve()
                    .body(new ParameterizedTypeReference<Ms01Page<Ms01User>>() {
                    });
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 400) {
                throw new InvalidReferenceException(RECIPIENTS_FIELD, List.of(role));
            }
            throw unavailable(ex);
        } catch (RestClientException ex) {
            throw unavailable(ex);
        }
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

    private ExternalServiceUnavailableException unavailable(Throwable cause) {
        return new ExternalServiceUnavailableException(MS01, cause);
    }

    record BatchUsersRequest(List<String> ids) {
    }

    record BatchUsersResponse(
            List<Ms01User> usuarios,
            @JsonProperty("no_encontrados") List<String> noEncontrados) {
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
