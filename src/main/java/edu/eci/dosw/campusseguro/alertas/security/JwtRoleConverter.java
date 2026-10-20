package edu.eci.dosw.campusseguro.alertas.security;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class JwtRoleConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String AUTHORITY_PREFIX = "ROLE_";

    private final String rolesClaim;

    public JwtRoleConverter(@Value("${app.security.roles-claim:roles}") String rolesClaim) {
        this.rolesClaim = rolesClaim;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        List<String> claimValues = jwt.getClaimAsStringList(rolesClaim);
        Collection<GrantedAuthority> authorities = claimValues == null
                ? List.of()
                : claimValues.stream()
                        .filter(Objects::nonNull)
                        .map(String::strip)
                        .filter(value -> !value.isEmpty())
                        .map(this::toAuthority)
                        .distinct()
                        .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                        .toList();
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }

    private String toAuthority(String value) {
        String normalized = value.toUpperCase(Locale.ROOT);
        return normalized.startsWith(AUTHORITY_PREFIX)
                ? normalized
                : AUTHORITY_PREFIX + normalized;
    }
}
