package edu.eci.dosw.campusseguro.alertas.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class AccessActorTest {

    private static final UUID USER_ID =
            UUID.fromString("11111111-1111-4111-8111-111111111111");

    @Test
    void from_shouldReturnAnonymousActor_whenAuthenticationIsNull() {
        AccessActor actor = AccessActor.from(null);

        assertEquals("", actor.userId());
        assertTrue(actor.roles().isEmpty());
        assertTrue(actor.permissions().isEmpty());
        assertFalse(actor.hasPermission(Permission.ALERT_READ_HISTORY));
    }

    @Test
    void from_shouldReadRolesAndPermissions_whenPrincipalIsRequesterIdentity() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(new RequesterIdentity(
                USER_ID,
                Set.of("DIRECTOR_DE_BIENESTAR"),
                Set.of("ms03:alert:read_history"),
                "ms03"));

        AccessActor actor = AccessActor.from(authentication);

        assertEquals(USER_ID.toString(), actor.userId());
        assertEquals(Set.of("DIRECTOR_DE_BIENESTAR"), actor.roles());
        assertTrue(actor.hasPermission(Permission.ALERT_READ_HISTORY));
        assertFalse(actor.hasPermission(Permission.ALERT_MANAGE));
    }

    @Test
    void from_shouldMapAuthoritiesToPermissions_whenPrincipalIsNotRequesterIdentity() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("usr-001");
        when(authentication.getAuthorities()).thenAnswer(invocation -> List.of(
                new SimpleGrantedAuthority("ms03:alert:manage"),
                new SimpleGrantedAuthority("ms03:alert:receive"),
                (GrantedAuthority) () -> " "));

        AccessActor actor = AccessActor.from(authentication);

        assertEquals("usr-001", actor.userId());
        assertEquals(Set.of("ms03:alert:manage", "ms03:alert:receive"), actor.permissions());
        assertTrue(actor.roles().isEmpty());
    }

    @Test
    void from_shouldNotDeriveAnythingFromRoleAuthorities() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("usr-001");
        when(authentication.getAuthorities()).thenAnswer(invocation -> List.of(
                new SimpleGrantedAuthority("ROLE_COORDINADOR")));

        AccessActor actor = AccessActor.from(authentication);

        assertTrue(actor.permissions().isEmpty());
        assertTrue(actor.roles().isEmpty());
        assertFalse(actor.hasPermission(Permission.ALERT_MANAGE));
    }

    @Test
    void hasPermission_shouldBeFalse_whenPermissionIsNull() {
        AccessActor actor = new AccessActor("usr-001", Set.of(), Set.of());

        assertFalse(actor.hasPermission(null));
    }
}
