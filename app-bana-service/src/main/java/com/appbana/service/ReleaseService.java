package com.appbana.service;

import com.appbana.AppManager;
import com.appbana.JdbcManager;
import com.appbana.SchemaManager;
import com.appbana.model.AppMetadata;
import com.appbana.model.AppVersion;
import com.appbana.model.DeploymentResult;
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
     * Creates a new immutable version of the application in DEV environment.
     * This method delegates to AppPublishService for V11 schema compatibility.
     * Snapshots: Metadata, Pages, Entities, Workflows.
     *
     * @param appId       The application ID
     * @param label       Optional label (e.g. "v1.0") - NOTE: Not currently used in V11 schema
     * @param description Release notes - NOTE: Not currently used in V11 schema
     * @param userId      User creating the release
     * @return The created Version ID
     */
    public String createVersion(String tenantId, String appId, String label, String description, String userId)
            throws Exception {
        LOG.info("[Release] Creating version for app: {} (Tenant: {}) - Delegating to AppPublishService", appId, tenantId);

        // Get app metadata
        AppMetadata meta = AppManager.getApp(tenantId, appId);
        if (meta == null) {
            throw new IllegalStateException("App not found: " + appId);
        }

        // Serialize app metadata to JSON (AppPublishService expects JSON string)
        String appMetaJson = MAPPER.writeValueAsString(meta);

        // Delegate to AppPublishService which handles V11 schema (app_versions table)
        // Default to DEV environment for version creation
        // S1.11: was a bare (unclosed) Connection -- leaked one connection per call, exhausting
        // the pool under sustained load. Every sibling method in this class already scopes its
        // connection with try-with-resources; this one didn't.
        DeploymentResult result;
        try (Connection conn = JdbcManager.getConnection()) {
            AppPublishService publishService = new AppPublishService(conn, new SchemaManager());
            result = publishService.publishApp(
                appMetaJson,
                appId,
                tenantId,
                AppVersion.Environment.DEV,
                userId
            );
        }

        if (!result.isSuccess()) {
            throw new Exception("Failed to create version: " + result.getErrorMessage());
        }

        // Convert Long versionId to String for API compatibility
        String versionId = String.valueOf(result.getVersionId());
        LOG.info("[Release] Created version {} for app {} in DEV environment", versionId, appId);
        return versionId;
    }


    /**
     * Mark a specific version as deployed to the specified environment.
     * Note: New deployments are created via AppPublishService.
     * This method is kept for API compatibility but now updates the status
     * of an existing version record in app_versions.
     */
    public void deployVersion(String appId, String versionId, String userId, String targetEnv) throws SQLException {
        String env = targetEnv != null && !targetEnv.isBlank() ? targetEnv.toUpperCase() : "PROD";
        LOG.info("[Release] Marking version {} as deployed for app {} in env {}", versionId, appId, env);

        // Update the status of the version record
        String sql = "UPDATE app_versions SET status = ?::text, deployed_by = ?, deployed_at = ? WHERE id = ? AND environment = ?::text";
        
        try (Connection conn = JdbcManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "SUCCESS");
            ps.setString(2, userId);
            ps.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            ps.setLong(4, Long.parseLong(versionId));
            ps.setString(5, env);
            
            int updated = ps.executeUpdate();
            if (updated == 0) {
                LOG.warn("No version found to deploy: id={}, env={}", versionId, env);
            }
        }
    }

    /**
     * Lists all versions across all environments.
     * Returns versions grouped by version number with their environment deployments.
     */
    public List<Map<String, Object>> listVersions(String appId) throws SQLException {
        String sql = """
                    SELECT id, version, environment, status, deployed_at, deployed_by
                    FROM app_versions
                    WHERE app_id = ?
                    ORDER BY version DESC, environment
                """;

        // Group versions: version number -> list of environment deployments
        Map<Integer, Map<String, Object>> versionMap = new java.util.LinkedHashMap<>();

        try (Connection conn = JdbcManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, appId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int versionNum = rs.getInt("version");
                    String env = rs.getString("environment");
                    
                    // Get or create version entry
                    Map<String, Object> versionEntry = versionMap.computeIfAbsent(versionNum, k -> {
                        Map<String, Object> v = new HashMap<>();
                        v.put("versionNumber", versionNum);
                        v.put("activeEnvs", new ArrayList<String>());
                        v.put("deployments", new ArrayList<Map<String, Object>>());
                        return v;
                    });
                    
                    // Add environment to activeEnvs list
                    @SuppressWarnings("unchecked")
                    List<String> activeEnvs = (List<String>) versionEntry.get("activeEnvs");
                    activeEnvs.add(env);
                    
                    // Add deployment details
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> deployments = (List<Map<String, Object>>) versionEntry.get("deployments");
                    Map<String, Object> deployment = new HashMap<>();
                    deployment.put("id", rs.getLong("id"));
                    deployment.put("environment", env);
                    deployment.put("status", rs.getString("status"));
                    deployment.put("deployedAt", rs.getTimestamp("deployed_at"));
                    deployment.put("deployedBy", rs.getString("deployed_by"));
                    deployments.add(deployment);
                    
                    // Set the first deployment's metadata as version-level metadata
                    if (!versionEntry.containsKey("id")) {
                        versionEntry.put("id", rs.getLong("id"));
                        versionEntry.put("createdAt", rs.getTimestamp("deployed_at"));
                        versionEntry.put("createdBy", rs.getString("deployed_by"));
                    }
                }
            }
            return new ArrayList<>(versionMap.values());
        }
    }

    /**
     * Returns a summary of the pipeline: which version is live in which env.
     */
    public Map<String, Object> getPipelineStatus(String appId, String tenantId) throws SQLException {
        String sql = """
                    SELECT environment, version, status, deployed_at, deployed_by,
                           id as version_id, duration_ms, tables_created
                    FROM app_versions
                    WHERE app_id = ? AND tenant_id = ?
                    ORDER BY environment, version DESC
                """;

        Map<String, Object> pipeline = new HashMap<>();
        try (Connection conn = JdbcManager.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, appId);
            ps.setString(2, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                // Group by environment, taking only the latest version per environment
                while (rs.next()) {
                    String env = rs.getString("environment");
                    // Only add if this environment hasn't been added yet (we ORDER BY version DESC)
                    if (!pipeline.containsKey(env)) {
                        Map<String, Object> status = new HashMap<>();
                        status.put("versionId", rs.getLong("version_id"));
                        status.put("versionNumber", rs.getInt("version"));
                        status.put("status", rs.getString("status"));
                        status.put("deployedAt", rs.getTimestamp("deployed_at"));
                        status.put("deployedBy", rs.getString("deployed_by"));
                        status.put("durationMs", rs.getLong("duration_ms"));
                        
                        // Get tables_created as array
                        Array tablesArray = rs.getArray("tables_created");
                        if (tablesArray != null) {
                            status.put("tablesCreated", Arrays.asList((Object[]) tablesArray.getArray()));
                        }
                        
                        pipeline.put(env, status);
                    }
                }
            }
        }
        return pipeline;
    }

    /**
     * Retrieves the full application snapshot (versioned) for the given environment.
     * Returns the app_snapshot JSONB field from the latest version in that environment.
     */
    public Map<String, Object> getAppSnapshot(String appId, String env)
            throws SQLException, com.fasterxml.jackson.core.JsonProcessingException {
        // Get the latest version for this app in the specified environment
        String sql = """
                    SELECT app_snapshot, version, deployed_at, deployed_by, status
                    FROM app_versions
                    WHERE app_id = ? AND environment = ?::text
                    ORDER BY version DESC
                    LIMIT 1
                """;

        try (Connection conn = JdbcManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, appId);
            ps.setString(2, env);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String snapshotJson = rs.getString("app_snapshot");
                    
                    // Parse the JSONB snapshot
                    Map<String, Object> app = MAPPER.readValue(snapshotJson,
                            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                    
                    // Add version metadata
                    app.put("version", rs.getInt("version"));
                    app.put("environment", env);
                    app.put("deployedAt", rs.getTimestamp("deployed_at"));
                    app.put("deployedBy", rs.getString("deployed_by"));
                    app.put("status", rs.getString("status"));

                    return app;
                }
            }
        }
        return null; // Not deployed to this env
    }

    // --- Helpers ---

}
