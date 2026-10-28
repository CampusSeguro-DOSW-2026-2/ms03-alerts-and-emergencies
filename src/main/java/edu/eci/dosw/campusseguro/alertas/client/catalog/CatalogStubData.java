package edu.eci.dosw.campusseguro.alertas.client.catalog;

import edu.eci.dosw.campusseguro.alertas.entity.enums.ScopeType;
import edu.eci.dosw.campusseguro.alertas.exception.ExternalServiceUnavailableException;
import edu.eci.dosw.campusseguro.alertas.exception.InvalidReferenceException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class CatalogStubData {

    private static final String RESOURCE = "catalog-stub.json";
    private static final String SCOPE_FIELD = "alcance.ids";

    private final Set<String> buildingIds;
    private final Set<String> zoneIds;
    private final Set<String> roleIds;
    private final List<UserEntry> users;

    public CatalogStubData(ObjectMapper objectMapper) {
        CatalogData data = load(objectMapper);
        this.buildingIds = ids(data.buildings());
        this.zoneIds = ids(data.zones());
        this.roleIds = ids(data.roles());
        this.users = data.users() == null ? List.of() : List.copyOf(data.users());
    }

    public void validateScope(ScopeType type, List<String> ids) {
        if (type == ScopeType.CAMPUS || ids == null || ids.isEmpty()) {
            return;
        }
        Set<String> known = type == ScopeType.BUILDING ? buildingIds : zoneIds;
        List<String> invalid = ids.stream()
                .filter(id -> !known.contains(id))
                .toList();
        if (!invalid.isEmpty()) {
            throw new InvalidReferenceException(SCOPE_FIELD, invalid);
        }
    }

    public Set<String> roleIds() {
        return roleIds;
    }

    public List<UserEntry> users() {
        return users;
    }

    private CatalogData load(ObjectMapper objectMapper) {
        try (InputStream input = new ClassPathResource(RESOURCE).getInputStream()) {
            CatalogData data = objectMapper.readValue(input, CatalogData.class);
            return data == null
                    ? new CatalogData(List.of(), List.of(), List.of(), List.of())
                    : data;
        } catch (IOException ex) {
            throw new ExternalServiceUnavailableException("catálogo simulado", ex);
        }
    }

    private Set<String> ids(List<Entry> entries) {
        if (entries == null) {
            return Set.of();
        }
        return entries.stream()
                .map(Entry::id)
                .collect(Collectors.toUnmodifiableSet());
    }

    public record Entry(String id, String name) {
    }

    public record UserEntry(
            String id,
            String name,
            String email,
            List<String> roles,
            String estado) {
    }

    public record CatalogData(
            List<Entry> buildings,
            List<Entry> zones,
            List<Entry> roles,
            List<UserEntry> users) {
    }
}
