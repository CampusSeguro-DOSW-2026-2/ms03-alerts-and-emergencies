package edu.eci.dosw.campusseguro.alertas.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import edu.eci.dosw.campusseguro.alertas.client.catalog.CatalogClient;
import edu.eci.dosw.campusseguro.alertas.client.catalog.ResolvedUser;
import edu.eci.dosw.campusseguro.alertas.entity.AlertRecipientEntity;
import edu.eci.dosw.campusseguro.alertas.entity.enums.RecipientType;
import edu.eci.dosw.campusseguro.alertas.exception.ExternalServiceUnavailableException;
import edu.eci.dosw.campusseguro.alertas.repository.AlertRecipientRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecipientResolverServiceTest {

    private static final UUID ALERT_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final ResolvedUser ANA = new ResolvedUser("usr-001", "ana@example.edu.co", "Ana");
    private static final ResolvedUser LUIS = new ResolvedUser("usr-002", "luis@example.edu.co", "Luis");

    @Mock
    private AlertRecipientRepository recipientRepository;

    @Mock
    private CatalogClient catalogClient;

    private RecipientResolverService service;

    @BeforeEach
    void setUp() {
        service = new RecipientResolverService(recipientRepository, catalogClient);
    }

    private AlertRecipientEntity recipient(RecipientType type, String refId) {
        AlertRecipientEntity recipient = new AlertRecipientEntity();
        recipient.setAlertId(ALERT_ID);
        recipient.setRecipientType(type);
        recipient.setRecipientRefId(refId);
        return recipient;
    }

    @Test
    void resolve_shouldReturnEmpty_whenThereAreNoRecipients() {
        when(recipientRepository.findByAlertId(ALERT_ID)).thenReturn(List.of());

        assertTrue(service.resolve(ALERT_ID).isEmpty());
        verifyNoInteractions(catalogClient);
    }

    @Test
    void resolve_shouldResolveDirectUsers() {
        when(recipientRepository.findByAlertId(ALERT_ID))
                .thenReturn(List.of(recipient(RecipientType.USER, "usr-001")));
        when(catalogClient.findUserById("usr-001")).thenReturn(Optional.of(ANA));

        assertEquals(List.of(ANA), service.resolve(ALERT_ID));
    }

    @Test
    void resolve_shouldExpandRoles() {
        when(recipientRepository.findByAlertId(ALERT_ID))
                .thenReturn(List.of(recipient(RecipientType.ROLE, "STUDENT")));
        when(catalogClient.getUsersByRole("STUDENT")).thenReturn(List.of(ANA, LUIS));

        assertEquals(List.of(ANA, LUIS), service.resolve(ALERT_ID));
    }

    @Test
    void resolve_shouldDeduplicateUsersAcrossDirectAndRoleRecipients() {
        when(recipientRepository.findByAlertId(ALERT_ID)).thenReturn(List.of(
                recipient(RecipientType.USER, "usr-001"),
                recipient(RecipientType.ROLE, "STUDENT")));
        when(catalogClient.findUserById("usr-001")).thenReturn(Optional.of(ANA));
        when(catalogClient.getUsersByRole("STUDENT")).thenReturn(List.of(ANA, LUIS));

        assertEquals(List.of(ANA, LUIS), service.resolve(ALERT_ID));
    }

    @Test
    void resolve_shouldSkipMissingUserAndKeepTheOthers() {
        when(recipientRepository.findByAlertId(ALERT_ID)).thenReturn(List.of(
                recipient(RecipientType.USER, "usr-404"),
                recipient(RecipientType.USER, "usr-001")));
        when(catalogClient.findUserById("usr-404")).thenReturn(Optional.empty());
        when(catalogClient.findUserById("usr-001")).thenReturn(Optional.of(ANA));

        assertEquals(List.of(ANA), service.resolve(ALERT_ID));
        verify(catalogClient).findUserById("usr-404");
    }

    @Test
    void resolve_shouldPropagateCatalogFailure_whenRoleLookupFails() {
        when(recipientRepository.findByAlertId(ALERT_ID))
                .thenReturn(List.of(recipient(RecipientType.ROLE, "STUDENT")));
        when(catalogClient.getUsersByRole("STUDENT"))
                .thenThrow(new ExternalServiceUnavailableException("MS01"));

        assertThrows(ExternalServiceUnavailableException.class, () -> service.resolve(ALERT_ID));
    }
}
