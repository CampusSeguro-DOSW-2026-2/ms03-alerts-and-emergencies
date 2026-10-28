package edu.eci.dosw.campusseguro.alertas.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class ActorResolverTest {

    private static final UUID USER_ID =
            UUID.fromString("11111111-1111-4111-8111-111111111111");

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

    private void authenticate(RequesterIdentity identity) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        identity,
                        "n/a",
                        identity.authorities()));
    }

    // resolve

    @Test
    void resolve_shouldReturnAuthenticationName_whenUserIsAuthenticated() {
        authenticateAs("coordinator-1", new SimpleGrantedAuthority("ROLE_COORDINADOR"));

        assertEquals("coordinator-1", resolver.resolve());
    }

    @Test
    void requester_shouldReturnIdentityActorAndRoles_whenPrincipalIsRequesterIdentity() {
        authenticate(new RequesterIdentity(
                USER_ID,
                Set.of(UserRole.COORDINADOR),
                "ms03"));

        RequesterContext context = resolver.requester();

        assertEquals(USER_ID.toString(), context.actor());
        assertEquals(Set.of(UserRole.COORDINADOR), context.roles());
        assertEquals("ms03", context.serviceName());
        assertTrue(context.has(Permission.ALERT_MANAGE));
        assertTrue(context.has(Permission.ALERT_RECEIVE));
        assertFalse(context.isSimulationOnly());
        assertFalse(context.isServiceMs06());
    }

    @Test
    void requester_shouldReturnAuditActorWithServicePrefix_whenServiceIsMs06() {
        authenticate(new RequesterIdentity(
                USER_ID,
                Set.of(UserRole.ORGANIZADOR),
                "MS06"));

        RequesterContext context = resolver.requester();

        assertEquals("ms06", context.serviceName());
        assertTrue(context.isServiceMs06());
        assertEquals("ms06:" + USER_ID, context.auditActor());
        assertTrue(context.isSimulationOnly());
    }

    @Test
    void requester_shouldMapAuthoritiesToOfficialRoles_whenPrincipalIsNotRequesterIdentity() {
        authenticateAs(
                "coordinator-1",
                new SimpleGrantedAuthority("ms03:alert:manage"),
                new SimpleGrantedAuthority("ROLE_COORDINADOR"));

        RequesterContext context = resolver.requester();

        assertEquals(Set.of(UserRole.COORDINADOR), context.roles());
        assertTrue(context.has(Permission.ALERT_MANAGE));
    }

    @Test
    void requester_shouldIgnoreUnknownAuthorities_whenPrincipalIsNotRequesterIdentity() {
        authenticateAs("auditor-1", new SimpleGrantedAuthority("ROLE_UNKNOWN"));

        RequesterContext context = resolver.requester();

        assertTrue(context.roles().isEmpty());
        assertEquals("auditor-1", context.actor());
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
        authenticateAs("a".repeat(100), new SimpleGrantedAuthority("ROLE_COORDINADOR"));

        String actor = resolver.resolve();

        assertEquals(64, actor.length());
        assertEquals("a".repeat(64), actor);
    }

    @Test
    void resolve_shouldNotTruncateActor_whenNameIsExactly64Chars() {
        String name = "b".repeat(64);
        authenticateAs(name, new SimpleGrantedAuthority("ROLE_COORDINADOR"));

        assertEquals(name, resolver.resolve());
    }
}
