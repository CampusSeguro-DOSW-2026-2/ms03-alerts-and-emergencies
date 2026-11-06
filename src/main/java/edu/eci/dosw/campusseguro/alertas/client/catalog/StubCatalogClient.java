package edu.eci.dosw.campusseguro.alertas.client.catalog;

import edu.eci.dosw.campusseguro.alertas.client.catalog.CatalogStubData.UserEntry;
import edu.eci.dosw.campusseguro.alertas.dto.request.RecipientRequest;
import edu.eci.dosw.campusseguro.alertas.entity.enums.RecipientType;
import edu.eci.dosw.campusseguro.alertas.entity.enums.ScopeType;
import edu.eci.dosw.campusseguro.alertas.exception.InvalidReferenceException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"local", "test"})
public class StubCatalogClient implements CatalogClient {

    private static final String RECIPIENTS_FIELD = "destinatarios";

    private final CatalogStubData stubData;
    private final Set<String> activeUserIds;

    public StubCatalogClient(CatalogStubData stubData) {
        this.stubData = stubData;
        this.activeUserIds = stubData.users().stream()
                .filter(this::isActive)
                .map(UserEntry::id)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public void validateBuildingsZones(ScopeType type, List<String> ids) {
        stubData.validateScope(type, ids);
    }

    @Override
    public void validateRecipients(List<RecipientRequest> recipients) {
        List<String> invalid = recipients.stream()
                .filter(recipient -> !exists(recipient))
                .map(RecipientRequest::id)
                .toList();
        if (!invalid.isEmpty()) {
            throw new InvalidReferenceException(RECIPIENTS_FIELD, invalid);
        }
    }

    @Override
    public Optional<ResolvedUser> findUserById(String userId) {
        return stubData.users().stream()
                .filter(this::isActive)
                .filter(user -> user.id().equals(userId))
                .findFirst()
                .map(this::toResolvedUser);
    }

    @Override
    public List<ResolvedUser> getUsersByRole(String role) {
        return stubData.users().stream()
                .filter(this::isActive)
                .filter(user -> user.roles().contains(role))
                .map(this::toResolvedUser)
                .toList();
    }

    private boolean exists(RecipientRequest recipient) {
        if (recipient == null || recipient.type() == null || recipient.id() == null) {
            return false;
        }
        return recipient.type() == RecipientType.USER
                ? activeUserIds.contains(recipient.id())
                : stubData.roleIds().contains(recipient.id());
    }

    private boolean isActive(UserEntry user) {
        return user.estado() == null || "ACTIVO".equalsIgnoreCase(user.estado());
    }

    private ResolvedUser toResolvedUser(UserEntry user) {
        return new ResolvedUser(
                user.id(),
                user.email(),
                user.name(),
                user.roles() == null ? Set.of() : Set.copyOf(user.roles()),
                user.estado() == null ? "ACTIVO" : user.estado());
    }
}
