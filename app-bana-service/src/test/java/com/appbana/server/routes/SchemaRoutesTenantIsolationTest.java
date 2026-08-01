package com.appbana.server.routes;

import com.appbana.ApiServer;
import com.appbana.JdbcManager;
import com.appbana.config.AppConfig;
import com.appbana.config.ConfigManager;
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
 * SchemaRoutesTenantIsolationTest — S1.15 (H1, docs/planning/TENANT_ISOLATION_SECURITY_PLAN.md,
 * S1 review round 1) and S1.16 (round 2).
 *
 * <p>{@code GET /schema} previously returned every tenant's schema names with no filtering, and
 * {@code GET /schema/{name}} had no ownership check at all — both were guarded only by the same
 * {@code authEnabled(cfg)}-conditional admin gate {@link SchemaRoutesAdminTokenTest} covers, which
 * is skipped entirely under the shipped config (adminToken/readToken both null), leaving both
 * routes fully public and cross-tenant by default.
 *
 * <p>Fixed: {@code GET /schema} now requires either a valid admin/service token (sees every
 * tenant, break-glass — mirrors {@link com.appbana.security.TenantAccessGuard}) or a real session
 * (filtered to that session's own tenant only, via {@code SchemaManager.listSchemaNames(String)}).
 * {@code GET /schema/{name}} now requires a resolved identity and
 * {@link com.appbana.security.AppAuthorization#isAppOwnerOrSystem} — mirroring the ownership check
 * S1.4 already added to {@code DELETE /schema/{name}}.
 */
public class SchemaRoutesTenantIsolationTest {

    private static final int PORT = 18094;
    private static final String BASE_URL = "http://localhost:" + PORT;
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String TEST_TENANT_IDS_SQL = "'t_s115_victim', 't_s115_attacker'";

    @BeforeAll
    public static void startServer() throws Exception {
        ApiServer.startJdk(PORT);
    }

    // Scoped to this test class's own fixture tenants only — see RoleRoutesSecurityTest for why
    // a blanket DELETE with no WHERE clause must never be used against the shared dev Postgres.
    @BeforeEach
    public void cleanTables() throws Exception {
        try (Connection c = JdbcManager.getConnection("default");
             Statement s = c.createStatement()) {
            s.execute("DELETE FROM appbana_apps WHERE tenant_id IN (" + TEST_TENANT_IDS_SQL + ")");
            s.execute("DELETE FROM appbana_schemas WHERE tenant_id IN (" + TEST_TENANT_IDS_SQL + ")");
        }
    }

    private String createTestSession(String userId, String tenantId) {
        return SessionService.createSession(userId, tenantId).sessionId();
    }

    /** Creates an app (owned by ownerUserId) and a 1-field schema under it. Returns the schema key. */
    private String createAppAndSchema(String tenantId, String appId, String ownerUserId, String entityName) throws Exception {
        String ownerSession = createTestSession(ownerUserId, tenantId);

        String appJson = MAPPER.writeValueAsString(Map.of(
                "id", appId,
                "name", "S1.15 fixture app",
                "version", "1.0.0"
        ));
        HttpRequest createApp = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/appbana-studio/" + tenantId + "/apps"))
                .header("Content-Type", "application/json")
                .header("X-Session-Token", ownerSession)
                .POST(HttpRequest.BodyPublishers.ofString(appJson))
                .build();
        HttpResponse<String> appRes = client.send(createApp, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, appRes.statusCode(), "Test fixture setup: app creation must succeed");

        String schemaJson = MAPPER.writeValueAsString(Map.of(
                "name", entityName,
                "tenantId", tenantId,
                "appId", appId,
                "fields", List.of(Map.of(
                        "name", "id",
                        "type", "integer",
                        "primaryKey", true,
                        "autoIncrement", true
                ))
        ));
        HttpRequest createSchema = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/schema"))
                .header("Content-Type", "application/json")
                .header("X-Session-Token", ownerSession)
                .POST(HttpRequest.BodyPublishers.ofString(schemaJson))
                .build();
        HttpResponse<String> schemaRes = client.send(createSchema, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, schemaRes.statusCode(), "Test fixture setup: schema creation must succeed: " + schemaRes.body());

        return tenantId + "_" + appId + "_" + entityName;
    }

    // ========================================
    // S1.15 — GET /schema must be tenant-filtered
    // ========================================

    @Test
    public void testGetSchemaExcludesOtherTenantsKeys() throws Exception {
        String victimTenant = "t_s115_victim";
        String attackerTenant = "t_s115_attacker";
        String victimKey = createAppAndSchema(victimTenant, "s115-app-victim", "s115_victim_owner", "VictimEntity");
        createAppAndSchema(attackerTenant, "s115-app-attacker", "s115_attacker_owner", "AttackerEntity");

        String attackerSession = createTestSession("s115_attacker_owner", attackerTenant);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/schema"))
                .header("X-Session-Token", attackerSession)
                .GET()
                .build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, res.statusCode());

        List<String> names = MAPPER.readValue(res.body(), MAPPER.getTypeFactory().constructCollectionType(List.class, String.class));
        assertFalse(names.contains(victimKey), "A tenant B session must not see tenant A's schema key in GET /schema");
    }

    @Test
    public void testGetSchemaIncludesCallersOwnTenantKey() throws Exception {
        String victimTenant = "t_s115_victim";
        String victimKey = createAppAndSchema(victimTenant, "s115-app-own", "s115_own_owner", "OwnEntity");

        String session = createTestSession("s115_own_owner", victimTenant);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/schema"))
                .header("X-Session-Token", session)
                .GET()
                .build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, res.statusCode());

        List<String> names = MAPPER.readValue(res.body(), MAPPER.getTypeFactory().constructCollectionType(List.class, String.class));
        assertTrue(names.contains(victimKey), "The caller's own tenant's schema key must still appear in GET /schema");
    }

    @Test
    public void testGetSchemaUnauthenticatedIsRejected() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/schema"))
                .GET()
                .build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, res.statusCode(), "No session and no admin token at all must 401, not return any list");
    }

    @Test
    public void testGetSchemaAdminTokenSeesBothTenants() throws Exception {
        AppConfig cfg = ConfigManager.getConfig();
        String originalAdmin = cfg.getAdminToken();
        cfg.setAdminToken("s115-admin-xyz");
        try {
            String victimTenant = "t_s115_victim";
            String attackerTenant = "t_s115_attacker";
            String victimKey = createAppAndSchema(victimTenant, "s115-app-admin-v", "s115_admin_owner_v", "AdminVictimEntity");
            String attackerKey = createAppAndSchema(attackerTenant, "s115-app-admin-a", "s115_admin_owner_a", "AdminAttackerEntity");

            // A session is still required by SessionMiddleware for /schema; it need not belong to
            // either fixture tenant since the admin/service token is what grants the cross-tenant view.
            String unrelatedSession = createTestSession("s115_admin_caller", "t_s115_unrelated");
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/schema"))
                    .header("X-Session-Token", unrelatedSession)
                    .header("X-AppBana-Token", "s115-admin-xyz")
                    .GET()
                    .build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, res.statusCode());

            List<String> names = MAPPER.readValue(res.body(), MAPPER.getTypeFactory().constructCollectionType(List.class, String.class));
            assertTrue(names.contains(victimKey), "A valid admin token must see every tenant's schema keys, including tenant A's");
            assertTrue(names.contains(attackerKey), "A valid admin token must see every tenant's schema keys, including tenant B's");
        } finally {
            cfg.setAdminToken(originalAdmin);
        }
    }

    // ========================================
    // S1.15 — GET /schema/{name} must enforce app ownership
    // ========================================

    @Test
    public void testGetSchemaByNameDeniesNonOwningTenant() throws Exception {
        String victimTenant = "t_s115_victim";
        String attackerTenant = "t_s115_attacker";
        String victimKey = createAppAndSchema(victimTenant, "s115-app-read-victim", "s115_read_owner_v", "ReadVictimEntity");

        String attackerSession = createTestSession("s115_read_attacker", attackerTenant);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/schema/" + victimKey))
                .header("X-Session-Token", attackerSession)
                .GET()
                .build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(403, res.statusCode(),
                "A session for a different tenant/non-owner must not be able to read another app's schema by name");
    }

    @Test
    public void testGetSchemaByNameAllowsOwner() throws Exception {
        String victimTenant = "t_s115_victim";
        String victimKey = createAppAndSchema(victimTenant, "s115-app-read-owner", "s115_read_owner_o", "ReadOwnerEntity");

        String ownerSession = createTestSession("s115_read_owner_o", victimTenant);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/schema/" + victimKey))
                .header("X-Session-Token", ownerSession)
                .GET()
                .build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, res.statusCode(), "The app's own owner must still be able to read its schema by name");

        JsonNode body = MAPPER.readTree(res.body());
        assertEquals("ReadOwnerEntity", body.get("name").asText());
    }

    @Test
    public void testGetSchemaByNameUnauthenticatedIsRejected() throws Exception {
        String victimTenant = "t_s115_victim";
        String victimKey = createAppAndSchema(victimTenant, "s115-app-read-anon", "s115_read_owner_anon", "ReadAnonEntity");

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/schema/" + victimKey))
                .GET()
                .build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, res.statusCode(), "No session at all must 401, not return the schema");
    }
}
