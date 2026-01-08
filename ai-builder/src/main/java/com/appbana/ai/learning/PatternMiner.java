package com.appbana.ai.learning;

import com.appbana.ai.config.AiConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * Service for discovering and managing app patterns
 * 
 * Features:
 * - Discover patterns from created apps
 * - Store patterns with usage statistics
 * - Find best matching pattern for a request
 * - Update pattern success rates
 * - Quality gates (min occurrences, success rate)
 * 
 * Story: 2.1 - Implement Pattern Miner
 */
@Slf4j
public class PatternMiner {

    private final DataSource dataSource;
    private final AiConfig config;
    private final ObjectMapper objectMapper;

    // Quality gates
    private static final int MIN_OCCURRENCES = 5;
    private static final double MIN_SUCCESS_RATE = 0.5;

    public PatternMiner(DataSource dataSource, AiConfig config) {
        this.dataSource = dataSource;
        this.config = config;
        this.objectMapper = new ObjectMapper();

        log.info("Pattern Miner initialized");
    }

    /**
     * Discover patterns from app metadata
     * This would typically be called periodically (e.g., daily)
     * 
     * @param apps List of app metadata to analyze
     * @return Number of patterns discovered
     * @throws PatternMinerException if discovery fails
     */
    public int discoverPatterns(List<AppMetadata> apps) throws PatternMinerException {
        log.info("Discovering patterns from {} apps", apps.size());

        // Group apps by similarity
        Map<String, List<AppMetadata>> groupedApps = groupBySimilarity(apps);

        int patternsDiscovered = 0;

        for (Map.Entry<String, List<AppMetadata>> entry : groupedApps.entrySet()) {
            String patternKey = entry.getKey();
            List<AppMetadata> similarApps = entry.getValue();

            // Apply quality gate: minimum occurrences
            if (similarApps.size() < MIN_OCCURRENCES) {
                log.debug("Skipping pattern '{}' - only {} occurrences (min: {})",
                        patternKey, similarApps.size(), MIN_OCCURRENCES);
                continue;
            }

            // Extract common pattern
            AppPattern pattern = extractPattern(similarApps);

            // Calculate success rate
            double successRate = calculateSuccessRate(similarApps);

            // Apply quality gate: minimum success rate
            if (successRate < MIN_SUCCESS_RATE) {
                log.debug("Skipping pattern '{}' - success rate {} (min: {})",
                        patternKey, successRate, MIN_SUCCESS_RATE);
                continue;
            }

            pattern.setUsageCount(similarApps.size());
            pattern.setSuccessRate(successRate);

            // Store or update pattern
            storePattern(pattern);
            patternsDiscovered++;

            log.info("Discovered pattern: {} (usage: {}, success: {:.2f})",
                    pattern.getPatternName(), pattern.getUsageCount(), pattern.getSuccessRate());
        }

        log.info("Discovered {} patterns total", patternsDiscovered);
        return patternsDiscovered;
    }

    /**
     * Get the best matching pattern for a request
     * 
     * @param appType  Type of app requested
     * @param keywords Optional keywords for matching
     * @return Best matching pattern, or null if none found
     * @throws PatternMinerException if retrieval fails
     */
    public AppPattern getBestPattern(String appType, List<String> keywords)
            throws PatternMinerException {

        log.debug("Finding best pattern for type: {}, keywords: {}", appType, keywords);

        String sql = """
                SELECT id, pattern_name, app_type, entities, relationships, pages,
                       usage_count, success_rate, created_at, updated_at
                FROM ai_app_patterns
                WHERE app_type = ?
                  AND usage_count >= ?
                  AND success_rate >= ?
                ORDER BY success_rate DESC, usage_count DESC
                LIMIT 1
                """;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, appType);
            stmt.setInt(2, MIN_OCCURRENCES);
            stmt.setDouble(3, MIN_SUCCESS_RATE);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    AppPattern pattern = mapResultSetToPattern(rs);
                    log.info("Found best pattern: {} (usage: {}, success: {:.2f})",
                            pattern.getPatternName(), pattern.getUsageCount(), pattern.getSuccessRate());
                    return pattern;
                }
            }

            log.debug("No suitable pattern found for type: {}", appType);
            return null;

        } catch (SQLException e) {
            log.error("Failed to get best pattern for type: {}", appType, e);
            throw new PatternMinerException("Failed to get best pattern", e);
        }
    }

    /**
     * Update pattern usage and success rate
     * 
     * @param patternId     Pattern ID
     * @param wasSuccessful Whether the pattern usage was successful
     * @throws PatternMinerException if update fails
     */
    public void updatePatternUsage(String patternId, boolean wasSuccessful)
            throws PatternMinerException {

        String sql = """
                UPDATE ai_app_patterns
                SET usage_count = usage_count + 1,
                    success_rate = (success_rate * usage_count + ?) / (usage_count + 1),
                    updated_at = NOW()
                WHERE id = ?
                """;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setDouble(1, wasSuccessful ? 1.0 : 0.0);
            stmt.setObject(2, UUID.fromString(patternId));

            int updated = stmt.executeUpdate();
            if (updated > 0) {
                log.debug("Updated pattern {} usage (successful: {})", patternId, wasSuccessful);
            }

        } catch (SQLException e) {
            log.error("Failed to update pattern usage for {}", patternId, e);
            throw new PatternMinerException("Failed to update pattern usage", e);
        }
    }

    /**
     * Get all patterns sorted by quality
     * 
     * @param limit Maximum number of patterns to return
     * @return List of patterns
     * @throws PatternMinerException if retrieval fails
     */
    public List<AppPattern> getTopPatterns(int limit) throws PatternMinerException {
        String sql = """
                SELECT id, pattern_name, app_type, entities, relationships, pages,
                       usage_count, success_rate, created_at, updated_at
                FROM ai_app_patterns
                WHERE usage_count >= ? AND success_rate >= ?
                ORDER BY success_rate DESC, usage_count DESC
                LIMIT ?
                """;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, MIN_OCCURRENCES);
            stmt.setDouble(2, MIN_SUCCESS_RATE);
            stmt.setInt(3, limit);

            List<AppPattern> patterns = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    patterns.add(mapResultSetToPattern(rs));
                }
            }

            log.debug("Retrieved {} top patterns", patterns.size());
            return patterns;

        } catch (SQLException e) {
            log.error("Failed to get top patterns", e);
            throw new PatternMinerException("Failed to get top patterns", e);
        }
    }

    // Private helper methods

    private Map<String, List<AppMetadata>> groupBySimilarity(List<AppMetadata> apps) {
        Map<String, List<AppMetadata>> groups = new HashMap<>();

        for (AppMetadata app : apps) {
            // Create a key based on app type and entity structure
            String key = createPatternKey(app);
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(app);
        }

        return groups;
    }

    private String createPatternKey(AppMetadata app) {
        // Simple key: app_type + sorted entity names
        List<String> entityNames = app.getEntities().stream()
                .map(e -> e.get("name").toString())
                .sorted()
                .toList();

        return app.getAppType() + ":" + String.join(",", entityNames);
    }

    private AppPattern extractPattern(List<AppMetadata> apps) {
        // Take the most common structure
        AppMetadata representative = apps.get(0);

        AppPattern pattern = new AppPattern();
        pattern.setId(UUID.randomUUID().toString());
        pattern.setPatternName(generatePatternName(representative));
        pattern.setAppType(representative.getAppType());
        pattern.setEntities(representative.getEntities());
        pattern.setRelationships(representative.getRelationships());
        pattern.setPages(representative.getPages());
        pattern.setCreatedAt(Instant.now());
        pattern.setUpdatedAt(Instant.now());

        return pattern;
    }

    private String generatePatternName(AppMetadata app) {
        List<String> entityNames = app.getEntities().stream()
                .map(e -> e.get("name").toString())
                .limit(3)
                .toList();

        return app.getAppType() + " with " + String.join(", ", entityNames);
    }

    private double calculateSuccessRate(List<AppMetadata> apps) {
        long successfulApps = apps.stream()
                .filter(AppMetadata::isSuccessful)
                .count();

        return (double) successfulApps / apps.size();
    }

    private void storePattern(AppPattern pattern) throws PatternMinerException {
        String sql = """
                INSERT INTO ai_app_patterns
                (id, pattern_name, app_type, entities, relationships, pages, usage_count, success_rate, created_at, updated_at)
                VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    usage_count = EXCLUDED.usage_count,
                    success_rate = EXCLUDED.success_rate,
                    updated_at = EXCLUDED.updated_at
                """;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, UUID.fromString(pattern.getId()));
            stmt.setString(2, pattern.getPatternName());
            stmt.setString(3, pattern.getAppType());
            stmt.setString(4, objectMapper.writeValueAsString(pattern.getEntities()));
            stmt.setString(5, objectMapper.writeValueAsString(pattern.getRelationships()));
            stmt.setString(6, objectMapper.writeValueAsString(pattern.getPages()));
            stmt.setInt(7, pattern.getUsageCount());
            stmt.setDouble(8, pattern.getSuccessRate());
            stmt.setTimestamp(9, Timestamp.from(pattern.getCreatedAt()));
            stmt.setTimestamp(10, Timestamp.from(pattern.getUpdatedAt()));

            stmt.executeUpdate();

        } catch (SQLException | JsonProcessingException e) {
            log.error("Failed to store pattern: {}", pattern.getPatternName(), e);
            throw new PatternMinerException("Failed to store pattern", e);
        }
    }

    private AppPattern mapResultSetToPattern(ResultSet rs) throws SQLException {
        AppPattern pattern = new AppPattern();
        pattern.setId(rs.getObject("id", UUID.class).toString());
        pattern.setPatternName(rs.getString("pattern_name"));
        pattern.setAppType(rs.getString("app_type"));

        try {
            pattern.setEntities(objectMapper.readValue(rs.getString("entities"), List.class));
            pattern.setRelationships(objectMapper.readValue(rs.getString("relationships"), List.class));
            pattern.setPages(objectMapper.readValue(rs.getString("pages"), List.class));
        } catch (JsonProcessingException e) {
            log.error("Failed to parse JSON for pattern {}", pattern.getId(), e);
        }

        pattern.setUsageCount(rs.getInt("usage_count"));
        pattern.setSuccessRate(rs.getDouble("success_rate"));
        pattern.setCreatedAt(rs.getTimestamp("created_at").toInstant());
        pattern.setUpdatedAt(rs.getTimestamp("updated_at").toInstant());

        return pattern;
    }

    /**
     * App metadata for pattern discovery
     */
    @Data
    public static class AppMetadata {
        private String appId;
        private String appType;
        private List<Map<String, Object>> entities;
        private List<Map<String, Object>> relationships;
        private List<Map<String, Object>> pages;
        private boolean successful;
    }

    /**
     * Discovered app pattern
     */
    @Data
    public static class AppPattern {
        private String id;
        private String patternName;
        private String appType;
        private List<Map<String, Object>> entities;
        private List<Map<String, Object>> relationships;
        private List<Map<String, Object>> pages;
        private int usageCount;
        private double successRate;
        private Instant createdAt;
        private Instant updatedAt;
    }

    /**
     * Custom exception for pattern miner errors
     */
    public static class PatternMinerException extends Exception {
        public PatternMinerException(String message) {
            super(message);
        }

        public PatternMinerException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
