package com.appbana.server.routes;

import com.appbana.ApiServer;
import com.appbana.JdbcManager;
import com.appbana.api.Router;
import com.appbana.middleware.SessionMiddleware;
import com.appbana.server.RouteRegistry;
import com.appbana.service.SessionService;
import com.appbana.storage.LocalFilesystemAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

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

    // ==========================================================================
    // S1.18 — GET /api/files/{tenantId}/{appId}/{fileId} must remain reachable with
    // ZERO credentials (SessionMiddleware now excludes this exact 3-segment shape),
    // restoring FileRoutes.java's own always-documented anonymous design intent —
    // while the (tenantId, appId, fileId) triple enforced by SELECT_SQL (proven at
    // the SQL layer by the tests near the top of this file) continues to gate
    // cross-tenant/unknown reads exactly as before. Exercised end-to-end over real
    // HTTP (real authenticated upload, then a real zero-header download) since
    // SessionMiddleware sits in front of the route handler entirely and the
    // SQL-level tests above cannot exercise it.
    // ==========================================================================

    private static Map<String, Object> uploadRealFile(String session, String tenantId, String appId, String content) throws Exception {
        Map<String, Object> body = Map.of(
                "tenantId", tenantId,
                "appId", appId,
                "filename", "s1-18-real.txt",
                "mimeType", "text/plain",
                "contentBase64", Base64.getEncoder().encodeToString(content.getBytes())
        );
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/files/upload"))
                .header("Content-Type", "application/json")
                .header("X-Session-Token", session)
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                .build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, res.statusCode(), "Test fixture setup: real upload must succeed: " + res.body());
        @SuppressWarnings("unchecked")
        Map<String, Object> respBody = MAPPER.readValue(res.body(), Map.class);
        return respBody;
    }

    @Test
    public void anonymousDownloadOfRealUploadedFileSucceeds() throws Exception {
        String tenantId = "s118-tenantA";
        String appId = "s118-appX";
        String session = createTestSession("s118-owner", tenantId);
        String content = "S1.18 anonymous download content";
        Map<String, Object> uploaded = uploadRealFile(session, tenantId, appId, content);
        String fileId = (String) uploaded.get("fileId");
        try {
            // No X-Session-Token, no Authorization, no headers at all beyond what
            // HttpClient sends by default — exactly what a plain
            // <a href target="_blank"> browser navigation sends.
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/files/" + tenantId + "/" + appId + "/" + fileId))
                    .GET()
                    .build();
            HttpResponse<byte[]> res = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, res.statusCode(),
                    "A real file's download URL must be reachable with zero credentials — proves the " +
                    "SessionMiddleware exclusion actually took effect, not merely that the SQL-level check allows it");
            assertEquals(content, new String(res.body()), "The real uploaded bytes must be returned");
            assertTrue(res.headers().firstValue("Content-Type").orElse("").contains("text/plain"),
                    "The original mimeType must be preserved: " + res.headers().firstValue("Content-Type"));
        } finally {
            deleteFile(fileId);
        }
    }

    @Test
    public void anonymousDownloadWithWrongTenantStill404sDespiteNoSessionRequirement() throws Exception {
        String tenantId = "s118-tenantB1";
        String appId = "s118-appY";
        String session = createTestSession("s118-owner2", tenantId);
        Map<String, Object> uploaded = uploadRealFile(session, tenantId, appId, "irrelevant");
        String fileId = (String) uploaded.get("fileId");
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/files/s118-attacker-tenant/" + appId + "/" + fileId))
                    .GET()
                    .build();
            HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            assertEquals(404, res.statusCode(),
                    "Removing the session requirement must not weaken the (tenantId, appId, fileId) triple " +
                    "check — a wrong tenant in the URL, with zero credentials, must still 404: " + res.body());
        } finally {
            deleteFile(fileId);
        }
    }

    @Test
    public void anonymousDownloadOfUnknownFileIdStill404s() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/files/s118-tenantZ/s118-appZ/s118-does-not-exist"))
                .GET()
                .build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, res.statusCode(), "An unknown fileId must still 404 with zero credentials presented: " + res.body());
    }

    @Test
    public void uploadRouteStillRequiresASessionAfterTheDownloadRouteExclusion() throws Exception {
        // Round-16 review: the previous version of this test asserted only a bare HTTP 401, which
        // the reviewer proved stays green even when SessionMiddleware.FILE_DOWNLOAD_EXCLUSION_PATTERN
        // is deliberately widened to ALSO swallow POST /api/files/upload -- because that route's 401
        // actually comes from TenantAccessGuard, not from this pattern (the upload route was already
        // excluded from SessionMiddleware entirely, via ENTITY_API_PATTERN, long before S1.18
        // existed). Testing the regex directly is the only way to prove the claim this test's name
        // makes. The upload route's own required-session behavior over a real HTTP round-trip is
        // separately (and correctly) covered by uploadWithoutSessionIsRejected above.
        Field f = SessionMiddleware.class.getDeclaredField("FILE_DOWNLOAD_EXCLUSION_PATTERN");
        f.setAccessible(true);
        Pattern pattern = (Pattern) f.get(null);

        assertTrue(pattern.matcher("/api/files/s118-tenantD/s118-appD/s118-fileD").matches(),
                "the real 3-segment download shape must match");
        assertFalse(pattern.matcher("/api/files/upload").matches(),
                "the 2-segment upload route must NOT match the download-exclusion pattern -- this is " +
                "the actual claim this test makes");
        assertFalse(pattern.matcher("/api/files/s118-tenantD/s118-appD/s118-fileD/extra").matches(),
                "a 4-segment path must not match either, confirming the boundary is exactly 3 segments");
    }

    @Test
    public void onlyGetIsRegisteredOnTheFileDownloadPathShape() throws Exception {
        // Round-16 review (finding 3): FILE_DOWNLOAD_EXCLUSION_PATTERN is verb-agnostic -- it
        // matches on path only, so it would silently exclude ANY method registered on this exact
        // 3-segment shape from session validation, not just GET (verified live by the reviewer:
        // anonymous DELETE/PUT on this shape both reach the router today, stopped only by the
        // absence of a registered handler -- not by SessionMiddleware). This is a ratchet test, not a
        // behavior test: it fails the moment a second route is registered on this same shape, forcing
        // whoever adds it to consciously re-scope the exclusion rather than silently inheriting
        // anonymous access.
        Router router = RouteRegistry.buildRouter();
        Field routesField = Router.class.getDeclaredField("routes");
        routesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Object> routes = (List<Object>) routesField.get(router);

        Class<?> routeClass = Class.forName("com.appbana.api.Router$Route");
        Field methodField = routeClass.getDeclaredField("method");
        Field partsField = routeClass.getDeclaredField("parts");
        methodField.setAccessible(true);
        partsField.setAccessible(true);

        List<String> matchingMethods = new ArrayList<>();
        for (Object route : routes) {
            @SuppressWarnings("unchecked")
            List<String> parts = (List<String>) partsField.get(route);
            // "api" / "files" / {tenantId} / {appId} / {fileId} -- exactly 5 path segments
            if (parts.size() == 5 && "api".equals(parts.get(0)) && "files".equals(parts.get(1))) {
                matchingMethods.add((String) methodField.get(route));
            }
        }

        assertEquals(List.of("GET"), matchingMethods,
                "Exactly one GET route must be registered on the 3-segment /api/files/{t}/{a}/{f} " +
                "shape. If this now fails, a new route was added on this exact path shape -- " +
                "SessionMiddleware's FILE_DOWNLOAD_EXCLUSION_PATTERN excludes ALL methods on this " +
                "shape from session validation, so the new route needs its own deliberate auth " +
                "decision, not to silently inherit anonymous access.");
    }
}
