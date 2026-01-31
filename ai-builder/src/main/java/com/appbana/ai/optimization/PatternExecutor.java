package com.appbana.ai.optimization;

import com.appbana.ai.learning.UserPreferenceEngine;
import com.appbana.ai.rag.QdrantService;
import com.appbana.ai.rag.EmbeddingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Service for executing common patterns using learned templates
 * Cost optimization: Pattern matching + simple customization instead of full
 * generation
 */
@Slf4j
public class PatternExecutor {

    private final UserPreferenceEngine userPreferenceEngine;
    private final EmbeddingService embeddingService;
    private final QdrantService qdrantService;
    private final ObjectMapper objectMapper;

    // Common app patterns
    private static final Set<String> COMMON_PATTERNS = Set.of(
            "employee_management",
            "customer_crm",
            "inventory_management",
            "task_tracker",
            "project_management",
            "hr_system",
            "sales_pipeline");

    public PatternExecutor(
            UserPreferenceEngine userPreferenceEngine,
            EmbeddingService embeddingService,
            QdrantService qdrantService) {
        this.userPreferenceEngine = userPreferenceEngine;
        this.embeddingService = embeddingService;
        this.qdrantService = qdrantService;
        this.objectMapper = new ObjectMapper();
        log.info("PatternExecutor initialized");
    }

    /**
     * Try to execute query using a learned pattern
     * 
     * @return Optional containing pattern execution result if suitable, empty
     *         otherwise
     */
    public Optional<PatternExecutionResult> tryPatternExecution(String query, String userId) {
        if (!isPatternExecutable(query)) {
            return Optional.empty();
        }

        try {
            log.info("[PatternExecutor] Attempting pattern-based execution for user: {}", userId);

            // Detect the pattern type
            String patternType = detectPattern(query);

            if (patternType == null) {
                log.debug("[PatternExecutor] No matching pattern detected");
                return Optional.empty();
            }

            // Check if user has this pattern in history
            Map<String, String> userPrefs = userPreferenceEngine.getPreferences(userId);

            // For now, return empty - full implementation requires pattern storage
            // which will be added in Phase 4 (Semantic Cache)
            log.info("[PatternExecutor] Pattern '{}' detected, but template cache not yet implemented", patternType);
            return Optional.empty();

        } catch (Exception e) {
            log.warn("[PatternExecutor] Failed to execute pattern: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Check if query is suitable for pattern execution
     */
    private boolean isPatternExecutable(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }

        String lower = query.toLowerCase().trim();

        // Must be a creation request
        if (!lower.contains("create") && !lower.contains("build") &&
                !lower.contains("make") && !lower.contains("generate")) {
            return false;
        }

        // Must be for an app
        if (!lower.contains("app") && !lower.contains("application") &&
                !lower.contains("system")) {
            return false;
        }

        // Check for common patterns
        for (String pattern : COMMON_PATTERNS) {
            String normalizedPattern = pattern.replace("_", " ");
            if (lower.contains(normalizedPattern)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Detect which pattern the query matches
     */
    private String detectPattern(String query) {
        String lower = query.toLowerCase();

        if (lower.contains("employee") || lower.contains("staff") || lower.contains("hr")) {
            return "employee_management";
        }
        if (lower.contains("customer") || lower.contains("crm") || lower.contains("client")) {
            return "customer_crm";
        }
        if (lower.contains("inventory") || lower.contains("stock") || lower.contains("warehouse")) {
            return "inventory_management";
        }
        if (lower.contains("task") || lower.contains("todo") || lower.contains("checklist")) {
            return "task_tracker";
        }
        if (lower.contains("project") || lower.contains("milestone")) {
            return "project_management";
        }
        if (lower.contains("sales") || lower.contains("pipeline") || lower.contains("lead")) {
            return "sales_pipeline";
        }

        return null;
    }

    /**
     * Extract app name from query
     */
    private String extractAppName(String query) {
        // Simple extraction - can be improved with NLP
        String[] words = query.split("\\s+");

        for (int i = 0; i < words.length - 1; i++) {
            if (words[i].equalsIgnoreCase("create") ||
                    words[i].equalsIgnoreCase("build") ||
                    words[i].equalsIgnoreCase("make")) {
                return words[i + 1];
            }
        }

        return "App";
    }

    /**
     * Result of pattern execution
     */
    @Data
    public static class PatternExecutionResult {
        private final String patternType;
        private final String appName;
        private final Map<String, Object> metadata;
        private final boolean fromCache;
        private final long timestamp;

        public PatternExecutionResult(
                String patternType,
                String appName,
                Map<String, Object> metadata,
                boolean fromCache) {
            this.patternType = patternType;
            this.appName = appName;
            this.metadata = metadata;
            this.fromCache = fromCache;
            this.timestamp = System.currentTimeMillis();
        }
    }
}
