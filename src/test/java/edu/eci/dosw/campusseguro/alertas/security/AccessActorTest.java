package edu.eci.dosw.campusseguro.alertas.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class AccessActorTest {

    @Test
    void from_shouldReturnAnonymousActor_whenAuthenticationIsNull() {
        AccessActor actor = AccessActor.from(null);

        assertEquals("", actor.userId());
        assertTrue(actor.roles().isEmpty());
    }

    @Test
    void from_shouldNormalizeRolesAndKeepSubject() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("usr-001");
        when(authentication.getAuthorities()).thenAnswer(invocation -> List.of(
                new SimpleGrantedAuthority("ROLE_COORDINATOR"),
                new SimpleGrantedAuthority("student"),
                (org.springframework.security.core.GrantedAuthority) () -> " "));

        AccessActor actor = AccessActor.from(authentication);

        assertEquals("usr-001", actor.userId());
        assertEquals(Set.of("COORDINATOR", "STUDENT"), actor.roles());
    }
}
