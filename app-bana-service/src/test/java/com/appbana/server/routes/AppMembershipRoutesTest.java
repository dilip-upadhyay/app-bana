package com.appbana.server.routes;

import com.appbana.ApiServer;
import com.appbana.JdbcManager;
import com.appbana.security.AppMembershipService;
import com.appbana.service.SessionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AppMembershipRoutesTest — S2.7 (docs/planning/TENANT_ISOLATION_IMPLEMENTATION_TASKS.md).
 *
 * <p>Covers the new {@code GET/POST/DELETE /api/tenants/{t}/apps/{a}/members} routes: owner-only
 * gate on all three verbs (deliberately stricter than {@code AppRoutes}'s owner-or-member
 * management gate, S2.6 — see {@code AppMembershipRoutes}'s class Javadoc for why), and that grant
 * accepts all 3 {@link AppMembershipService.Role} values including {@code end-user}.
 */
public class AppMembershipRoutesTest {

    private static final int PORT = 18098;
    private static final String BASE_URL = "http://localhost:" + PORT;
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String TENANT = "t_s27_owner";
    private static final String OTHER_TENANT = "t_s27_other";
    private static final String APP_ID = "s27-fixture-app";

    private String ownerSession;
    private String memberSession;
    private String crossTenantSession;

    @BeforeAll
    public static void startServer() throws Exception {
        ApiServer.startJdk(PORT);
    }

    @BeforeEach
    public void setUp() throws Exception {
        try (Connection c = JdbcManager.getConnection("default");
             Statement s = c.createStatement()) {
            s.execute("DELETE FROM appbana_apps WHERE tenant_id IN ('" + TENANT + "', '" + OTHER_TENANT + "')");
            s.execute("DELETE FROM appbana_app_members WHERE tenant_id IN ('" + TENANT + "', '" + OTHER_TENANT + "')");
        }

        ownerSession = SessionService.createSession("s27_owner", TENANT).sessionId();
        memberSession = SessionService.createSession("s27_member", TENANT).sessionId();
        crossTenantSession = SessionService.createSession("s27_cross_tenant", OTHER_TENANT).sessionId();

        HttpResponse<String> appRes = send("POST", "/appbana-studio/" + TENANT + "/apps", ownerSession,
                MAPPER.writeValueAsString(Map.of("id", APP_ID, "name", "S2.7 fixture app", "version", "1.0.0")));
        assertEquals(201, appRes.statusCode(), "Test fixture setup: app creation must succeed: " + appRes.body());
        assertTrue(AppMembershipService.isMember(TENANT, APP_ID, "s27_owner"), "S2.3: creator must be an owner");

        AppMembershipService.grant(TENANT, APP_ID, "s27_member", AppMembershipService.Role.MEMBER, "test-setup");
    }

    private HttpResponse<String> send(String method, String path, String sessionOrNull, String bodyOrNull) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder().uri(URI.create(BASE_URL + path));
        if (sessionOrNull != null) {
            b.header("X-Session-Token", sessionOrNull);
        }
        switch (method) {
            case "GET" -> b.GET();
            case "DELETE" -> b.DELETE();
            default -> b.method(method, HttpRequest.BodyPublishers.ofString(bodyOrNull != null ? bodyOrNull : "{}"))
                    .header("Content-Type", "application/json");
        }
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String membersPath() {
        return "/api/tenants/" + TENANT + "/apps/" + APP_ID + "/members";
    }

    @Test
    public void ownerCanListMembers() throws Exception {
        HttpResponse<String> res = send("GET", membersPath(), ownerSession, null);
        assertEquals(200, res.statusCode(), res.body());
        JsonNode members = MAPPER.readTree(res.body()).get("members");
        assertEquals(2, members.size(), "owner + the seeded member row");
    }

    @Test
    public void memberCannotListOrGrantOrRevokeMembers() throws Exception {
        HttpResponse<String> listRes = send("GET", membersPath(), memberSession, null);
        assertEquals(403, listRes.statusCode(), "a member role must not be able to list membership: " + listRes.body());

        HttpResponse<String> grantRes = send("POST", membersPath(), memberSession,
                MAPPER.writeValueAsString(Map.of("userId", "s27_intruder", "role", "owner")));
        assertEquals(403, grantRes.statusCode(),
                "a member must not be able to grant itself (or anyone) owner — that would be self-escalation: " + grantRes.body());

        HttpResponse<String> revokeRes = send("DELETE", membersPath() + "?userId=s27_owner", memberSession, null);
        assertEquals(403, revokeRes.statusCode(), "a member must not be able to revoke another member: " + revokeRes.body());
    }

    @Test
    public void ownerCanGrantAllThreeRolesIncludingEndUser() throws Exception {
        for (AppMembershipService.Role role : AppMembershipService.Role.values()) {
            String userId = "s27_grantee_" + role.getValue();
            HttpResponse<String> res = send("POST", membersPath(), ownerSession,
                    MAPPER.writeValueAsString(Map.of("userId", userId, "role", role.getValue())));
            assertEquals(200, res.statusCode(), "owner must be able to grant role " + role.getValue() + ": " + res.body());
            assertTrue(AppMembershipService.isMember(TENANT, APP_ID, userId));
        }
    }

    @Test
    public void ownerCanRevokeAMember() throws Exception {
        HttpResponse<String> res = send("DELETE", membersPath() + "?userId=s27_member", ownerSession, null);
        assertEquals(200, res.statusCode(), res.body());
        assertFalse(AppMembershipService.isMember(TENANT, APP_ID, "s27_member"));
    }

    @Test
    public void crossTenantNonMemberIsRejectedAtTheTenantGate() throws Exception {
        HttpResponse<String> res = send("GET", membersPath(), crossTenantSession, null);
        assertEquals(403, res.statusCode(),
                "a caller with no membership row at all on a foreign-tenant app must still 403 at the tenant gate: " + res.body());
    }

    @Test
    public void crossTenantOwnerCanManageMembers() throws Exception {
        AppMembershipService.grant(TENANT, APP_ID, "s27_cross_tenant", AppMembershipService.Role.OWNER, "test-setup");

        HttpResponse<String> res = send("GET", membersPath(), crossTenantSession, null);
        assertEquals(200, res.statusCode(),
                "an owner-role membership grant must admit a cross-tenant caller past both the tenant gate "
                        + "(membership exception) and this route's own owner-only gate: " + res.body());
    }

    @Test
    public void unauthenticatedRequestIsRejected() throws Exception {
        HttpResponse<String> res = send("GET", membersPath(), null, null);
        assertEquals(401, res.statusCode());
    }

    @Test
    public void invalidRoleValueIsRejectedWith400() throws Exception {
        HttpResponse<String> res = send("POST", membersPath(), ownerSession,
                MAPPER.writeValueAsString(Map.of("userId", "s27_bad_role", "role", "superadmin")));
        assertEquals(400, res.statusCode(), res.body());
    }
}
