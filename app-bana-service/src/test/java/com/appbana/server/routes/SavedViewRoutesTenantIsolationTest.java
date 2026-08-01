package com.appbana.server.routes;

import com.appbana.ApiServer;
import com.appbana.JdbcManager;
import com.appbana.service.SessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S1.8 hardening — {@code SavedViewRoutes} previously matched {@code ENTITY_API_PATTERN} and was
 * reachable anonymously end-to-end; {@code DELETE} scoped only by {@code view_id}, so anyone who
 * could see or guess a viewId (returned in plaintext by both create and list) could delete any
 * tenant's saved view. All 3 routes now require a resolved identity whose own tenant matches the
 * request; DELETE additionally requires the caller to be the view's own {@code owner_user_id}.
 */
public class SavedViewRoutesTenantIsolationTest {

    private static final int PORT = 18092;
    private static final String BASE_URL = "http://localhost:" + PORT;
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeAll
    public static void setup() throws Exception {
        ApiServer.startJdk(PORT);
    }

    private static String createTestSession(String userId, String tenantId) {
        return SessionService.createSession(userId, tenantId).sessionId();
    }

    private static void seedView(String viewId, String tenantId, String appId, String entityKey, String ownerUserId) throws Exception {
        try (Connection conn = JdbcManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO appbana_saved_views " +
                 "(view_id, tenant_id, app_id, entity_key, owner_user_id, name, view_json, is_default) " +
                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, viewId);
            ps.setString(2, tenantId);
            ps.setString(3, appId);
            ps.setString(4, entityKey);
            ps.setString(5, ownerUserId);
            ps.setString(6, "s18 test view");
            ps.setString(7, "{}");
            ps.setBoolean(8, false);
            ps.executeUpdate();
        }
    }

    private static boolean viewExists(String viewId) throws Exception {
        try (Connection conn = JdbcManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT 1 FROM appbana_saved_views WHERE view_id = ?")) {
            ps.setString(1, viewId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void deleteViewDirect(String viewId) throws Exception {
        try (Connection conn = JdbcManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "DELETE FROM appbana_saved_views WHERE view_id = ?")) {
            ps.setString(1, viewId);
            ps.executeUpdate();
        }
    }

    // ==========================================================================
    // GET /api/saved-views
    // ==========================================================================

    @Test
    public void listWithoutSessionIsRejected() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/saved-views?tenantId=s18-tenantA&appId=s18-appX&entityKey=Customer"))
                .GET()
                .build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, res.statusCode(), "Anonymous list must be rejected, not silently accepted");
    }

    @Test
    public void listOfAnotherTenantIsRejected() throws Exception {
        String session = createTestSession("s18-user", "s18-tenantA");
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/saved-views?tenantId=s18-tenantB&appId=s18-appY&entityKey=Customer"))
                .header("X-Session-Token", session)
                .GET()
                .build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(403, res.statusCode(), "A tenant-A session must not list tenant B's views");
    }

    @Test
    public void listOfOwnTenantSucceeds() throws Exception {
        String session = createTestSession("s18-user", "s18-tenantA");
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/saved-views?tenantId=s18-tenantA&appId=s18-appX&entityKey=Customer"))
                .header("X-Session-Token", session)
                .GET()
                .build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, res.statusCode(), "Listing the caller's own tenant/app must still succeed");
    }

    // ==========================================================================
    // POST /api/saved-views
    // ==========================================================================

    private static Map<String, Object> upsertBody(String tenantId, String appId, String ownerUserId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenantId", tenantId);
        body.put("appId", appId);
        body.put("entityKey", "Customer");
        body.put("name", "s18 test view");
        body.put("view", Map.of("filters", Map.of()));
        body.put("isDefault", false);
        if (ownerUserId != null) {
            body.put("ownerUserId", ownerUserId);
        }
        return body;
    }

    @Test
    public void upsertWithoutSessionIsRejected() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/saved-views"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(upsertBody("s18-tenantA", "s18-appX", null))))
                .build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, res.statusCode(), "Anonymous create must be rejected, not silently accepted");
    }

    @Test
    public void upsertToAnotherTenantIsRejected() throws Exception {
        String session = createTestSession("s18-attacker", "s18-tenantA");
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/saved-views"))
                .header("Content-Type", "application/json")
                .header("X-Session-Token", session)
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(upsertBody("s18-tenantB", "s18-appY", null))))
                .build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(403, res.statusCode(), "A tenant-A session must not create a view tagged as tenant B");
    }

    @Test
    public void upsertRecordsResolvedOwnerNotClientSuppliedValue() throws Exception {
        String session = createTestSession("s18-real-owner", "s18-tenantA");
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/saved-views"))
                .header("Content-Type", "application/json")
                .header("X-Session-Token", session)
                // Client tries to stamp a different user's id as the owner — must be ignored.
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(upsertBody("s18-tenantA", "s18-appX", "someone-else"))))
                .build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, res.statusCode(), "Creating a view in the caller's own tenant must still succeed");

        @SuppressWarnings("unchecked")
        Map<String, Object> respBody = MAPPER.readValue(res.body(), Map.class);
        String viewId = (String) respBody.get("viewId");
        assertNotNull(viewId, "a successful create must return a viewId");
        try (Connection conn = JdbcManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT tenant_id, app_id, owner_user_id FROM appbana_saved_views WHERE view_id = ?")) {
            ps.setString(1, viewId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "created row must be persisted");
                assertEquals("s18-tenantA", rs.getString("tenant_id"));
                assertEquals("s18-appX", rs.getString("app_id"));
                assertEquals("s18-real-owner", rs.getString("owner_user_id"),
                        "owner_user_id must reflect the resolved session identity, not the client-supplied value");
            }
        } finally {
            deleteViewDirect(viewId);
        }
    }

    // ==========================================================================
    // DELETE /api/saved-views/{viewId}
    // ==========================================================================

    @Test
    public void deleteWithoutSessionIsRejected() throws Exception {
        String viewId = "s18-del-nosession-" + System.nanoTime();
        seedView(viewId, "s18-tenantA", "s18-appX", "Customer", "s18-owner1");
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/saved-views/" + viewId))
                    .DELETE()
                    .build();
            HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            assertEquals(401, res.statusCode(), "Anonymous delete must be rejected, not silently accepted");
            assertTrue(viewExists(viewId), "a rejected delete must leave the row untouched");
        } finally {
            deleteViewDirect(viewId);
        }
    }

    @Test
    public void deleteOfAnotherTenantsViewIsRejected() throws Exception {
        String viewId = "s18-del-crosstenant-" + System.nanoTime();
        seedView(viewId, "s18-tenantA", "s18-appX", "Customer", "s18-owner1");
        try {
            // Same userId as the real owner, but a session tenant that doesn't match the row's
            // tenant — isolates the tenant check from the owner check (both must independently
            // hold, so this proves the tenant check alone is enforced).
            String attackerSession = createTestSession("s18-owner1", "s18-tenantB");
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/saved-views/" + viewId))
                    .header("X-Session-Token", attackerSession)
                    .DELETE()
                    .build();
            HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            assertEquals(403, res.statusCode(), "A tenant-B session must not delete tenant A's view");
            assertTrue(viewExists(viewId), "a rejected cross-tenant delete must leave the row untouched");
        } finally {
            deleteViewDirect(viewId);
        }
    }

    @Test
    public void deleteOfSomeoneElsesViewWithinSameTenantIsRejected() throws Exception {
        String viewId = "s18-del-crossowner-" + System.nanoTime();
        seedView(viewId, "s18-tenantA", "s18-appX", "Customer", "s18-owner1");
        try {
            // Same tenant as the view, but a DIFFERENT user — tenant match alone must not be
            // enough to delete someone else's saved view.
            String otherUserSession = createTestSession("s18-owner2", "s18-tenantA");
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/saved-views/" + viewId))
                    .header("X-Session-Token", otherUserSession)
                    .DELETE()
                    .build();
            HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            assertEquals(403, res.statusCode(), "A same-tenant user who does not own the view must not delete it");
            assertTrue(viewExists(viewId), "a rejected same-tenant-wrong-owner delete must leave the row untouched");
        } finally {
            deleteViewDirect(viewId);
        }
    }

    @Test
    public void deleteOfLegacyNullOwnerViewIsRejectedForNonAdmin() throws Exception {
        String viewId = "s18-del-nullowner-" + System.nanoTime();
        seedView(viewId, "s18-tenantA", "s18-appX", "Customer", null);
        try {
            String session = createTestSession("s18-owner1", "s18-tenantA");
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/saved-views/" + viewId))
                    .header("X-Session-Token", session)
                    .DELETE()
                    .build();
            HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            assertEquals(403, res.statusCode(), "A null owner_user_id must fail closed, not act as a wildcard match");
            assertTrue(viewExists(viewId), "a rejected null-owner delete must leave the row untouched");
        } finally {
            deleteViewDirect(viewId);
        }
    }

    @Test
    public void deleteOwnViewSucceeds() throws Exception {
        String viewId = "s18-del-owner-success-" + System.nanoTime();
        seedView(viewId, "s18-tenantA", "s18-appX", "Customer", "s18-owner1");
        String session = createTestSession("s18-owner1", "s18-tenantA");
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/saved-views/" + viewId))
                .header("X-Session-Token", session)
                .DELETE()
                .build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, res.statusCode(), "The view's own owner must be able to delete it");
        assertFalse(viewExists(viewId), "a successful delete must actually remove the row");
    }

    /**
     * Structural guard — if someone reverts {@code DELETE_SQL} back to the insecure
     * {@code WHERE view_id = ?}, this test fails because the production string would no longer
     * match ours.
     */
    @Test
    public void productionDeleteSqlStillEnforcesTenantAppAndOwner() throws Exception {
        java.lang.reflect.Field f = SavedViewRoutes.class.getDeclaredField("DELETE_SQL");
        f.setAccessible(true);
        String sql = (String) f.get(null);
        assertNotNull(sql);
        assertTrue(sql.contains("tenant_id"), "production DELETE_SQL must filter by tenant_id");
        assertTrue(sql.contains("app_id"), "production DELETE_SQL must filter by app_id");
        assertTrue(sql.contains("owner_user_id"), "production DELETE_SQL must filter by owner_user_id");
        assertTrue(sql.contains("view_id = ?"), "production DELETE_SQL must filter by view_id");
    }
}
