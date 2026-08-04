package com.appbana.server.routes;

import com.appbana.ApiServer;
import com.appbana.JdbcManager;
import com.appbana.security.AppMembershipService;
import com.appbana.service.SessionService;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EndUserMembershipCannotManageAppTest — S2.9 (docs/planning/TENANT_ISOLATION_IMPLEMENTATION_TASKS.md).
 *
 * <p>{@code AppRoutesMembershipTest} is route-centric: every {@code AppRoutes.java} handler ×
 * every role. This class is deliberately persona-centric instead: holding the {@code end-user}
 * role fixed and sweeping across every route FAMILY an app-scoped caller can reach — {@code
 * AppRoutes} (data/config read+write), {@code SchemaRoutes} (entity schema management, gated by
 * the STRICTER owner-only {@code isAppOwnerOrSystem} — a {@code member} is blocked here too, not
 * just {@code end-user}, which this class also asserts explicitly to avoid the two authorization
 * models being conflated), {@code RoleRoutes} (per-entity maker/checker workflow roles — an
 * entirely separate role system from app membership, included here to show the "read your own
 * roles" carve-out is independent of the owner/member/end-user split), and {@code
 * AppMembershipRoutes} (membership management itself). Answers, in one place: "as an app's
 * end-user, what can I actually do?" — 200 on data reads, 403 everywhere else.
 */
public class EndUserMembershipCannotManageAppTest {

    private static final int PORT = 18101;
    private static final String BASE_URL = "http://localhost:" + PORT;
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String TENANT = "s29-enduser-tenant";
    private static final String APP_ID = "s29-enduser-app";
    private static final String ENTITY = "S29EndUserEntity";
    private static final String OWNER_USER = "s29eu_owner";
    private static final String END_USER = "s29eu_enduser";

    private String ownerSession;
    private String endUserSession;

    @BeforeAll
    public static void startServer() throws Exception {
        ApiServer.startJdk(PORT);
    }

    @BeforeEach
    public void setUp() throws Exception {
        try (Connection c = JdbcManager.getConnection("default");
             Statement s = c.createStatement()) {
            s.execute("DELETE FROM appbana_apps WHERE tenant_id = '" + TENANT + "'");
            s.execute("DELETE FROM appbana_app_members WHERE tenant_id = '" + TENANT + "'");
            s.execute("DELETE FROM appbana_schemas WHERE tenant_id = '" + TENANT + "'");
            s.execute("DELETE FROM appbana_user_roles WHERE tenant_id = '" + TENANT + "'");
        }

        ownerSession = SessionService.createSession(OWNER_USER, TENANT).sessionId();
        endUserSession = SessionService.createSession(END_USER, TENANT).sessionId();

        HttpResponse<String> appRes = send("POST", "/appbana-studio/" + TENANT + "/apps", ownerSession,
                MAPPER.writeValueAsString(Map.of("id", APP_ID, "name", "S2.9 end-user fixture", "version", "1.0.0")));
        assertEquals(201, appRes.statusCode(), "Test fixture setup: app creation must succeed: " + appRes.body());

        String schemaJson = MAPPER.writeValueAsString(Map.of(
                "name", ENTITY,
                "tenantId", TENANT,
                "appId", APP_ID,
                "fields", List.of(Map.of("name", "id", "type", "integer", "primaryKey", true, "autoIncrement", true))
        ));
        HttpResponse<String> schemaRes = send("POST", "/schema", ownerSession, schemaJson);
        assertEquals(200, schemaRes.statusCode(), "Test fixture setup: schema creation must succeed: " + schemaRes.body());

        AppMembershipService.grant(TENANT, APP_ID, END_USER, AppMembershipService.Role.END_USER, "test-setup");
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

    private String schemaKey() {
        return TENANT + "_" + APP_ID + "_" + ENTITY;
    }

    // ── AppRoutes: reads succeed, writes are blocked ─────────────────────────────────────────────

    @Test
    public void endUserCanListAndGetTheApp() throws Exception {
        HttpResponse<String> listRes = send("GET", "/appbana-studio/" + TENANT + "/apps", endUserSession, null);
        assertEquals(200, listRes.statusCode());

        HttpResponse<String> getRes = send("GET", "/appbana-studio/" + TENANT + "/apps/" + APP_ID, endUserSession, null);
        assertEquals(200, getRes.statusCode());
    }

    @Test
    public void endUserCannotUpdateOrDeleteTheApp() throws Exception {
        HttpResponse<String> updateRes = send("PUT", "/appbana-studio/" + TENANT + "/apps/" + APP_ID, endUserSession,
                MAPPER.writeValueAsString(Map.of("name", "hijacked")));
        assertEquals(403, updateRes.statusCode());

        HttpResponse<String> deleteRes = send("DELETE", "/appbana-studio/" + TENANT + "/apps/" + APP_ID, endUserSession, null);
        assertEquals(403, deleteRes.statusCode());
    }

    @Test
    public void endUserCannotPublishOrDeployOrManageVersions() throws Exception {
        assertEquals(403, send("POST", "/api/" + TENANT + "/apps/" + APP_ID + "/publish?env=DEV", endUserSession, null).statusCode());
        assertEquals(403, send("PUT", "/api/" + TENANT + "/apps/" + APP_ID + "/deploy/local", endUserSession, null).statusCode());
        assertEquals(403, send("POST", "/api/" + TENANT + "/apps/" + APP_ID + "/versions", endUserSession, null).statusCode());
        assertEquals(403, send("POST", "/api/" + TENANT + "/apps/" + APP_ID + "/restore-schemas", endUserSession, null).statusCode());
    }

    // ── SchemaRoutes: owner-only — a MEMBER would be blocked here too, not just end-user ────────

    @Test
    public void endUserCannotReadOrWriteEntitySchema() throws Exception {
        HttpResponse<String> getRes = send("GET", "/schema/" + schemaKey(), endUserSession, null);
        assertEquals(403, getRes.statusCode(), "isAppOwnerOrSystem denies end-user (same as it would deny a plain member)");

        String schemaJson = MAPPER.writeValueAsString(Map.of(
                "name", ENTITY, "tenantId", TENANT, "appId", APP_ID,
                "fields", List.of(Map.of("name", "id", "type", "integer", "primaryKey", true, "autoIncrement", true))
        ));
        HttpResponse<String> postRes = send("POST", "/schema", endUserSession, schemaJson);
        assertEquals(403, postRes.statusCode());

        HttpResponse<String> deleteRes = send("DELETE", "/schema/" + schemaKey(), endUserSession, null);
        assertEquals(403, deleteRes.statusCode());
    }

    /**
     * Explicitly documents the nuance {@code EndUserMembershipCannotManageAppTest}'s own class
     * Javadoc calls out: {@code SchemaRoutes} uses the strict {@code isAppOwnerOrSystem}, so a
     * plain {@code member} — who WOULD be admitted to {@code AppRoutes}'s management routes — is
     * blocked here too. Without this, a reader could mistakenly assume schema-mgmt 403s are
     * specific to end-user.
     */
    @Test
    public void aPlainMemberIsAlsoDeniedEntitySchemaAccessNotOnlyEndUser() throws Exception {
        String memberSession = SessionService.createSession("s29eu_member", TENANT).sessionId();
        AppMembershipService.grant(TENANT, APP_ID, "s29eu_member", AppMembershipService.Role.MEMBER, "test-setup");

        HttpResponse<String> getRes = send("GET", "/schema/" + schemaKey(), memberSession, null);
        assertEquals(403, getRes.statusCode(),
                "SchemaRoutes' isAppOwnerOrSystem gate blocks a plain member too — only owner (or system) passes");
    }

    // ── RoleRoutes: a separate, per-entity workflow-role system — grant/revoke blocked, ─────────
    // ── reading one's OWN workflow roles is allowed regardless of app-membership role ───────────

    @Test
    public void endUserCannotGrantOrRevokeWorkflowRoles() throws Exception {
        String rolesPath = "/api/tenants/" + TENANT + "/apps/" + APP_ID + "/roles";

        HttpResponse<String> grantRes = send("POST", rolesPath, endUserSession,
                MAPPER.writeValueAsString(Map.of("entityName", ENTITY, "userId", "someone-else", "role", "maker")));
        assertEquals(403, grantRes.statusCode());

        HttpResponse<String> revokeRes = send("DELETE", rolesPath + "?entityName=" + ENTITY + "&userId=" + OWNER_USER,
                endUserSession, null);
        assertEquals(403, revokeRes.statusCode());
    }

    @Test
    public void endUserCanReadTheirOwnWorkflowRolesButNotSomeoneElses() throws Exception {
        String rolesPath = "/api/tenants/" + TENANT + "/apps/" + APP_ID + "/roles";

        HttpResponse<String> ownRolesRes = send("GET",
                rolesPath + "?entityName=" + ENTITY + "&userId=" + END_USER, endUserSession, null);
        assertEquals(200, ownRolesRes.statusCode(),
                "reading one's OWN workflow roles is allowed regardless of app-membership role: " + ownRolesRes.body());

        HttpResponse<String> othersRolesRes = send("GET",
                rolesPath + "?entityName=" + ENTITY + "&userId=" + OWNER_USER, endUserSession, null);
        assertEquals(403, othersRolesRes.statusCode(), "reading someone ELSE's workflow roles still requires management authorization");
    }

    // ── AppMembershipRoutes: membership management itself — light-touch, avoids duplicating ────
    // ── AppMembershipRoutesTest's existing (member-role) coverage of the same owner-only gate ──

    @Test
    public void endUserCannotListOrGrantOrRevokeAppMembership() throws Exception {
        String membersPath = "/api/tenants/" + TENANT + "/apps/" + APP_ID + "/members";

        assertEquals(403, send("GET", membersPath, endUserSession, null).statusCode());
        assertEquals(403, send("POST", membersPath, endUserSession,
                MAPPER.writeValueAsString(Map.of("userId", "intruder", "role", "owner"))).statusCode());
        assertEquals(403, send("DELETE", membersPath + "?userId=" + OWNER_USER, endUserSession, null).statusCode());
    }
}
