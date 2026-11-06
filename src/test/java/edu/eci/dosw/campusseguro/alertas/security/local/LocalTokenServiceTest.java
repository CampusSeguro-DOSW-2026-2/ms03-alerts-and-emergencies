package edu.eci.dosw.campusseguro.alertas.security.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class LocalTokenServiceTest {

    private static final String SECRET = "campusseguro-local-secret-key-change-me-0123456789";
    private static final Instant NOW = Instant.parse("2026-10-15T18:00:00Z");

    private SecretKey key() {
        return new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Test
    void generate_shouldCreateSignedTokenWithRolesAndExpiry() {
        LocalTokenService service = new LocalTokenService(
                new NimbusJwtEncoder(new ImmutableSecret<>(key())),
                Clock.fixed(NOW, ZoneOffset.UTC),
                "roles");

        String token = service.generate("local-coordinator", List.of("COORDINATOR"));

        JwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        ((NimbusJwtDecoder) decoder)
                .setJwtValidator(jwt -> org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success());
        Jwt jwt = decoder.decode(token);
        assertEquals("campusseguro-local", jwt.getClaimAsString("iss"));
        assertEquals("local-coordinator", jwt.getSubject());
        assertEquals(List.of("COORDINATOR"), jwt.getClaimAsStringList("roles"));
        assertEquals(NOW, jwt.getIssuedAt());
        assertEquals(NOW.plusSeconds(8 * 3600), jwt.getExpiresAt());
    }

    @Test
    void controller_shouldExposeTokenForEachPrivilegedRoleAndGenericUser() {
        LocalTokenService service = mock(LocalTokenService.class);
        when(service.generate(eq("local-coordinator"), anyList())).thenReturn("coordinator-token");
        when(service.generate(eq("local-auditor"), anyList())).thenReturn("auditor-token");
        when(service.generate(eq("local-service_ms06"), anyList())).thenReturn("service-token");
        when(service.generate(eq("local-user"), anyList())).thenReturn("user-token");

        Map<String, String> tokens = new LocalTokenController(service).tokens();

        assertEquals("coordinator-token", tokens.get("COORDINATOR"));
        assertEquals("auditor-token", tokens.get("AUDITOR"));
        assertEquals("service-token", tokens.get("SERVICE_MS06"));
        assertEquals("user-token", tokens.get("USER"));
        assertTrue(tokens.containsKey("USER"));
        verify(service).generate("local-coordinator", List.of("COORDINATOR"));
    }

    @Test
    void controller_shouldExposeTokenForCatalogRecipient() {
        LocalTokenService service = mock(LocalTokenService.class);
        when(service.generate(eq("usr-002"), eq(List.of("STUDENT")))).thenReturn("recipient-token");

        Map<String, String> tokens = new LocalTokenController(service).tokens();

        assertEquals("recipient-token", tokens.get("RECIPIENT"));
    }
}
