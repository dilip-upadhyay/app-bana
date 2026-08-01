package com.appbana.server.routes;

import com.appbana.ApiServer;
import com.appbana.AppManager;
import com.appbana.JdbcManager;
import com.appbana.config.AppConfig;
import com.appbana.config.ConfigManager;
import com.appbana.service.AuthService;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AppRoutesTenantIsolationTest — review round 1 findings B1 and B2
 * (docs/planning/TENANT_ISOLATION_SECURITY_PLAN.md, S1 review).
 *
 * <p>B1: {@code POST /appbana-studio/{tenantId}/apps} had no {@link com.appbana.security.TenantAccessGuard}
 * check at all — any authenticated session could create an app inside another tenant's path. The
 * S0.2 census had marked this route's "T/A check?" cell "N/A (creation)", which hid the gap: there
 * is no existing app to own, but there is very much a target tenant (path) to check the caller
 * against.
 *
 * <p>B2: the S1.6 admin-gate on {@code POST/PUT/DELETE /api/templates} was wrapped in
 * {@code if (AuthService.authEnabled(cfg))}, which evaluates false under the shipped config
 * (adminToken/readToken both null) — so the gate never ran under the config the product actually
 * ships. These tests deliberately do NOT set an admin token anywhere, so they exercise the real
 * shipped-config behavior via the live {@link ConfigManager} singleton, rather than a
 * locally-configured one — reproducing the exact gap that let B2 pass its original ad-hoc live
 * verification. If {@code config.json}'s {@code adminToken}/{@code readToken} ever stop being
 * null by default, {@link #testTemplatesGateIsUnconditionalUnderShippedConfig()} will fail loudly
 * rather than the other tests silently testing the wrong thing.
 */
public class AppRoutesTenantIsolationTest {

    private static final int PORT = 18089;
    private static final String BASE_URL = "http://localhost:" + PORT;
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String TEST_TENANT_IDS_SQL = "'t_b1_victim', 't_b1_attacker'";

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
        }
    }

    private String createTestSession(String userId, String tenantId) {
        return SessionService.createSession(userId, tenantId).sessionId();
    }

    // ========================================
    // B1 — POST /appbana-studio/{tenantId}/apps must enforce tenant ownership
    // ========================================

    @Test
    public void testCrossTenantAppCreationIsRejected() throws Exception {
        String victimTenant = "t_b1_victim";
        String attackerTenant = "t_b1_attacker";
        String attackerSession = createTestSession("attacker", attackerTenant);

        String createJson = MAPPER.writeValueAsString(Map.of(
                "id", "b1-planted-app",
                "name", "Planted App",
                "version", "1.0.0"
        ));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/appbana-studio/" + victimTenant + "/apps"))
                .header("Content-Type", "application/json")
                .header("X-Session-Token", attackerSession)
                .POST(HttpRequest.BodyPublishers.ofString(createJson))
                .build();

        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(403, res.statusCode(),
                "A session for one tenant must not be able to create an app under another tenant's path");
        assertNull(AppManager.getApp(victimTenant, "b1-planted-app"), "No app row may land in the victim tenant");
    }

    @Test
    public void testSameTenantAppCreationStillWorks() throws Exception {
        String tenantId = "t_b1_victim";
        String session = createTestSession("legit_owner", tenantId);

        String createJson = MAPPER.writeValueAsString(Map.of(
                "id", "b1-legit-app",
                "name", "Legit App",
                "version", "1.0.0"
        ));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/appbana-studio/" + tenantId + "/apps"))
                .header("Content-Type", "application/json")
                .header("X-Session-Token", session)
                .POST(HttpRequest.BodyPublishers.ofString(createJson))
                .build();

        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, res.statusCode(), "Creating an app in the caller's own tenant must still succeed");
        assertNotNull(AppManager.getApp(tenantId, "b1-legit-app"));
    }

    @Test
    public void testUnauthenticatedAppCreationIsRejected() throws Exception {
        String tenantId = "t_b1_victim";

        String createJson = MAPPER.writeValueAsString(Map.of(
                "id", "b1-anon-app",
                "name", "Anon App",
                "version", "1.0.0"
        ));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/appbana-studio/" + tenantId + "/apps"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(createJson))
                .build();

        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, res.statusCode(), "No session at all must 401, not silently create the app");
        assertNull(AppManager.getApp(tenantId, "b1-anon-app"));
    }

    // ========================================
    // B2 — /api/templates writes must be admin-gated under the SHIPPED config
    // (adminToken=null, readToken=null) — no per-test override of config.json.
    // ========================================

    @Test
    public void testTemplatesGateIsUnconditionalUnderShippedConfig() {
        AppConfig cfg = ConfigManager.getConfig();
        assertFalse(AuthService.authEnabled(cfg),
                "This test suite assumes the shipped config.json has adminToken=null and readToken=null "
                        + "(auth disabled by default); if this assertion fails, that fixture assumption "
                        + "changed and the tests below need to be re-evaluated against the new default");
    }

    @Test
    public void testCreateTemplateWithoutAdminTokenIsRejected() throws Exception {
        String body = MAPPER.writeValueAsString(Map.of("name", "b2-template"));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/templates"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, res.statusCode(), "POST /api/templates must reject writes when no admin token is configured");
    }

    @Test
    public void testUpdateTemplateWithoutAdminTokenIsRejected() throws Exception {
        String body = MAPPER.writeValueAsString(Map.of("name", "b2-template-updated"));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/templates/nonexistent-id"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, res.statusCode(), "PUT /api/templates/{id} must reject writes when no admin token is configured");
    }

    @Test
    public void testDeleteTemplateWithoutAdminTokenIsRejected() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/templates/nonexistent-id"))
                .DELETE()
                .build();

        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, res.statusCode(), "DELETE /api/templates/{id} must reject writes when no admin token is configured");
    }

    @Test
    public void testGetTemplatesRemainsPublic() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/templates"))
                .GET()
                .build();

        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, res.statusCode(), "GET /api/templates must remain public per the plan's adopted default");
    }
}
