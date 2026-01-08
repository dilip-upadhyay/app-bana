package com.appbana.ai.learning;

import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FeedbackLoop
 */
class FeedbackLoopTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement preparedStatement;

    @Mock
    private ResultSet resultSet;

    private FeedbackLoop feedbackLoop;

    @BeforeEach
    void setUp() throws SQLException {
        MockitoAnnotations.openMocks(this);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);

        feedbackLoop = new FeedbackLoop(dataSource);
    }

    @Test
    @DisplayName("Should record feedback successfully")
    void testRecordFeedback() throws Exception {
        // Given
        when(preparedStatement.executeUpdate()).thenReturn(1);

        // When/Then
        assertDoesNotThrow(() -> {
            feedbackLoop.recordFeedback("user-123", UUID.randomUUID().toString(), "thumbs_up", 5, "Great!");
        });

        verify(preparedStatement).executeUpdate();
    }

    @Test
    @DisplayName("Should get metrics for user")
    void testGetMetrics() throws Exception {
        // Given
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getInt("total_feedback")).thenReturn(10);
        when(resultSet.getDouble("avg_rating")).thenReturn(4.5);
        when(resultSet.getInt("positive_count")).thenReturn(8);
        when(resultSet.getInt("negative_count")).thenReturn(2);

        // When
        Map<String, Object> metrics = feedbackLoop.getMetrics("user-123");

        // Then
        assertNotNull(metrics);
        assertEquals(10, metrics.get("total"));
        assertEquals(4.5, metrics.get("avgRating"));
        assertEquals(8, metrics.get("positive"));
        assertEquals(2, metrics.get("negative"));
    }

    @Test
    @DisplayName("Should handle null conversation ID")
    void testRecordFeedback_NullConversationId() throws Exception {
        // Given
        when(preparedStatement.executeUpdate()).thenReturn(1);

        // When/Then
        assertDoesNotThrow(() -> {
            feedbackLoop.recordFeedback("user-123", null, "suggestion", 0, "Add feature X");
        });
    }
}
