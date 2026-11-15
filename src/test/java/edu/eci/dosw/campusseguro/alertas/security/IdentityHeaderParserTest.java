package edu.eci.dosw.campusseguro.alertas.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class IdentityHeaderParserTest {

    private static final String USER_ID =
            "11111111-1111-4111-8111-111111111111";

    private static final String PERMISSIONS =
            "ms03:alert:receive,ms03:alert:manage";

    private final IdentityHeaderParser parser = new IdentityHeaderParser();

    private MockHttpServletRequest request(String userId, String roles, String permissions) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (userId != null) {
            request.addHeader(IdentityHeaderParser.USER_ID_HEADER, userId);
        }
        if (roles != null) {
            request.addHeader(IdentityHeaderParser.USER_ROLE_HEADER, roles);
        }
        if (permissions != null) {
            request.addHeader(IdentityHeaderParser.USER_PERMISSION_HEADER, permissions);
        }
        return request;
    }

    private MockHttpServletRequest request(String userId, String roles) {
        return request(userId, roles, PERMISSIONS);
    }

    @Test
    void parse_shouldBuildRequesterIdentity() {
        MockHttpServletRequest request = request(
                USER_ID, "ESTUDIANTE, COORDINADOR", PERMISSIONS);
        request.addHeader(IdentityHeaderParser.SERVICE_NAME_HEADER, "MS06");

        RequesterIdentity identity = parser.parse(request);

        assertEquals(UUID.fromString(USER_ID), identity.userId());
        assertEquals(Set.of("ESTUDIANTE", "COORDINADOR"), identity.roles());
        assertEquals(Set.of("ms03:alert:receive", "ms03:alert:manage"), identity.permissions());
        assertEquals("ms06", identity.serviceName());
    }

    @Test
    void parse_shouldKeepPermissionsOfOtherModules() {
        RequesterIdentity identity = parser.parse(request(
                USER_ID, "COORDINADOR", "ms02:campus:read,ms03:alert:receive"));

        assertTrue(identity.permissions().contains("ms02:campus:read"));
        assertEquals(2, identity.permissions().size());
    }

    @Test
    void parse_shouldAcceptARoleOutsideTheFormerCatalog() {
        RequesterIdentity identity = parser.parse(request(
                USER_ID, "DIRECTOR_DE_BIENESTAR", "ms03:alert:manage"));

        assertEquals(Set.of("DIRECTOR_DE_BIENESTAR"), identity.roles());
        assertEquals(Set.of("ms03:alert:manage"), identity.permissions());
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
    void parse_shouldRejectMissingPermissions() {
        assertInvalid(request(USER_ID, "ESTUDIANTE", null), "X-User-Permission");
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
    void parse_shouldRejectPermissionHeaderLongerThanAllowed() {
        String permissions = "ms03:alert:receive,".repeat(300);

        assertInvalid(request(USER_ID, "ESTUDIANTE", permissions), "formato válido");
    }

    @Test
    void parse_shouldRejectMalformedRoleName() {
        assertInvalid(request(USER_ID, "desconocido", "ms03:alert:receive"), "no es válido");
    }

    @Test
    void parse_shouldRejectMalformedPermissionCode() {
        assertInvalid(
                request(USER_ID, "ESTUDIANTE", "ms03:alert"),
                "no es válido");
    }

    @Test
    void parse_shouldRejectRoleListWithoutValidRoles() {
        assertInvalid(request(USER_ID, ","), "al menos un rol");
    }

    @Test
    void parse_shouldRejectPermissionListWithoutValidPermissions() {
        assertInvalid(request(USER_ID, "ESTUDIANTE", " , "), "al menos un permiso");
    }

    private void assertInvalid(MockHttpServletRequest request, String messagePart) {
        InvalidIdentityHeadersException ex = assertThrows(
                InvalidIdentityHeadersException.class,
                () -> parser.parse(request));

        assertEquals(true, ex.getMessage().contains(messagePart));
    }
}
