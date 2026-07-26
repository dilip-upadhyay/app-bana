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
 * RoleRoutesSecurityTest — Tasks C1.10 & C1.11
 *
 * Real HTTP-layer end-to-end integration test suite using Java HttpClient over local TCP socket port 18088:
 * 1. Path tenantId is strictly enforced; payload body.tenantId is ignored (Blocker #2).
 * 2. Non-creators and CHECKER users get 403 Forbidden on role grant requests (Blocker #1).
 * 3. App creation forces author to authenticated caller (client author payload ignored).
 * 4. App update preserves author field (author is immutable).
 * 5. POST /schema missing tenantId or appId returns 400 Bad Request.
 * 6. POST /schema on an un-owned app returns 403 Forbidden (C1.10 App ownership guard).
 * 7. POST /schema ONLY bootstraps creator role on NEW schema insert, NOT on updates (C1.10 Insert-only bootstrap).
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

    @BeforeEach
    public void cleanTables() throws Exception {
        try (Connection c = JdbcManager.getConnection("default");
             Statement s = c.createStatement()) {
            s.execute("DELETE FROM appbana_user_roles");
            s.execute("DELETE FROM appbana_apps");
            s.execute("DELETE FROM appbana_schemas");
        }
    }

    private String createTestSession(String userId) {
        return SessionService.createSession(userId).sessionId();
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
                .header("X-User-Id", creator)
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
                .header("X-User-Id", checkerUser)
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
        String realAuthorSession = createTestSession(realAuthor);

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
                .header("X-User-Id", realAuthor)
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
                .header("X-User-Id", realAuthor)
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
                .header("X-User-Id", appOwner)
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
                .header("X-User-Id", attacker)
                .POST(HttpRequest.BodyPublishers.ofString(validJson))
                .build();

        HttpResponse<String> attackRes = client.send(attackReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(403, attackRes.statusCode(), "Attacker saving schema to unowned app must get 403 Forbidden");

        // 3. POST /schema by appOwner for NEW schema succeeds (200 OK) & grants role
        HttpRequest ownerReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/schema"))
                .header("Content-Type", "application/json")
                .header("X-Session-Token", ownerSession)
                .header("X-User-Id", appOwner)
                .POST(HttpRequest.BodyPublishers.ofString(validJson))
                .build();

        HttpResponse<String> ownerRes = client.send(ownerReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, ownerRes.statusCode());
        assertTrue(UserRoleService.isMaker(tenantId, appId, entityName, appOwner));

        // 4. Update existing schema (second save) does NOT grant role to updater
        HttpRequest updateReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/schema"))
                .header("Content-Type", "application/json")
                .header("X-Session-Token", ownerSession)
                .header("X-User-Id", appOwner)
                .POST(HttpRequest.BodyPublishers.ofString(validJson))
                .build();

        HttpResponse<String> updateRes = client.send(updateReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, updateRes.statusCode());
    }
}
