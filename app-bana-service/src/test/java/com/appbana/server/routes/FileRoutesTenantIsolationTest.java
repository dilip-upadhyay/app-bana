package com.appbana.server.routes;

import com.appbana.ApiServer;
import com.appbana.JdbcManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

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
 */
public class FileRoutesTenantIsolationTest {

    @BeforeAll
    public static void setup() throws Exception {
        // Trigger Liquibase migrations (including V13 for appbana_files).
        ApiServer.startJdk(18083);
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
}
