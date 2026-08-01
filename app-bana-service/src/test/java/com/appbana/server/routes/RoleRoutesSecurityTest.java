package com.appbana.server.routes;

import com.appbana.ApiServer;
import com.appbana.AppManager;
import com.appbana.JdbcManager;
import com.appbana.SchemaManager;
import com.appbana.approval.UserRoleService;
import com.appbana.model.AppMetadata;
import com.appbana.model.EntitySchema;
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
 * RoleRoutesSecurityTest — Tasks C1.10, C1.11, C1.12 & C1.13
 *
 * Real HTTP-layer end-to-end integration test suite using Java HttpClient over local TCP socket port 18088:
 * 1. Path tenantId is strictly enforced; payload body.tenantId is ignored (Blocker #2).
 * 2. Non-creators and CHECKER users get 403 Forbidden on role grant requests (Blocker #1).
 * 3. App creation forces author to authenticated caller (client author payload ignored).
 * 4. App update preserves author field (author is immutable).
 * 5. POST /schema missing tenantId or appId returns 400 Bad Request.
 * 6. POST /schema on an un-owned app returns 403 Forbidden (C1.10 App ownership guard).
 * 7. POST /schema ONLY bootstraps creator role on NEW schema insert, NOT on updates (C1.10 & C1.13 Insert-only bootstrap).
 * 8. Header identity spoofing (X-User-Id spoofing with valid session) is rejected with 403 (C1.12 Platform identity hardening).
 */
public class RoleRoutesSecurityTest {

    private static final int PORT = 18088;
    private static final String BASE_URL = "http://localhost:" + PORT;
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeAll
    public static void startServer() throws Exception {
        ApiServer.startJdk(PORT);
    }

    // Scoped to this test class's OWN fixture tenants only. A blanket
    // "DELETE FROM appbana_apps"/"appbana_schemas" (no WHERE) here wipes every
    // real app in the shared dev Postgres instance on every `mvn test` run --
    // confirmed to have destroyed the live "Employee Onboarding" AI Builder app's
    // appbana_apps/appbana_schemas rows this way (its appbana_pages rows and
    // physical data tables survived untouched, only the app/schema catalog rows
    // were lost). Keep this IN (...) list in sync with every tenantId literal
    // used by a @Test in this file.
    private static final String TEST_TENANT_IDS_SQL =
            "'t_spoof', 'tenantA', 'tenantB', 't_sec', 't_immutable', 't_schema_sec', 't_getroles', 't_enum'";

    @BeforeEach
    public void cleanTables() throws Exception {
        try (Connection c = JdbcManager.getConnection("default");
             Statement s = c.createStatement()) {
            s.execute("DELETE FROM appbana_user_roles WHERE tenant_id IN (" + TEST_TENANT_IDS_SQL + ")");
            s.execute("DELETE FROM appbana_apps WHERE tenant_id IN (" + TEST_TENANT_IDS_SQL + ")");
            s.execute("DELETE FROM appbana_schemas WHERE tenant_id IN (" + TEST_TENANT_IDS_SQL + ")");
        }
    }

    private String createTestSession(String userId) {
        return SessionService.createSession(userId).sessionId();
    }

    // S1.3: TenantAccessGuard now checks the session's own tenantId against the path tenant on
    // every AppRoutes route (not just RoleRoutes), so a test that reuses the same session across
    // both a RoleRoutes call and an AppRoutes call must carry a real tenantId.
    private String createTestSession(String userId, String tenantId) {
        return SessionService.createSession(userId, tenantId).sessionId();
    }

    @Test
    public void testHttpHeaderUserSpoofingIsRejected() throws Exception {
        String tenantId = "t_spoof";
        String appId = "app_spoof";
        String entityName = "SpoofTarget";
        String realAuthor = "victim_author";
        String attacker = "attacker_user";

        // Attacker creates a valid session for attacker_user
        String attackerSessionToken = createTestSession(attacker);

        // Seed App owned by realAuthor
        AppMetadata app = new AppMetadata(appId, "Spoof App", "1.0.0");
        app.setTenantId(tenantId);
        app.setAuthor(realAuthor);
        AppManager.createApp(tenantId, app);

        EntitySchema.Field idField = new EntitySchema.Field("id", "integer", true, true, null);
        EntitySchema schema = new EntitySchema(entityName, List.of(idField));
        schema.setTenantId(tenantId);
        schema.setAppId(appId);
        SchemaManager.saveSchema(schema);

        // Attacker sends HTTP POST to grant role, presenting attackerSessionToken BUT passing X-User-Id: victim_author
        String requestJson = MAPPER.writeValueAsString(Map.of(
                "entityName", entityName,
                "userId", attacker,
                "role", "both"
        ));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/tenants/" + tenantId + "/apps/" + appId + "/roles"))
                .header("Content-Type", "application/json")
                .header("X-Session-Token", attackerSessionToken) // Attacker session
                .header("X-User-Id", realAuthor) // Attempt to spoof victim author in header
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        HttpResponse<String> response = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(403, response.statusCode(), "Server must ignore spoofed X-User-Id header and reject attacker with 403 Forbidden");
        assertTrue(response.body().contains("Forbidden"));
    }

    @Test
    public void testHttpBodyTenantIdOverrideIsIgnored() throws Exception {
        String pathTenant = "tenantA";
        String bodyTenant = "tenantB";
        String appId = "app_sec";
        String entityName = "Invoice";
        String creator = "alice_creator";
        String targetUser = "target_user";
        String sessionToken = createTestSession(creator);

        // Seed App & Schema for tenantA
        AppMetadata app = new AppMetadata(appId, "Sec App", "1.0.0");
        app.setTenantId(pathTenant);
        app.setAuthor(creator);
        AppManager.createApp(pathTenant, app);

        EntitySchema.Field idField = new EntitySchema.Field("id", "integer", true, true, null);
        EntitySchema schema = new EntitySchema(entityName, List.of(idField));
        schema.setTenantId(pathTenant);
        schema.setAppId(appId);
        SchemaManager.saveSchema(schema);

        // Make real HTTP POST request to pathTenant endpoint, but pass body.tenantId = tenantB
        String requestJson = MAPPER.writeValueAsString(Map.of(
                "tenantId", bodyTenant, // malicious body tenant override attempt
                "entityName", entityName,
                "userId", targetUser,
                "role", "maker"
        ));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/tenants/" + pathTenant + "/apps/" + appId + "/roles"))
                .header("Content-Type", "application/json")
                .header("X-Session-Token", sessionToken)
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        HttpResponse<String> response = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());

        // Assert role landed in pathTenant in database, NOT bodyTenant
        assertTrue(UserRoleService.isMaker(pathTenant, appId, entityName, targetUser));
        assertFalse(UserRoleService.isMaker(bodyTenant, appId, entityName, targetUser));
    }

    @Test
    public void testHttpCheckerCannotGrantRoles() throws Exception {
        String tenantId = "t_sec";
        String appId = "app_checker";
        String entityName = "Order";
        String creator = "alice_creator";
        String checkerUser = "charlie_checker";
        String victimUser = "victim_user";
        String checkerSessionToken = createTestSession(checkerUser);

        AppMetadata app = new AppMetadata(appId, "Checker App", "1.0.0");
        app.setTenantId(tenantId);
        app.setAuthor(creator);
        AppManager.createApp(tenantId, app);

        EntitySchema.Field idField = new EntitySchema.Field("id", "integer", true, true, null);
        EntitySchema schema = new EntitySchema(entityName, List.of(idField));
        schema.setTenantId(tenantId);
        schema.setAppId(appId);
        SchemaManager.saveSchema(schema);

        // Grant CHECKER role to charlie_checker
        UserRoleService.grantRole(tenantId, appId, entityName, checkerUser, UserRoleService.Role.CHECKER, creator);

        // Charlie_checker sends HTTP request trying to grant role to victim
        String requestJson = MAPPER.writeValueAsString(Map.of(
                "entityName", entityName,
                "userId", victimUser,
                "role", "both"
        ));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/tenants/" + tenantId + "/apps/" + appId + "/roles"))
                .header("Content-Type", "application/json")
                .header("X-Session-Token", checkerSessionToken)
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();

        HttpResponse<String> response = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(403, response.statusCode(), "CHECKER user must be rejected with 403 Forbidden");
        assertTrue(response.body().contains("Forbidden"));
    }

    @Test
    public void testHttpAppAuthorEnforcedAndImmutable() throws Exception {
        String tenantId = "t_immutable";
        String appId = "app_http_immutable";
        String realAuthor = "real_author";
        String spoofedAuthor = "spoofed_author";
        String hackerAuthor = "hacker_author";
        String realAuthorSession = createTestSession(realAuthor, tenantId);

        // 1. Create app with spoofed author in JSON payload
        String createJson = MAPPER.writeValueAsString(Map.of(
                "id", appId,
                "name", "Immutable Test App",
                "version", "1.0.0",
                "author", spoofedAuthor // Spoof attempt
        ));

        HttpRequest createReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/appbana-studio/" + tenantId + "/apps"))
                .header("Content-Type", "application/json")
                .header("X-Session-Token", realAuthorSession)
                .POST(HttpRequest.BodyPublishers.ofString(createJson))
                .build();

        HttpResponse<String> createRes = client.send(createReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, createRes.statusCode());

        // Verify author in DB was forced to realAuthor (not spoofedAuthor)
        AppMetadata createdApp = AppManager.getApp(tenantId, appId);
        assertEquals(realAuthor, createdApp.getAuthor(), "Author must be enforced to authenticated user");

        // 2. PUT update attempting to change author to hackerAuthor
        String updateJson = MAPPER.writeValueAsString(Map.of(
                "name", "Updated App Name",
                "author", hackerAuthor
        ));

        HttpRequest updateReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/appbana-studio/" + tenantId + "/apps/" + appId))
                .header("Content-Type", "application/json")
                .header("X-Session-Token", realAuthorSession)
                .PUT(HttpRequest.BodyPublishers.ofString(updateJson))
                .build();

        HttpResponse<String> updateRes = client.send(updateReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, updateRes.statusCode());

        // Verify author in DB remains realAuthor
        AppMetadata updatedApp = AppManager.getApp(tenantId, appId);
        assertEquals(realAuthor, updatedApp.getAuthor(), "Author must remain immutable after update");
    }

    @Test
    public void testHttpSchemaRoutesValidationAndOwnershipGuard() throws Exception {
        String tenantId = "t_schema_sec";
        String appId = "app_schema_sec";
        String entityName = "SecureEntity";
        String appOwner = "owner_user";
        String attacker = "attacker_user";

        String ownerSession = createTestSession(appOwner);
        String attackerSession = createTestSession(attacker);

        // Create target app owned by appOwner
        AppMetadata app = new AppMetadata(appId, "Schema Sec App", "1.0.0");
        app.setTenantId(tenantId);
        app.setAuthor(appOwner);
        AppManager.createApp(tenantId, app);

        // 1. POST /schema missing tenantId/appId returns 400 Bad Request
        String invalidJson = MAPPER.writeValueAsString(Map.of(
                "name", entityName,
                "fields", List.of(Map.of("name", "id", "type", "integer", "primaryKey", true))
        ));

        HttpRequest invalidReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/schema"))
                .header("Content-Type", "application/json")
                .header("X-Session-Token", ownerSession)
                .POST(HttpRequest.BodyPublishers.ofString(invalidJson))
                .build();

        HttpResponse<String> invalidRes = client.send(invalidReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(400, invalidRes.statusCode(), "Missing tenantId/appId must return 400 Bad Request");

        // 2. POST /schema by attacker targeting appOwner's app returns 403 Forbidden
        String validJson = MAPPER.writeValueAsString(Map.of(
                "name", entityName,
                "tenantId", tenantId,
                "appId", appId,
                "fields", List.of(Map.of("name", "id", "type", "integer", "primaryKey", true))
        ));

        HttpRequest attackReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/schema"))
                .header("Content-Type", "application/json")
                .header("X-Session-Token", attackerSession)
                .POST(HttpRequest.BodyPublishers.ofString(validJson))
                .build();

        HttpResponse<String> attackRes = client.send(attackReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(403, attackRes.statusCode(), "Attacker saving schema to unowned app must get 403 Forbidden");

        // 3. POST /schema by appOwner for NEW schema succeeds (200 OK) & grants role
        HttpRequest ownerReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/schema"))
                .header("Content-Type", "application/json")
                .header("X-Session-Token", ownerSession)
                .POST(HttpRequest.BodyPublishers.ofString(validJson))
                .build();

        HttpResponse<String> ownerRes = client.send(ownerReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, ownerRes.statusCode());
        assertTrue(UserRoleService.isMaker(tenantId, appId, entityName, appOwner));

        // C1.13 Fix: Revoke role for appOwner, then issue schema update and verify role is NOT re-granted
        UserRoleService.revokeRole(tenantId, appId, entityName, appOwner);
        assertFalse(UserRoleService.isMaker(tenantId, appId, entityName, appOwner), "Role should be revoked prior to update test");

        // 4. Update existing schema (second save) does NOT re-grant role to appOwner on update
        HttpRequest updateReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/schema"))
                .header("Content-Type", "application/json")
                .header("X-Session-Token", ownerSession)
                .POST(HttpRequest.BodyPublishers.ofString(validJson))
                .build();

        HttpResponse<String> updateRes = client.send(updateReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, updateRes.statusCode());
        assertFalse(UserRoleService.isMaker(tenantId, appId, entityName, appOwner), "Schema update MUST NOT re-grant maker/checker role");
    }

    /**
     * GET /roles originally shipped with no authentication at all, while POST and DELETE on the
     * same path were both guarded. The role map reveals who may approve what, and where separation
     * of duties rests on a single checker, so an unauthenticated read is reconnaissance material
     * for anyone targeting the approval workflow.
     */
    @Test
    public void testGetRolesRequiresAuthenticationAndAuthorization() throws Exception {
        String tenantId = "t_getroles";
        String appId = "app_getroles";
        String entityName = "Payment";
        String owner = "owner_user";
        String maker = "maker_user";
        String outsider = "outsider_user";

        AppMetadata app = new AppMetadata(appId, "Get Roles App", "1.0.0");
        app.setTenantId(tenantId);
        app.setAuthor(owner);
        AppManager.createApp(tenantId, app);

        EntitySchema.Field idField = new EntitySchema.Field("id", "integer", true, true, null);
        EntitySchema schema = new EntitySchema(entityName, List.of(idField));
        schema.setTenantId(tenantId);
        schema.setAppId(appId);
        SchemaManager.saveSchema(schema);

        UserRoleService.grantRole(tenantId, appId, entityName, maker, UserRoleService.Role.MAKER, owner);

        String url = BASE_URL + "/api/tenants/" + tenantId + "/apps/" + appId
                + "/roles?entityName=" + entityName + "&userId=" + maker;

        // 1. No session at all must be rejected, not answered.
        HttpResponse<String> anonymous = client.send(
                HttpRequest.newBuilder().uri(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(401, anonymous.statusCode(),
                "GET /roles must require a session; an anonymous caller could otherwise map the entire approval topology");
        assertFalse(anonymous.body().contains("\"isChecker\""), "Unauthenticated response must not leak role data");

        // 2. An authenticated but unrelated user must not read someone else's grants.
        HttpResponse<String> byOutsider = client.send(
                HttpRequest.newBuilder().uri(URI.create(url))
                        .header("X-Session-Token", createTestSession(outsider))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(403, byOutsider.statusCode(), "Only the app owner may read another user's roles");

        // 3. The app owner may read anyone's grants — this is the role-administration path.
        HttpResponse<String> byOwner = client.send(
                HttpRequest.newBuilder().uri(URI.create(url))
                        .header("X-Session-Token", createTestSession(owner))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, byOwner.statusCode());
        assertTrue(byOwner.body().contains("\"isMaker\":true"));

        // 4. A user may always read their own grants, without being an owner.
        HttpResponse<String> bySelf = client.send(
                HttpRequest.newBuilder().uri(URI.create(url))
                        .header("X-Session-Token", createTestSession(maker))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, bySelf.statusCode(), "A user must be able to discover their own roles");
        assertTrue(bySelf.body().contains("\"isMaker\":true"));
    }

    /**
     * The authorization check runs before the schema lookup on purpose: answering 404 first would
     * let an unauthorized caller enumerate which entities exist inside an app that is not theirs.
     *
     * <p>C3.9: originally only GET was fixed. handlePostRole and handleDeleteRole, fifty lines below
     * it in the same file, had the identical ordering and the identical disclosure. All three verbs
     * are pinned here so the next handler added to this file has an obvious template.
     */
    @Test
    public void testRoleRoutesDoNotLeakEntityExistenceToUnauthorizedCallers() throws Exception {
        String tenantId = "t_enum";
        String appId = "app_enum";
        String owner = "enum_owner";
        String outsider = "enum_outsider";

        AppMetadata app = new AppMetadata(appId, "Enum App", "1.0.0");
        app.setTenantId(tenantId);
        app.setAuthor(owner);
        AppManager.createApp(tenantId, app);

        EntitySchema.Field idField = new EntitySchema.Field("id", "integer", true, true, null);
        EntitySchema schema = new EntitySchema("RealEntity", List.of(idField));
        schema.setTenantId(tenantId);
        schema.setAppId(appId);
        SchemaManager.saveSchema(schema);

        String outsiderSession = createTestSession(outsider);
        String rolesUrl = BASE_URL + "/api/tenants/" + tenantId + "/apps/" + appId + "/roles";
        String queryBase = rolesUrl + "?userId=someone_else&entityName=";

        assertIndistinguishable("GET",
                HttpRequest.newBuilder().uri(URI.create(queryBase + "RealEntity"))
                        .header("X-Session-Token", outsiderSession).GET().build(),
                HttpRequest.newBuilder().uri(URI.create(queryBase + "NoSuchEntity"))
                        .header("X-Session-Token", outsiderSession).GET().build());

        String realBody = MAPPER.writeValueAsString(Map.of(
                "entityName", "RealEntity", "userId", "someone_else", "role", "maker"));
        String missingBody = MAPPER.writeValueAsString(Map.of(
                "entityName", "NoSuchEntity", "userId", "someone_else", "role", "maker"));
        assertIndistinguishable("POST",
                HttpRequest.newBuilder().uri(URI.create(rolesUrl))
                        .header("Content-Type", "application/json")
                        .header("X-Session-Token", outsiderSession)
                        .POST(HttpRequest.BodyPublishers.ofString(realBody)).build(),
                HttpRequest.newBuilder().uri(URI.create(rolesUrl))
                        .header("Content-Type", "application/json")
                        .header("X-Session-Token", outsiderSession)
                        .POST(HttpRequest.BodyPublishers.ofString(missingBody)).build());

        assertIndistinguishable("DELETE",
                HttpRequest.newBuilder().uri(URI.create(queryBase + "RealEntity"))
                        .header("X-Session-Token", outsiderSession).DELETE().build(),
                HttpRequest.newBuilder().uri(URI.create(queryBase + "NoSuchEntity"))
                        .header("X-Session-Token", outsiderSession).DELETE().build());
    }

    private void assertIndistinguishable(String verb, HttpRequest realEntity, HttpRequest missingEntity)
            throws Exception {
        HttpResponse<String> existing = client.send(realEntity, HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> missing = client.send(missingEntity, HttpResponse.BodyHandlers.ofString());

        assertEquals(existing.statusCode(), missing.statusCode(),
                verb + " /roles: an unauthorized caller must not be able to tell an existing entity"
                        + " from a missing one by status code");
        assertEquals(403, existing.statusCode(),
                verb + " /roles must answer 403 before it answers 404");
    }
}
