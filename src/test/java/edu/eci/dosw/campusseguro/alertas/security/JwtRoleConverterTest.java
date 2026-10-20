package edu.eci.dosw.campusseguro.alertas.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtRoleConverterTest {

    private Jwt jwt(List<String> roles) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("usr-001");
        if (roles != null) {
            builder.claim("roles", roles);
        }
        return builder.build();
    }

    @Test
    void convert_shouldPrefixAndNormalizeRoles() {
        AbstractAuthenticationToken authentication =
                new JwtRoleConverter("roles").convert(jwt(List.of("coordinator", "ROLE_AUDITOR", " Student ")));

        assertEquals("usr-001", authentication.getName());
        assertEquals(List.of("ROLE_COORDINATOR", "ROLE_AUDITOR", "ROLE_STUDENT"),
                authentication.getAuthorities().stream().map(Object::toString).toList());
    }

    @Test
    void convert_shouldDeduplicateRoles() {
        AbstractAuthenticationToken authentication =
                new JwtRoleConverter("roles").convert(jwt(List.of("COORDINATOR", "coordinator", "ROLE_COORDINATOR")));

        assertEquals(1, authentication.getAuthorities().size());
    }

    @Test
    void convert_shouldReturnNoAuthorities_whenClaimIsMissing() {
        AbstractAuthenticationToken authentication =
                new JwtRoleConverter("roles").convert(jwt(null));

        assertTrue(authentication.getAuthorities().isEmpty());
    }

    @Test
    void convert_shouldUseConfiguredClaimName() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("usr-001")
                .claim("perfiles", List.of("AUDITOR"))
                .build();

        AbstractAuthenticationToken authentication = new JwtRoleConverter("perfiles").convert(jwt);

        assertEquals("ROLE_AUDITOR", authentication.getAuthorities().iterator().next().getAuthority());
    }
}
