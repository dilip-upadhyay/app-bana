package com.appbana.server.routes;

import com.appbana.ApiServer;
import com.appbana.JdbcManager;
import com.appbana.service.SessionService;
import com.appbana.storage.LocalFilesystemAdapter;
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
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * H1 hardening — file download must enforce the (tenantId, appId, fileId)
 * triple. A file uploaded by tenant A must NOT be downloadable by supplying
 * tenant B's id in the URL, even with a valid fileId.
 *
 * The route handler in {@link FileRoutes#handleDownload} uses this exact SQL
 * (see {@code SELECT_SQL}), so testing at the SQL level catches the isolation
 * contract without needing a live HTTP server + storage adapter round-trip.
 *
 * S1.7 additions below also cover {@code POST /api/files/upload} over a real
 * HTTP round-trip (unlike the SQL-level tests above), since the guard lives
 * in the route handler itself, not in a reusable SQL string.
 */
public class FileRoutesTenantIsolationTest {

    private static final int PORT = 18083;
    private static final String BASE_URL = "http://localhost:" + PORT;
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeAll
    public static void setup() throws Exception {
        // Trigger Liquibase migrations (including V13 for appbana_files).
        ApiServer.startJdk(PORT);
    }

    /**
     * Copy of {@link FileRoutes} SELECT_SQL — kept private in production so we
     * duplicate it here rather than exposing it just for tests. If the
     * production string drifts, this test will catch it (see structural test
     * at the bottom).
     */
    private static final String SELECT_SQL =
            "SELECT original_name, mime_type, size_bytes, storage_path FROM appbana_files " +
            "WHERE file_id = ? AND tenant_id = ? AND app_id = ?";

    private static void seedFile(String fileId, String tenantId, String appId) throws Exception {
        try (Connection conn = JdbcManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO appbana_files " +
                 "(file_id, tenant_id, app_id, entity_key, field_name, original_name, mime_type, size_bytes, storage_path, uploaded_by) " +
                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, fileId);
            ps.setString(2, tenantId);
            ps.setString(3, appId);
            ps.setString(4, null);
            ps.setString(5, null);
            ps.setString(6, "secret.pdf");
            ps.setString(7, "application/pdf");
            ps.setLong(8, 42L);
            ps.setString(9, "test/" + fileId);
            ps.setString(10, "test-user");
            ps.executeUpdate();
        }
    }

    private static void deleteFile(String fileId) throws Exception {
        try (Connection conn = JdbcManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "DELETE FROM appbana_files WHERE file_id = ?")) {
            ps.setString(1, fileId);
            ps.executeUpdate();
        }
    }

    @Test
    public void ownerTenantCanReadItsOwnFile() throws Exception {
        String fileId = "H1-owner-" + System.nanoTime();
        seedFile(fileId, "tenantA", "appX");
        try (Connection conn = JdbcManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL)) {
            ps.setString(1, fileId);
            ps.setString(2, "tenantA");
            ps.setString(3, "appX");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "owner tenant + app must find the file");
                assertEquals("secret.pdf", rs.getString("original_name"));
            }
        } finally {
            deleteFile(fileId);
        }
    }

    @Test
    public void wrongTenantCannotReadFile() throws Exception {
        String fileId = "H1-cross-tenant-" + System.nanoTime();
        seedFile(fileId, "tenantA", "appX");
        try (Connection conn = JdbcManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL)) {
            ps.setString(1, fileId);
            ps.setString(2, "tenantB");   // ← attacker's tenant
            ps.setString(3, "appX");
            try (ResultSet rs = ps.executeQuery()) {
                assertFalse(rs.next(),
                        "cross-tenant read must return 0 rows — this is the P0 fix");
            }
        } finally {
            deleteFile(fileId);
        }
    }

    @Test
    public void wrongAppCannotReadFile() throws Exception {
        String fileId = "H1-cross-app-" + System.nanoTime();
        seedFile(fileId, "tenantA", "appX");
        try (Connection conn = JdbcManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL)) {
            ps.setString(1, fileId);
            ps.setString(2, "tenantA");
            ps.setString(3, "appY");       // ← different app in same tenant
            try (ResultSet rs = ps.executeQuery()) {
                assertFalse(rs.next(),
                        "cross-app read within same tenant must also return 0 rows");
            }
        } finally {
            deleteFile(fileId);
        }
    }

    @Test
    public void unknownFileIdReturnsNothing() throws Exception {
        try (Connection conn = JdbcManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_SQL)) {
            ps.setString(1, "H1-does-not-exist");
            ps.setString(2, "tenantA");
            ps.setString(3, "appX");
            try (ResultSet rs = ps.executeQuery()) {
                assertFalse(rs.next(), "unknown file id must return 0 rows");
            }
        }
    }

    /**
     * Structural guard — if someone reverts the SQL in FileRoutes back to the
     * insecure `WHERE file_id = ?`, this test fails because SELECT_SQL there
     * would no longer match ours.
     */
    @Test
    public void productionSqlStillEnforcesTenantAndApp() throws Exception {
        java.lang.reflect.Field f = FileRoutes.class.getDeclaredField("SELECT_SQL");
        f.setAccessible(true);
        String sql = (String) f.get(null);
        assertNotNull(sql);
        assertTrue(sql.contains("tenant_id = ?"),
                "production FileRoutes.SELECT_SQL must filter by tenant_id");
        assertTrue(sql.contains("app_id = ?"),
                "production FileRoutes.SELECT_SQL must filter by app_id");
        assertTrue(sql.contains("file_id = ?"),
                "production FileRoutes.SELECT_SQL must filter by file_id");
    }

    // ==========================================================================
    // S1.7 — POST /api/files/upload must require a resolved identity and reject
    // a body-supplied tenantId that doesn't match the caller's own tenant. This
    // route (like S1.8's saved views) takes its tenant identifier from the body,
    // not a path param — flagged by review round 5 for extra scrutiny. Exercised
    // over a real HTTP round-trip (unlike the SQL-level tests above) since the
    // guard lives in the route handler itself, not in a reusable SQL string.
    // ==========================================================================

    private static String createTestSession(String userId, String tenantId) {
        return SessionService.createSession(userId, tenantId).sessionId();
    }

    private static Map<String, Object> uploadBody(String tenantId, String appId) {
        return Map.of(
                "tenantId", tenantId,
                "appId", appId,
                "filename", "s1-7-test.txt",
                "mimeType", "text/plain",
                "contentBase64", Base64.getEncoder().encodeToString("hello S1.7".getBytes())
        );
    }

    @Test
    public void uploadWithoutSessionIsRejected() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/files/upload"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(uploadBody("s17-tenantA", "s17-appX"))))
                .build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, res.statusCode(), "Anonymous upload must be rejected, not silently accepted");
    }

    @Test
    public void uploadToAnotherTenantIsRejected() throws Exception {
        String attackerSession = createTestSession("s17-attacker", "s17-tenantA");
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/files/upload"))
                .header("Content-Type", "application/json")
                .header("X-Session-Token", attackerSession)
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(uploadBody("s17-tenantB", "s17-appY"))))
                .build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(403, res.statusCode(), "A tenant-A session must not upload a file tagged as tenant B");
    }

    @Test
    public void uploadToOwnTenantSucceedsAndRecordsResolvedUploader() throws Exception {
        String session = createTestSession("s17-owner", "s17-tenantA");
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/files/upload"))
                .header("Content-Type", "application/json")
                .header("X-Session-Token", session)
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(uploadBody("s17-tenantA", "s17-appX"))))
                .build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, res.statusCode(), "Uploading into the caller's own tenant must still succeed");

        @SuppressWarnings("unchecked")
        Map<String, Object> respBody = MAPPER.readValue(res.body(), Map.class);
        String fileId = (String) respBody.get("fileId");
        assertNotNull(fileId, "a successful upload must return a fileId");
        try (Connection conn = JdbcManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT tenant_id, app_id, uploaded_by, storage_path FROM appbana_files WHERE file_id = ?")) {
            ps.setString(1, fileId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "uploaded row must be persisted");
                assertEquals("s17-tenantA", rs.getString("tenant_id"));
                assertEquals("s17-appX", rs.getString("app_id"));
                assertEquals("s17-owner", rs.getString("uploaded_by"),
                        "uploaded_by must reflect the resolved session identity, not a never-set request attribute");
                new LocalFilesystemAdapter().delete(rs.getString("storage_path"));
            }
        } finally {
            deleteFile(fileId);
        }
    }
}
