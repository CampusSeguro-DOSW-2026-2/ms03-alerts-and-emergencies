package edu.eci.dosw.campusseguro.alertas.client.catalog;

import edu.eci.dosw.campusseguro.alertas.dto.request.RecipientRequest;
import edu.eci.dosw.campusseguro.alertas.entity.enums.RecipientType;
import edu.eci.dosw.campusseguro.alertas.entity.enums.ScopeType;
import edu.eci.dosw.campusseguro.alertas.exception.ExternalServiceUnavailableException;
import edu.eci.dosw.campusseguro.alertas.exception.InvalidReferenceException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@Profile({"local", "test"})
public class StubCatalogClient implements CatalogClient {

    private static final String RESOURCE = "catalog-stub.json";
    private static final String SCOPE_FIELD = "alcance.ids";
    private static final String RECIPIENTS_FIELD = "destinatarios";

    private final Set<String> buildingIds;
    private final Set<String> zoneIds;
    private final Set<String> roleIds;
    private final List<UserEntry> users;

    public StubCatalogClient(ObjectMapper objectMapper) {
        CatalogData data = load(objectMapper);
        this.buildingIds = ids(data.buildings());
        this.zoneIds = ids(data.zones());
        this.roleIds = ids(data.roles());
        this.users = data.users();
    }

    @Override
    public void validateBuildingsZones(ScopeType type, List<String> ids) {
        if (type == ScopeType.CAMPUS) {
            return;
        }
        Set<String> known = type == ScopeType.BUILDING ? buildingIds : zoneIds;
        List<String> invalid = ids.stream().filter(id -> !known.contains(id)).toList();
        if (!invalid.isEmpty()) {
            throw new InvalidReferenceException(SCOPE_FIELD, invalid);
        }
    }

    @Override
    public void validateRecipients(List<RecipientRequest> recipients) {
        Set<String> userIds = users.stream().map(UserEntry::id).collect(Collectors.toSet());
        List<String> invalid = recipients.stream()
                .filter(recipient -> !(recipient.type() == RecipientType.USER ? userIds : roleIds).contains(recipient.id()))
                .map(RecipientRequest::id)
                .toList();
        if (!invalid.isEmpty()) {
            throw new InvalidReferenceException(RECIPIENTS_FIELD, invalid);
        }
    }

    @Override
    public List<ResolvedUser> getUsersByRole(String role) {
        return users.stream()
                .filter(user -> user.roles().contains(role))
                .map(user -> new ResolvedUser(user.id(), user.email(), user.name()))
                .toList();
    }

    private CatalogData load(ObjectMapper objectMapper) {
        try (InputStream input = new ClassPathResource(RESOURCE).getInputStream()) {
            return objectMapper.readValue(input, CatalogData.class);
        } catch (IOException ex) {
            throw new ExternalServiceUnavailableException("catálogo simulado", ex);
        }
    }

    private Set<String> ids(List<Entry> entries) {
        return entries.stream().map(Entry::id).collect(Collectors.toUnmodifiableSet());
    }

    public record Entry(String id, String name) {
    }

    public record UserEntry(String id, String name, String email, List<String> roles) {
    }

    public record CatalogData(List<Entry> buildings, List<Entry> zones, List<Entry> roles, List<UserEntry> users) {
    }
}
