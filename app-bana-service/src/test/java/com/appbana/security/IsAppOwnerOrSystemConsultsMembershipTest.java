package com.appbana.security;

import com.appbana.ApiServer;
import com.appbana.JdbcManager;
import com.appbana.approval.ApprovalService;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IsAppOwnerOrSystemConsultsMembershipTest — S2.9 (docs/planning/TENANT_ISOLATION_IMPLEMENTATION_TASKS.md).
 *
 * <p>{@link AppAuthorization#isAppOwnerOrSystem} (S2.5) has 4 canonical call sites (per the
 * S2.5 tracker row): {@code ApprovalService} (3 internal call sites — {@code
 * hasCheckerOrOwnerPermission}, {@code hasCheckerL2OrOwnerPermission}, {@code
 * hasMakerOrOwnerPermission}), {@code RoleRoutes} (grant/revoke/read-others'-roles),
 * {@code SchemaRoutes} (GET/POST/DELETE {@code /schema}), and {@code UserRoutes} ({@code
 * isAppOwner} field on {@code GET /api/users/me}). {@code AppMembershipRoutes} (S2.7) is a
 * separate, later, 5th consumer of the same check — not one of these 4 — already fully covered
 * by {@code AppMembershipRoutesTest}.
 *
 * <p>{@code AppAuthorizationTest} already proves the shared method's own contract exhaustively at
 * the unit level (membership-first, author-fallback-only-when-no-membership-row). What none of
 * the existing suites prove is that each of the 4 call sites actually *reaches* a real, wired-in
 * app the same way in practice: this class grants OWNER membership to a user who is deliberately
 * NOT the app's {@code author} column, so a call site that (bug) fell back to checking the author
 * field instead of consulting membership would fail every test here even though {@code
 * AppAuthorizationTest} stays green. Each positive assertion is paired with a negative control (an
 * outsider with no membership row and no author match) so a call site that vacuously admits
 * everyone would also be caught.
 */
public class IsAppOwnerOrSystemConsultsMembershipTest {

    private static final int PORT = 18099;
    private static final String BASE_URL = "http://localhost:" + PORT;
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String TENANT = "s29-consult-tenant";
    private static final String APP_ID = "s29-consult-app";
    private static final String ENTITY = "S29Entity";
    private static final String AUTHOR = "s29_author";
    private static final String MEMBERSHIP_OWNER = "s29_membership_owner";
    private static final String OUTSIDER = "s29_outsider";

    private String authorSession;
    private String membershipOwnerSession;
    private String outsiderSession;

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

        authorSession = SessionService.createSession(AUTHOR, TENANT).sessionId();
        membershipOwnerSession = SessionService.createSession(MEMBERSHIP_OWNER, TENANT).sessionId();
        outsiderSession = SessionService.createSession(OUTSIDER, TENANT).sessionId();

        HttpResponse<String> appRes = send("POST", "/appbana-studio/" + TENANT + "/apps", authorSession,
                MAPPER.writeValueAsString(Map.of("id", APP_ID, "name", "S2.9 fixture app", "version", "1.0.0")));
        assertEquals(201, appRes.statusCode(), "Test fixture setup: app creation must succeed: " + appRes.body());

        String schemaJson = MAPPER.writeValueAsString(Map.of(
                "name", ENTITY,
                "tenantId", TENANT,
                "appId", APP_ID,
                "fields", List.of(Map.of("name", "id", "type", "integer", "primaryKey", true, "autoIncrement", true))
        ));
        HttpResponse<String> schemaRes = send("POST", "/schema", authorSession, schemaJson);
        assertEquals(200, schemaRes.statusCode(), "Test fixture setup: schema creation must succeed: " + schemaRes.body());

        // MEMBERSHIP_OWNER is deliberately NOT the author (appbana_apps.author == AUTHOR) — the
        // only thing that makes them an owner is this explicit membership grant.
        com.appbana.security.AppMembershipService.grant(TENANT, APP_ID, MEMBERSHIP_OWNER,
                com.appbana.security.AppMembershipService.Role.OWNER, "test-setup");
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

    // ── ApprovalService's 3 call sites — pure static methods, called directly (no HTTP) ────────

    @Test
    public void hasCheckerOrOwnerPermissionConsultsMembershipNotJustAuthor() {
        assertTrue(ApprovalService.hasCheckerOrOwnerPermission(TENANT, APP_ID, ENTITY, MEMBERSHIP_OWNER),
                "a membership-only OWNER (not the author, no checker role) must satisfy hasCheckerOrOwnerPermission");
        assertFalse(ApprovalService.hasCheckerOrOwnerPermission(TENANT, APP_ID, ENTITY, OUTSIDER),
                "negative control: an outsider with no membership row and no checker role must be denied");
    }

    @Test
    public void hasCheckerL2OrOwnerPermissionConsultsMembershipNotJustAuthor() {
        assertTrue(ApprovalService.hasCheckerL2OrOwnerPermission(TENANT, APP_ID, ENTITY, MEMBERSHIP_OWNER),
                "a membership-only OWNER must satisfy hasCheckerL2OrOwnerPermission");
        assertFalse(ApprovalService.hasCheckerL2OrOwnerPermission(TENANT, APP_ID, ENTITY, OUTSIDER));
    }

    @Test
    public void hasMakerOrOwnerPermissionConsultsMembershipNotJustAuthor() {
        assertTrue(ApprovalService.hasMakerOrOwnerPermission(TENANT, APP_ID, ENTITY, MEMBERSHIP_OWNER),
                "a membership-only OWNER must satisfy hasMakerOrOwnerPermission");
        assertFalse(ApprovalService.hasMakerOrOwnerPermission(TENANT, APP_ID, ENTITY, OUTSIDER));
    }

    // ── RoleRoutes — grant / revoke / read-someone-else's-roles ─────────────────────────────────

    @Test
    public void roleRoutesGrantAdmitsMembershipOwnerAndDeniesOutsider() throws Exception {
        String rolesPath = "/api/tenants/" + TENANT + "/apps/" + APP_ID + "/roles";

        HttpResponse<String> ownerRes = send("POST", rolesPath, membershipOwnerSession,
                MAPPER.writeValueAsString(Map.of("entityName", ENTITY, "userId", OUTSIDER, "role", "maker")));
        assertEquals(200, ownerRes.statusCode(),
                "a membership-only OWNER (not the author) must be able to grant roles: " + ownerRes.body());

        HttpResponse<String> outsiderRes = send("POST", rolesPath, outsiderSession,
                MAPPER.writeValueAsString(Map.of("entityName", ENTITY, "userId", "someone-else", "role", "maker")));
        assertEquals(403, outsiderRes.statusCode(), "negative control: an outsider must not be able to grant roles");
    }

    @Test
    public void roleRoutesRevokeAdmitsMembershipOwnerAndDeniesOutsider() throws Exception {
        String rolesPath = "/api/tenants/" + TENANT + "/apps/" + APP_ID + "/roles";
        // Seed a role to revoke, granted by the author (already an owner via S2.3's auto-grant).
        send("POST", rolesPath, authorSession,
                MAPPER.writeValueAsString(Map.of("entityName", ENTITY, "userId", OUTSIDER, "role", "maker")));

        HttpResponse<String> outsiderRes = send("DELETE", rolesPath + "?entityName=" + ENTITY + "&userId=" + OUTSIDER,
                outsiderSession, null);
        assertEquals(403, outsiderRes.statusCode(), "negative control: an outsider must not be able to revoke roles");

        HttpResponse<String> ownerRes = send("DELETE", rolesPath + "?entityName=" + ENTITY + "&userId=" + OUTSIDER,
                membershipOwnerSession, null);
        assertEquals(200, ownerRes.statusCode(),
                "a membership-only OWNER must be able to revoke roles: " + ownerRes.body());
    }

    @Test
    public void roleRoutesReadingSomeoneElsesRolesAdmitsMembershipOwnerAndDeniesOutsider() throws Exception {
        String rolesPath = "/api/tenants/" + TENANT + "/apps/" + APP_ID + "/roles"
                + "?entityName=" + ENTITY + "&userId=" + AUTHOR;

        // MEMBERSHIP_OWNER reading AUTHOR's roles is "someone else's roles" — requires management authorization.
        HttpResponse<String> ownerRes = send("GET", rolesPath, membershipOwnerSession, null);
        assertEquals(200, ownerRes.statusCode(),
                "a membership-only OWNER must be able to read another user's roles: " + ownerRes.body());

        HttpResponse<String> outsiderRes = send("GET", rolesPath, outsiderSession, null);
        assertEquals(403, outsiderRes.statusCode(), "negative control: an outsider must not read another user's roles");
    }

    // ── SchemaRoutes — GET/POST/DELETE /schema ───────────────────────────────────────────────────

    @Test
    public void schemaRoutesGetAdmitsMembershipOwnerAndDeniesOutsider() throws Exception {
        HttpResponse<String> ownerRes = send("GET", "/schema/" + schemaKey(), membershipOwnerSession, null);
        assertEquals(200, ownerRes.statusCode(),
                "a membership-only OWNER must be able to GET the schema: " + ownerRes.body());

        HttpResponse<String> outsiderRes = send("GET", "/schema/" + schemaKey(), outsiderSession, null);
        assertEquals(403, outsiderRes.statusCode(), "negative control: an outsider must not GET the schema");
    }

    @Test
    public void schemaRoutesPostAdmitsMembershipOwnerAndDeniesOutsider() throws Exception {
        String schemaJson = MAPPER.writeValueAsString(Map.of(
                "name", ENTITY,
                "tenantId", TENANT,
                "appId", APP_ID,
                "fields", List.of(Map.of("name", "id", "type", "integer", "primaryKey", true, "autoIncrement", true),
                        Map.of("name", "note", "type", "text", "primaryKey", false, "autoIncrement", false))
        ));

        HttpResponse<String> outsiderRes = send("POST", "/schema", outsiderSession, schemaJson);
        assertEquals(403, outsiderRes.statusCode(), "negative control: an outsider must not POST (update) the schema");

        HttpResponse<String> ownerRes = send("POST", "/schema", membershipOwnerSession, schemaJson);
        assertEquals(200, ownerRes.statusCode(),
                "a membership-only OWNER must be able to POST (update) the schema: " + ownerRes.body());
    }

    @Test
    public void schemaRoutesDeleteAdmitsMembershipOwnerAndDeniesOutsider() throws Exception {
        HttpResponse<String> outsiderRes = send("DELETE", "/schema/" + schemaKey(), outsiderSession, null);
        assertEquals(403, outsiderRes.statusCode(), "negative control: an outsider must not DELETE the schema");

        HttpResponse<String> ownerRes = send("DELETE", "/schema/" + schemaKey(), membershipOwnerSession, null);
        assertEquals(200, ownerRes.statusCode(),
                "a membership-only OWNER must be able to DELETE the schema: " + ownerRes.body());
    }

    // ── UserRoutes — GET /api/users/me?tenantId=&appId= isAppOwner field ────────────────────────

    @Test
    public void userRoutesMeReportsIsAppOwnerTrueForMembershipOwnerAndFalseForOutsider() throws Exception {
        String mePath = "/api/users/me?tenantId=" + TENANT + "&appId=" + APP_ID;

        HttpResponse<String> ownerRes = send("GET", mePath, membershipOwnerSession, null);
        assertEquals(200, ownerRes.statusCode());
        JsonNode ownerJson = MAPPER.readTree(ownerRes.body());
        assertTrue(ownerJson.get("isAppOwner").asBoolean(),
                "GET /api/users/me must report isAppOwner=true for a membership-only OWNER, not just the author");

        HttpResponse<String> outsiderRes = send("GET", mePath, outsiderSession, null);
        assertEquals(200, outsiderRes.statusCode());
        JsonNode outsiderJson = MAPPER.readTree(outsiderRes.body());
        assertFalse(outsiderJson.get("isAppOwner").asBoolean(),
                "negative control: GET /api/users/me must report isAppOwner=false for an outsider");
    }
}
