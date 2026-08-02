package com.appbana.server.routes;

import com.appbana.ApiServer;
import com.appbana.JdbcManager;
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
 * CrossTenantSchemaAccessTest — S1.11 capstone (docs/planning/TENANT_ISOLATION_IMPLEMENTATION_TASKS.md).
 *
 * <p>Formalizes that a tenant B session must not read or delete tenant A's schemas by name.
 * {@code GET /schema/{name}}'s ownership check was already covered by
 * {@link SchemaRoutesTenantIsolationTest} (S1.15); this class adds it here too for the capstone's
 * own completeness, and — the real gap this task closes — adds the first automated coverage of
 * {@code DELETE /schema/{name}}'s ownership check, which S1.4 only ever proved via a live, manual
 * HTTP call against a running backend, never as a JUnit test.
 *
 * <p>Unauthenticated (401) on both routes here actually comes from {@code SessionMiddleware}, not
 * {@code TenantAccessGuard} — {@code /schema} is unconditionally excluded from every carve-out in
 * {@code SessionMiddleware.isExcludedPath}, so a sessionless request never reaches
 * {@code SchemaRoutes.java}'s own code at all. Confirmed by live-probing the real backend
 * (S1.11 review round 4).
 */
public class CrossTenantSchemaAccessTest {

    private static final int PORT = 18096;
    private static final String BASE_URL = "http://localhost:" + PORT;
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String VICTIM_TENANT = "t_s111_schema_victim";
    private static final String ATTACKER_TENANT = "t_s111_schema_attacker";

    @BeforeAll
    public static void startServer() throws Exception {
        ApiServer.startJdk(PORT);
    }

    @BeforeEach
    public void cleanTables() throws Exception {
        try (Connection c = JdbcManager.getConnection("default");
             Statement s = c.createStatement()) {
            s.execute("DELETE FROM appbana_apps WHERE tenant_id IN ('" + VICTIM_TENANT + "', '" + ATTACKER_TENANT + "')");
            s.execute("DELETE FROM appbana_schemas WHERE tenant_id IN ('" + VICTIM_TENANT + "', '" + ATTACKER_TENANT + "')");
        }
    }

    private String createTestSession(String userId, String tenantId) {
        return SessionService.createSession(userId, tenantId).sessionId();
    }

    /** Creates an app (owned by ownerUserId) and a 1-field schema under it. Returns the schema key. */
    private String createAppAndSchema(String tenantId, String appId, String ownerUserId, String entityName) throws Exception {
        String ownerSession = createTestSession(ownerUserId, tenantId);

        HttpRequest createApp = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/appbana-studio/" + tenantId + "/apps"))
                .header("Content-Type", "application/json")
                .header("X-Session-Token", ownerSession)
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(Map.of(
                        "id", appId, "name", "S1.11 schema fixture app", "version", "1.0.0"))))
                .build();
        HttpResponse<String> appRes = client.send(createApp, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, appRes.statusCode(), "Test fixture setup: app creation must succeed: " + appRes.body());

        HttpRequest createSchema = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/schema"))
                .header("Content-Type", "application/json")
                .header("X-Session-Token", ownerSession)
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(Map.of(
                        "name", entityName,
                        "tenantId", tenantId,
                        "appId", appId,
                        "fields", List.of(Map.of(
                                "name", "id", "type", "integer", "primaryKey", true, "autoIncrement", true))))))
                .build();
        HttpResponse<String> schemaRes = client.send(createSchema, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, schemaRes.statusCode(), "Test fixture setup: schema creation must succeed: " + schemaRes.body());

        return tenantId + "_" + appId + "_" + entityName;
    }

    private HttpResponse<String> get(String path, String sessionOrNull) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder().uri(URI.create(BASE_URL + path)).GET();
        if (sessionOrNull != null) b.header("X-Session-Token", sessionOrNull);
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> delete(String path, String sessionOrNull) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder().uri(URI.create(BASE_URL + path)).DELETE();
        if (sessionOrNull != null) b.header("X-Session-Token", sessionOrNull);
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    public void testCrossTenantSessionCannotReadSchemaByName() throws Exception {
        String victimKey = createAppAndSchema(VICTIM_TENANT, "s111-schema-app-read", "s111_read_owner", "S111ReadEntity");
        String attackerSession = createTestSession("s111_read_attacker", ATTACKER_TENANT);

        HttpResponse<String> res = get("/schema/" + victimKey, attackerSession);
        assertEquals(403, res.statusCode(),
                "A session for a different tenant must not be able to read another app's schema by name");
    }

    @Test
    public void testCrossTenantSessionCannotDeleteSchemaByName() throws Exception {
        String victimKey = createAppAndSchema(VICTIM_TENANT, "s111-schema-app-del", "s111_del_owner", "S111DelEntity");
        String attackerSession = createTestSession("s111_del_attacker", ATTACKER_TENANT);

        HttpResponse<String> denyRes = delete("/schema/" + victimKey, attackerSession);
        assertEquals(403, denyRes.statusCode(),
                "A session for a different tenant must not be able to delete another app's schema by name");

        String ownerSession = createTestSession("s111_del_owner", VICTIM_TENANT);
        HttpResponse<String> getRes = get("/schema/" + victimKey, ownerSession);
        assertEquals(200, getRes.statusCode(), "The blocked cross-tenant delete attempt must not have deleted the schema");
    }

    @Test
    public void testUnauthenticatedCannotDeleteSchemaByName() throws Exception {
        String victimKey = createAppAndSchema(VICTIM_TENANT, "s111-schema-app-anon", "s111_anon_owner", "S111AnonEntity");

        HttpResponse<String> res = delete("/schema/" + victimKey, null);
        assertEquals(401, res.statusCode(), "No session at all must 401, not delete the schema");
    }

    @Test
    public void testOwnerCanStillDeleteOwnSchema() throws Exception {
        String victimKey = createAppAndSchema(VICTIM_TENANT, "s111-schema-app-owner", "s111_owner_owner", "S111OwnerEntity");
        String ownerSession = createTestSession("s111_owner_owner", VICTIM_TENANT);

        HttpResponse<String> delRes = delete("/schema/" + victimKey + "?dropTable=true", ownerSession);
        assertEquals(200, delRes.statusCode(), "The app's own owner must still be able to delete its schema: " + delRes.body());

        HttpResponse<String> getRes = get("/schema/" + victimKey, ownerSession);
        assertEquals(404, getRes.statusCode(), "After the owner's own real delete, the schema must actually be gone");
    }
}
