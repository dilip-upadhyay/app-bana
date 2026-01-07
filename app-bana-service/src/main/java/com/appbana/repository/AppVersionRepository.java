package com.appbana.repository;

import com.appbana.model.AppVersion;
import com.appbana.model.AppVersion.Environment;
import com.appbana.model.AppVersion.DeploymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Repository for managing app_versions table.
 * Handles CRUD operations and version queries.
 */
public class AppVersionRepository {
    private static final Logger LOG = LoggerFactory.getLogger(AppVersionRepository.class);
    
    private final Connection connection;
    
    public AppVersionRepository(Connection connection) {
        this.connection = connection;
    }
    
    /**
     * Get the next version number for an app in a specific environment
     */
    public int getNextVersion(String appId, String tenantId, Environment environment) throws SQLException {
        String sql = "SELECT COALESCE(MAX(version), 0) + 1 AS next_version " +
                     "FROM app_versions " +
                     "WHERE app_id = ? AND tenant_id = ? AND environment = ?::text";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, appId);
            stmt.setString(2, tenantId);
            stmt.setString(3, environment.name());
            
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("next_version");
            }
            return 1;
        }
    }
    
    /**
     * Save a new app version record
     */
    public AppVersion save(AppVersion appVersion) throws SQLException {
        String sql = "INSERT INTO app_versions " +
                     "(app_id, tenant_id, version, environment, status, app_snapshot, " +
                     "tables_created, deployed_by, deployed_at, duration_ms, " +
                     "error_message, error_stack_trace, notes) " +
                     "VALUES (?, ?, ?, ?::text, ?::text, ?::jsonb, ?, ?, ?, ?, ?, ?, ?) " +
                     "RETURNING id";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, appVersion.getAppId());
            stmt.setString(2, appVersion.getTenantId());
            stmt.setInt(3, appVersion.getVersion());
            stmt.setString(4, appVersion.getEnvironment().name());
            stmt.setString(5, appVersion.getStatus().name());
            stmt.setString(6, appVersion.getAppSnapshot());
            
            // PostgreSQL array
            Array tablesArray = connection.createArrayOf("text", 
                    appVersion.getTablesCreated() != null ? 
                    appVersion.getTablesCreated().toArray() : new String[0]);
            stmt.setArray(7, tablesArray);
            
            stmt.setString(8, appVersion.getDeployedBy());
            stmt.setTimestamp(9, Timestamp.from(appVersion.getDeployedAt() != null ? 
                    appVersion.getDeployedAt() : Instant.now()));
            stmt.setObject(10, appVersion.getDurationMs());
            stmt.setString(11, appVersion.getErrorMessage());
            stmt.setString(12, appVersion.getErrorStackTrace());
            stmt.setString(13, appVersion.getNotes());
            
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                appVersion.setId(rs.getLong("id"));
            }
            
            LOG.info("[REPO] Saved app version: {} v{} for tenant {} in {}", 
                    appVersion.getAppId(), appVersion.getVersion(), 
                    appVersion.getTenantId(), appVersion.getEnvironment());
            
            return appVersion;
        }
    }
    
    /**
     * Get the latest version for an app in a specific environment
     */
    public Optional<AppVersion> getLatestVersion(String appId, String tenantId, Environment environment) throws SQLException {
        String sql = "SELECT * FROM app_versions " +
                     "WHERE app_id = ? AND tenant_id = ? AND environment = ?::text " +
                     "ORDER BY version DESC LIMIT 1";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, appId);
            stmt.setString(2, tenantId);
            stmt.setString(3, environment.name());
            
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return Optional.of(mapResultSet(rs));
            }
            return Optional.empty();
        }
    }
    
    /**
     * Get all versions for an app in a specific environment
     */
    public List<AppVersion> getVersionHistory(String appId, String tenantId, Environment environment) throws SQLException {
        String sql = "SELECT * FROM app_versions " +
                     "WHERE app_id = ? AND tenant_id = ? AND environment = ?::text " +
                     "ORDER BY version DESC";
        
        List<AppVersion> versions = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, appId);
            stmt.setString(2, tenantId);
            stmt.setString(3, environment.name());
            
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                versions.add(mapResultSet(rs));
            }
        }
        
        return versions;
    }
    
    /**
     * Get a specific version by ID
     */
    public Optional<AppVersion> getById(Long id) throws SQLException {
        String sql = "SELECT * FROM app_versions WHERE id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, id);
            
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return Optional.of(mapResultSet(rs));
            }
            return Optional.empty();
        }
    }
    
    /**
     * Map ResultSet to AppVersion object
     */
    private AppVersion mapResultSet(ResultSet rs) throws SQLException {
        // Get tables array
        Array tablesArray = rs.getArray("tables_created");
        List<String> tables = tablesArray != null ? 
                Arrays.asList((String[]) tablesArray.getArray()) : new ArrayList<>();
        
        return AppVersion.builder()
                .id(rs.getLong("id"))
                .appId(rs.getString("app_id"))
                .tenantId(rs.getString("tenant_id"))
                .version(rs.getInt("version"))
                .environment(Environment.valueOf(rs.getString("environment")))
                .status(DeploymentStatus.valueOf(rs.getString("status")))
                .appSnapshot(rs.getString("app_snapshot"))
                .tablesCreated(tables)
                .deployedBy(rs.getString("deployed_by"))
                .deployedAt(rs.getTimestamp("deployed_at").toInstant())
                .durationMs(rs.getObject("duration_ms", Long.class))
                .errorMessage(rs.getString("error_message"))
                .errorStackTrace(rs.getString("error_stack_trace"))
                .notes(rs.getString("notes"))
                .build();
    }
}
