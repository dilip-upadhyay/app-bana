package com.appbana.ai.learning;

import com.appbana.ai.config.AiConfig;
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
 * Unit tests for PatternMiner
 */
class PatternMinerTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement preparedStatement;

    @Mock
    private ResultSet resultSet;

    private PatternMiner patternMiner;
    private AiConfig config;

    @BeforeEach
    void setUp() throws SQLException {
        MockitoAnnotations.openMocks(this);
        config = new AiConfig();

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);

        patternMiner = new PatternMiner(dataSource, config);
    }

    @Test
    @DisplayName("Should initialize successfully")
    void testInitialization() {
        assertNotNull(patternMiner);
    }

    @Test
    @DisplayName("Should discover patterns from apps")
    void testDiscoverPatterns() throws Exception {
        // Given
        List<PatternMiner.AppMetadata> apps = createTestApps(10);

        when(preparedStatement.executeUpdate()).thenReturn(1);

        // When
        int discovered = patternMiner.discoverPatterns(apps);

        // Then
        assertTrue(discovered >= 0);
    }

    @Test
    @DisplayName("Should skip patterns below minimum occurrences")
    void testQualityGate_MinOccurrences() throws Exception {
        // Given - only 3 apps (below min of 5)
        List<PatternMiner.AppMetadata> apps = createTestApps(3);

        // When
        int discovered = patternMiner.discoverPatterns(apps);

        // Then
        assertEquals(0, discovered, "Should not discover patterns with < 5 occurrences");
    }

    @Test
    @DisplayName("Should get best pattern for app type")
    void testGetBestPattern() throws Exception {
        // Given
        when(resultSet.next()).thenReturn(true).thenReturn(false);
        when(resultSet.getObject("id", UUID.class)).thenReturn(UUID.randomUUID());
        when(resultSet.getString("pattern_name")).thenReturn("CRM with Contacts");
        when(resultSet.getString("app_type")).thenReturn("CRM");
        when(resultSet.getString("entities")).thenReturn("[]");
        when(resultSet.getString("relationships")).thenReturn("[]");
        when(resultSet.getString("pages")).thenReturn("[]");
        when(resultSet.getInt("usage_count")).thenReturn(10);
        when(resultSet.getDouble("success_rate")).thenReturn(0.8);
        when(resultSet.getTimestamp(anyString())).thenReturn(new Timestamp(System.currentTimeMillis()));

        // When
        PatternMiner.AppPattern pattern = patternMiner.getBestPattern("CRM", null);

        // Then
        assertNotNull(pattern);
        assertEquals("CRM", pattern.getAppType());
    }

    @Test
    @DisplayName("Should return null when no pattern found")
    void testGetBestPattern_NotFound() throws Exception {
        // Given
        when(resultSet.next()).thenReturn(false);

        // When
        PatternMiner.AppPattern pattern = patternMiner.getBestPattern("NonExistent", null);

        // Then
        assertNull(pattern);
    }

    @Test
    @DisplayName("Should update pattern usage")
    void testUpdatePatternUsage() throws Exception {
        // Given
        String patternId = UUID.randomUUID().toString();
        when(preparedStatement.executeUpdate()).thenReturn(1);

        // When/Then - should not throw
        assertDoesNotThrow(() -> {
            patternMiner.updatePatternUsage(patternId, true);
        });

        verify(preparedStatement).executeUpdate();
    }

    @Test
    @DisplayName("Should get top patterns")
    void testGetTopPatterns() throws Exception {
        // Given
        when(resultSet.next()).thenReturn(true).thenReturn(true).thenReturn(false);
        when(resultSet.getObject("id", UUID.class)).thenReturn(UUID.randomUUID());
        when(resultSet.getString(anyString())).thenReturn("test");
        when(resultSet.getInt(anyString())).thenReturn(10);
        when(resultSet.getDouble(anyString())).thenReturn(0.8);
        when(resultSet.getTimestamp(anyString())).thenReturn(new Timestamp(System.currentTimeMillis()));

        // When
        List<PatternMiner.AppPattern> patterns = patternMiner.getTopPatterns(10);

        // Then
        assertNotNull(patterns);
        assertEquals(2, patterns.size());
    }

    // Helper methods

    private List<PatternMiner.AppMetadata> createTestApps(int count) {
        List<PatternMiner.AppMetadata> apps = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            PatternMiner.AppMetadata app = new PatternMiner.AppMetadata();
            app.setAppId("app-" + i);
            app.setAppType("CRM");

            List<Map<String, Object>> entities = new ArrayList<>();
            Map<String, Object> entity = new HashMap<>();
            entity.put("name", "Contact");
            entities.add(entity);

            app.setEntities(entities);
            app.setRelationships(new ArrayList<>());
            app.setPages(new ArrayList<>());
            app.setSuccessful(i % 2 == 0); // 50% success rate

            apps.add(app);
        }

        return apps;
    }
}
