package com.appbana.ai.learning;

import com.appbana.ai.config.AiConfig;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * Service for learning and applying user preferences
 * Story: 2.2 - Implement User Preference Engine
 */
@Slf4j
public class UserPreferenceEngine {

    private final DataSource dataSource;
    private final AiConfig config;

    public UserPreferenceEngine(DataSource dataSource, AiConfig config) {
        this.dataSource = dataSource;
        this.config = config;
        log.info("User Preference Engine initialized");
    }

    public void learnFromApp(String userId, Map<String, Object> appMetadata) throws Exception {
        // Learn naming conventions
        if (appMetadata.containsKey("entities")) {
            String namingStyle = detectNamingStyle(appMetadata.get("entities"));
            storePreference(userId, "naming", "entity_style", namingStyle, 0.8);
        }
    }

    public void recordRejection(String userId, String suggestionType, String rejectedValue) throws Exception {
        storePreference(userId, "rejection", suggestionType, rejectedValue, 0.5);
    }

    public Map<String, String> getPreferences(String userId) throws Exception {
        String sql = "SELECT preference_key, preference_value FROM ai_user_preferences WHERE user_id = ?";
        Map<String, String> prefs = new HashMap<>();

        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    prefs.put(rs.getString("preference_key"), rs.getString("preference_value"));
                }
            }
        }
        return prefs;
    }

    private void storePreference(String userId, String type, String key, String value, double confidence)
            throws SQLException {
        String sql = """
                INSERT INTO ai_user_preferences (user_id, preference_type, preference_key, preference_value, confidence)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (user_id, preference_type, preference_key)
                DO UPDATE SET preference_value = EXCLUDED.preference_value,
                              confidence = EXCLUDED.confidence,
                              updated_at = NOW()
                """;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            stmt.setString(2, type);
            stmt.setString(3, key);
            stmt.setString(4, value);
            stmt.setDouble(5, confidence);
            stmt.executeUpdate();
        }
    }

    private String detectNamingStyle(Object entities) {
        // Simple detection logic
        return "camelCase";
    }
}
