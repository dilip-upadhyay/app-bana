package com.appbana.ai.learning;

import lombok.extern.slf4j.Slf4j;
import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/**
 * Service for managing feedback loop
 * Story: 2.3 - Implement Feedback Loop
 */
@Slf4j
public class FeedbackLoop {

    private final DataSource dataSource;

    public FeedbackLoop(DataSource dataSource) {
        this.dataSource = dataSource;
        log.info("Feedback Loop initialized");
    }

    public void recordFeedback(String userId, String conversationId, String feedbackType,
            int rating, String comment) throws SQLException {
        String sql = """
                INSERT INTO ai_feedback (user_id, conversation_id, feedback_type, rating, comment)
                VALUES (?, ?, ?, ?, ?)
                """;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            stmt.setObject(2, conversationId != null ? UUID.fromString(conversationId) : null);
            stmt.setString(3, feedbackType);
            stmt.setInt(4, rating);
            stmt.setString(5, comment);
            stmt.executeUpdate();
            log.debug("Recorded {} feedback from user {}", feedbackType, userId);
        }
    }

    public Map<String, Object> getMetrics(String userId) throws SQLException {
        String sql = """
                SELECT
                    COUNT(*) as total_feedback,
                    AVG(CASE WHEN rating > 0 THEN rating ELSE NULL END) as avg_rating,
                    SUM(CASE WHEN feedback_type = 'thumbs_up' THEN 1 ELSE 0 END) as positive_count,
                    SUM(CASE WHEN feedback_type = 'thumbs_down' THEN 1 ELSE 0 END) as negative_count
                FROM ai_feedback
                WHERE user_id = ?
                """;

        Map<String, Object> metrics = new HashMap<>();
        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    metrics.put("total", rs.getInt("total_feedback"));
                    metrics.put("avgRating", rs.getDouble("avg_rating"));
                    metrics.put("positive", rs.getInt("positive_count"));
                    metrics.put("negative", rs.getInt("negative_count"));
                }
            }
        }
        return metrics;
    }
}
