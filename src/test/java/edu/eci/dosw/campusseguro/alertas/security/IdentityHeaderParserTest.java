package edu.eci.dosw.campusseguro.alertas.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class IdentityHeaderParserTest {

    private static final String USER_ID =
            "11111111-1111-4111-8111-111111111111";

    private final IdentityHeaderParser parser = new IdentityHeaderParser();

    private MockHttpServletRequest request(String userId, String roles) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (userId != null) {
            request.addHeader(IdentityHeaderParser.USER_ID_HEADER, userId);
        }
        if (roles != null) {
            request.addHeader(IdentityHeaderParser.USER_ROLE_HEADER, roles);
        }
        return request;
    }

    @Test
    void parse_shouldBuildRequesterIdentity() {
        MockHttpServletRequest request = request(USER_ID, "ESTUDIANTE, ROLE_COORDINADOR");
        request.addHeader(IdentityHeaderParser.SERVICE_NAME_HEADER, "MS06");

        RequesterIdentity identity = parser.parse(request);

        assertEquals(UUID.fromString(USER_ID), identity.userId());
        assertEquals(Set.of(UserRole.ESTUDIANTE, UserRole.COORDINADOR), identity.roles());
        assertEquals("ms06", identity.serviceName());
    }

    @Test
    void parse_shouldRejectMissingUserId() {
        assertInvalid(request(null, "ESTUDIANTE"), "X-User-Id");
    }

    @Test
    void parse_shouldRejectMissingRoles() {
        assertInvalid(request(USER_ID, null), "X-User-Role");
    }

    @Test
    void parse_shouldRejectMalformedUserId() {
        assertInvalid(request("usr-001", "ESTUDIANTE"), "formato válido");
    }

    @Test
    void parse_shouldRejectUserIdLongerThan36Chars() {
        assertInvalid(request("1".repeat(37), "ESTUDIANTE"), "formato válido");
    }

    @Test
    void parse_shouldRejectRoleHeaderLongerThan512Chars() {
        String roles = "ESTUDIANTE,".repeat(47);

        assertInvalid(request(USER_ID, roles), "formato válido");
    }

    @Test
    void parse_shouldRejectUnknownRole() {
        assertInvalid(request(USER_ID, "DESCONOCIDO"), "no es válido");
    }

    @Test
    void parse_shouldRejectRoleListWithoutValidRoles() {
        assertInvalid(request(USER_ID, ","), "al menos un rol");
    }

    private void assertInvalid(MockHttpServletRequest request, String messagePart) {
        InvalidIdentityHeadersException ex = assertThrows(
                InvalidIdentityHeadersException.class,
                () -> parser.parse(request));

        assertEquals(true, ex.getMessage().contains(messagePart));
    }
}
