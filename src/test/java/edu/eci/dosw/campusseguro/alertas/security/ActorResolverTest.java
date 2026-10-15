package edu.eci.dosw.campusseguro.alertas.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class ActorResolverTest {

    private ActorResolver resolver;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        resolver = new ActorResolver();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // helpers 

    private void authenticateAs(String name, GrantedAuthority... authorities) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(name, "n/a", List.of(authorities)));
    }

    // resolve 

    @Test
    void resolve_shouldReturnAuthenticationName_whenUserIsAuthenticated() {
        authenticateAs("coordinator-1", new SimpleGrantedAuthority("ROLE_COORDINATOR"));

        assertEquals("coordinator-1", resolver.resolve());
    }

    @Test
    void resolve_shouldReturnServiceRoleName_whenUserHasServiceRole() {
        authenticateAs("some-token-subject", new SimpleGrantedAuthority(Role.SERVICE_MS06.authority()));

        assertEquals(Role.SERVICE_MS06.name(), resolver.resolve());
    }

    @Test
    void resolve_shouldReturnServiceRoleName_whenServiceRoleIsAmongSeveralAuthorities() {
        authenticateAs(
                "some-token-subject",
                new SimpleGrantedAuthority("ROLE_COORDINATOR"),
                new SimpleGrantedAuthority(Role.SERVICE_MS06.authority()));

        assertEquals(Role.SERVICE_MS06.name(), resolver.resolve());
    }

    @Test
    void resolve_shouldReturnAuthenticationName_whenAuthorityIsNotTheServiceRole() {
        authenticateAs("auditor-1", new SimpleGrantedAuthority("ROLE_AUDITOR"));

        assertEquals("auditor-1", resolver.resolve());
    }

    @Test
    void resolve_shouldThrowAuthenticationNotFound_whenThereIsNoAuthentication() {
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> resolver.resolve());
    }

    @Test
    void resolve_shouldThrowAuthenticationNotFound_whenAuthenticationIsNotAuthenticated() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.unauthenticated("coordinator-1", "n/a"));

        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> resolver.resolve());
    }

    @Test
    void resolve_shouldTruncateActor_whenNameIsLongerThan64Chars() {
        authenticateAs("a".repeat(100), new SimpleGrantedAuthority("ROLE_COORDINATOR"));

        String actor = resolver.resolve();

        assertEquals(64, actor.length());
        assertEquals("a".repeat(64), actor);
    }

    @Test
    void resolve_shouldNotTruncateActor_whenNameIsExactly64Chars() {
        String name = "b".repeat(64);
        authenticateAs(name, new SimpleGrantedAuthority("ROLE_COORDINATOR"));

        assertEquals(name, resolver.resolve());
    }
}