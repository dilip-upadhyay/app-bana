package com.appbana.service;

import com.appbana.AppManager;
import com.appbana.JdbcManager;
import com.appbana.model.AppMetadata;
import com.appbana.workflow.model.WorkflowDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Service for managing application releases (versions and deployments).
 * Handles snapshotting of application state to the database.
 */
public class ReleaseService {
    private static final Logger LOG = LoggerFactory.getLogger(ReleaseService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Creates a new immutable version of the application.
     * Snapshots: Metadata, Pages, Entities, Workflows.
     *
     * @param appId       The application ID
     * @param label       Optional label (e.g. "v1.0")
     * @param description Release notes
     * @param userId      User creating the release
     * @return The created Version ID
     */
    public String createVersion(String tenantId, String appId, String label, String description, String userId)
            throws Exception {
        LOG.info("[Release] Creating snapshot for app: {} (Tenant: {})", appId, tenantId);

        // CRITICAL: Use a fresh connection to ensure we see all committed data
        // This prevents reading stale data from connection pooling
        Connection versionConn = null;
        try {
            versionConn = JdbcManager.getConnection();
            versionConn.setAutoCommit(false);

            // Force a read barrier - this ensures all previous commits are visible
            versionConn.commit();

            // 1. Gather Application State
            // A. Metadata & Pages & Entities (Database-based)
            Map<String, Object> fullApp = AppManager.getAppWithPages(tenantId, appId);
            if (fullApp == null) {
                throw new IllegalArgumentException("App not found: " + appId);
            }

            // Extract components for separate storage (cleaner querying/diffing later)
            Object pagesObj = fullApp.get("pages");
            Object entitiesObj = fullApp.get("entities"); // Stored as Maps in AppMetadata

            // Remove bulk objects from metadata to keep strict separation if desired,
            // but AppMetadata usually contains entities. pages are separate.
            // We will store the exact JSONs.

            String metadataJson = MAPPER.writeValueAsString(fullApp.get("app")); // The "app" key from getAppWithPages
                                                                                 // usually has the metadata
            // Wait, getAppWithPages returns a Map merging app fields AND "pages".
            // Let's verify AppManager.getAppWithPages structure.
            // It returns map of (AppMetadata fields) + "pages": [List].
            // Ref: AppManager.java

            // Let's rely on AppMetadata vs the composite map.
            AppMetadata meta = AppManager.getApp(tenantId, appId);
            String metaJson = MAPPER.writeValueAsString(meta);

            String pagesJson = MAPPER.writeValueAsString(pagesObj != null ? pagesObj : Collections.emptyList());

            // Log the number of pages being snapshotted for debugging
            int pageCount = pagesObj instanceof List ? ((List) pagesObj).size() : 0;
            LOG.info("[Release] Snapshotting {} pages for app {}", pageCount, appId);

            // B. Workflows (DB-based)
            // We need to fetch all ACTIVE workflows for this app from the DB
            List<Map<String, Object>> workflows = fetchWorkflowsForApp(appId);
            String workflowsJson = MAPPER.writeValueAsString(workflows);

            // Entities are inside meta usually, but let's strictly extract them if we want
            // a separate column
            // For now, we put them in entities_json column AND they are in metadata_json.
            // Redundancy is fine for reliability.
            String entitiesJson = MAPPER
                    .writeValueAsString(meta.getEntities() != null ? meta.getEntities() : Collections.emptyList());

            // 2. Calculate Next Version Number
            int nextVersion = getNextVersionNumber(appId);
            String versionId = UUID.randomUUID().toString();

            // 3. Persist Snapshot
            String sql = """
                        INSERT INTO app_version
                        (id, app_id, version_number, label, description,
                         metadata_json, pages_json, entities_json, workflows_json,
                         created_by, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;

            try (PreparedStatement ps = versionConn.prepareStatement(sql)) {

                ps.setString(1, versionId);
                ps.setString(2, appId);
                ps.setInt(3, nextVersion);
                ps.setString(4, label);
                ps.setString(5, description);
                ps.setString(6, metaJson);
                ps.setString(7, pagesJson);
                ps.setString(8, entitiesJson);
                ps.setString(9, workflowsJson);
                ps.setString(10, userId);
                ps.setTimestamp(11, Timestamp.valueOf(LocalDateTime.now()));

                ps.executeUpdate();
                versionConn.commit();
            }

            LOG.info("[Release] Created version {} (v{}) for app {} with {} pages",
                    versionId, nextVersion, appId, pageCount);
            return versionId;

        } finally {
            if (versionConn != null) {
                try {
                    versionConn.close();
                } catch (SQLException e) {
                    LOG.warn("Failed to close version connection", e);
                }
            }
        }
    }

    /**
     * deploys a specific version to the specified environment.
     * This updates the pointer in app_deployment.
     */
    public void deployVersion(String appId, String versionId, String userId, String targetEnv) throws SQLException {
        String env = targetEnv != null && !targetEnv.isBlank() ? targetEnv.toUpperCase() : "PROD";
        LOG.info("[Release] Deploying version {} for app {} to env {}", versionId, appId, env);

        // Upsert logic (H2 supports MERGE, but standard SQL uses manual check or ON
        // DUPLICATE)
        String checkSql = "SELECT app_id FROM app_deployment WHERE app_id = ? AND environment = ?";
        String updateSql = "UPDATE app_deployment SET live_version_id = ?, deployed_at = ?, deployed_by = ? WHERE app_id = ? AND environment = ?";
        String insertSql = "INSERT INTO app_deployment (app_id, live_version_id, deployed_at, deployed_by, environment) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = JdbcManager.getConnection()) {
            boolean exists = false;
            try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setString(1, appId);
                ps.setString(2, env);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next())
                        exists = true;
                }
            }

            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            if (exists) {
                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setString(1, versionId);
                    ps.setTimestamp(2, now);
                    ps.setString(3, userId);
                    ps.setString(4, appId);
                    ps.setString(5, env);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    ps.setString(1, appId);
                    ps.setString(2, versionId);
                    ps.setTimestamp(3, now);
                    ps.setString(4, userId);
                    ps.setString(5, env);
                    ps.executeUpdate();
                }
            }
        }
    }

    /**
     * Lists all versions.
     * Note: "isLive" logic is tricky with multiple envs.
     * We will remove simple "isLive" bool and replace with "deployments" list or
     * "envTags".
     */
    public List<Map<String, Object>> listVersions(String appId) throws SQLException {
        // First get all versions
        String sql = """
                    SELECT v.id, v.version_number, v.label, v.description, v.created_at, v.created_by
                    FROM app_version v
                    WHERE v.app_id = ?
                    ORDER BY v.version_number DESC
                """;

        // Then get all active deployments
        String depSql = "SELECT live_version_id, environment FROM app_deployment WHERE app_id = ?";
        Map<String, List<String>> activeEnvs = new HashMap<>(); // versionId -> [DEV, PROD]

        try (Connection conn = JdbcManager.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(depSql)) {
                ps.setString(1, appId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String vid = rs.getString("live_version_id");
                        String env = rs.getString("environment");
                        activeEnvs.computeIfAbsent(vid, k -> new ArrayList<>()).add(env);
                    }
                }
            }

            List<Map<String, Object>> versions = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, appId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> v = new HashMap<>();
                        String vid = rs.getString("id");
                        v.put("id", vid);
                        v.put("versionNumber", rs.getInt("version_number"));
                        v.put("label", rs.getString("label"));
                        v.put("description", rs.getString("description"));
                        v.put("createdAt", rs.getTimestamp("created_at"));
                        v.put("createdBy", rs.getString("created_by"));

                        // Active environments for this version
                        v.put("activeEnvs", activeEnvs.getOrDefault(vid, Collections.emptyList()));

                        versions.add(v);
                    }
                }
            }
            return versions;
        }
    }

    /**
     * Returns a summary of the pipeline: which version is live in which env.
     */
    public Map<String, Object> getPipelineStatus(String appId) throws SQLException {
        String sql = """
                    SELECT d.environment, d.deployed_at, d.deployed_by,
                           v.id as version_id, v.version_number, v.label
                    FROM app_deployment d
                    JOIN app_version v ON d.live_version_id = v.id
                    WHERE d.app_id = ?
                """;

        Map<String, Object> pipeline = new HashMap<>();
        try (Connection conn = JdbcManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, appId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> status = new HashMap<>();
                    status.put("versionId", rs.getString("version_id"));
                    status.put("versionNumber", rs.getInt("version_number"));
                    status.put("label", rs.getString("label"));
                    status.put("deployedAt", rs.getTimestamp("deployed_at"));
                    status.put("deployedBy", rs.getString("deployed_by"));

                    pipeline.put(rs.getString("environment"), status);
                }
            }
        }
        return pipeline;
    }

    /**
     * Retrieves the full application snapshot (versioned) for the given
     * environment.
     * Reconstructs the app object from stored JSON blobs.
     */
    public Map<String, Object> getAppSnapshot(String appId, String env)
            throws SQLException, com.fasterxml.jackson.core.JsonProcessingException {
        // Find which version is live in this environment
        String sql = """
                    SELECT v.metadata_json, v.pages_json, v.entities_json, v.workflows_json, v.version_number
                    FROM app_deployment d
                    JOIN app_version v ON d.live_version_id = v.id
                    WHERE d.app_id = ? AND d.environment = ?
                """;

        try (Connection conn = JdbcManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, appId);
            ps.setString(2, env);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String metaJson = rs.getString("metadata_json");
                    String pagesJson = rs.getString("pages_json");

                    // Reconstruct the full app object expected by the frontend
                    // structure: { ...AppMetadata, pages: [...] }
                    Map<String, Object> app = MAPPER.readValue(metaJson,
                            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                            });
                    List<Object> pages = MAPPER.readValue(pagesJson,
                            new com.fasterxml.jackson.core.type.TypeReference<List<Object>>() {
                            });

                    app.put("pages", pages);
                    app.put("version", rs.getInt("version_number"));
                    app.put("environment", env);

                    return app;
                }
            }
        }
        return null; // Not deployed to this env
    }

    // --- Helpers ---

    private int getNextVersionNumber(String appId) throws SQLException {
        String sql = "SELECT MAX(version_number) FROM app_version WHERE app_id = ?";
        try (Connection conn = JdbcManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, appId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) + 1;
                }
            }
        }
        return 1;
    }

    private List<Map<String, Object>> fetchWorkflowsForApp(String appId) throws SQLException {
        String sql = "SELECT * FROM appbana_wf_definition WHERE app_id = ?";
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = JdbcManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, appId);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    for (int i = 1; i <= colCount; i++) {
                        row.put(meta.getColumnLabel(i), rs.getObject(i));
                    }
                    list.add(row);
                }
            }
        }
        return list;
    }
}
