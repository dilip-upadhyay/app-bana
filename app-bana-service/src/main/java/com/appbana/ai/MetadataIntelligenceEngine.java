package com.appbana.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Metadata-Driven Intelligence Engine
 * 
 * Core Principle: ALL AI behavior is defined in
 * builder-database/11-intent-patterns.json
 * Code is just the interpreter, not the intelligence itself.
 * 
 * Benefits:
 * - No code changes needed to add new intents
 * - A/B test different patterns via metadata
 * - Users can customize AI behavior via UI
 * - Intent patterns version-controlled with app templates
 */
public class MetadataIntelligenceEngine {

    private static final Logger LOG = LoggerFactory.getLogger(MetadataIntelligenceEngine.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String INTENT_PATTERNS_PATH = "../builder-database/11-intent-patterns.json";

    private static IntentPatternsConfig config;
    private static boolean initialized = false;

    /**
     * Initialize and load intent patterns from metadata
     */
    public static synchronized void initialize() {
        if (initialized) {
            return;
        }

        try {
            loadIntentPatterns();
            startHotReload();
            initialized = true;
            LOG.info("[MetaAI] Initialized with {} intents from metadata",
                    config.intents.size());
        } catch (Exception e) {
            LOG.error("[MetaAI] Failed to initialize", e);
            config = createFallbackConfig();
            initialized = true;
        }
    }

    /**
     * Classify intent using ONLY metadata-defined patterns
     */
    public static IntentResult classifyIntent(String input, Map<String, Object> context) {
        if (!initialized) {
            initialize();
        }

        if (input == null || input.trim().isEmpty()) {
            return new IntentResult("unknown", 0.0, null);
        }

        Map<String, Double> scores = new HashMap<>();
        Map<String, String> explanations = new HashMap<>();

        // Evaluate each intent from metadata
        for (IntentDefinition intent : config.intents) {
            double score = evaluateIntent(intent, input, context, explanations);
            scores.put(intent.id, score);
        }

        // Find best match
        Map.Entry<String, Double> best = scores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElse(null);

        if (best == null) {
            return new IntentResult("unknown", 0.0, null);
        }

        String bestIntent = best.getKey();
        double confidence = best.getValue();
        IntentDefinition intentDef = getIntent(bestIntent);

        LOG.info("[MetaAI] Input: '{}' → Intent: '{}' (confidence: {:.2f})",
                input.length() > 50 ? input.substring(0, 50) + "..." : input,
                bestIntent, confidence);
        LOG.debug("[MetaAI] Explanation: {}", explanations.get(bestIntent));

        // Check confidence threshold
        if (confidence < intentDef.confidence_threshold) {
            LOG.info("[MetaAI] Confidence {:.2f} below threshold {:.2f}, checking fallback",
                    confidence, intentDef.confidence_threshold);
            return applyFallbackStrategy(input, context, confidence, bestIntent);
        }

        return new IntentResult(bestIntent, confidence, intentDef, explanations.get(bestIntent));
    }

    /**
     * Evaluate a single intent definition against input
     */
    private static double evaluateIntent(IntentDefinition intent,
            String input,
            Map<String, Object> context,
            Map<String, String> explanations) {
        double totalScore = 0.0;
        List<String> matchedPatterns = new ArrayList<>();

        // Evaluate each pattern
        for (PatternDefinition pattern : intent.patterns) {
            double patternScore = evaluatePattern(pattern, input, context);
            if (patternScore > 0) {
                totalScore += patternScore * pattern.weight;
                matchedPatterns.add(String.format("%s(%.2f)", pattern.type, patternScore));
            }
        }

        // Apply context boosters
        if (intent.context_boosters != null) {
            for (ContextBooster booster : intent.context_boosters) {
                if (evaluateCondition(booster.condition, context)) {
                    totalScore += booster.boost;
                    matchedPatterns.add(String.format("boost:%s(+%.2f)",
                            booster.condition, booster.boost));
                }
            }
        }

        // Cap at 1.0
        totalScore = Math.min(totalScore, 1.0);

        // Record explanation
        if (!matchedPatterns.isEmpty()) {
            explanations.put(intent.id, String.join(", ", matchedPatterns));
        }

        return totalScore;
    }

    /**
     * Evaluate a pattern definition
     */
    private static double evaluatePattern(PatternDefinition pattern,
            String input,
            Map<String, Object> context) {
        // Check context requirements
        if (pattern.context_required != null && !pattern.context_required.isEmpty()) {
            if (!hasContextValue(context, pattern.context_required)) {
                return 0.0;
            }
        }

        switch (pattern.type) {
            case "keyword":
                return evaluateKeywordPattern(pattern, input);

            case "exact_match":
                return evaluateExactMatch(pattern, input);

            case "approval_phrase":
                return evaluateApprovalPhrase(pattern, input);

            case "entity_overlap":
                return evaluateEntityOverlap(pattern, input, context);

            case "question_word":
                return evaluateQuestionWord(pattern, input);

            case "ordinal":
                return evaluateOrdinal(pattern, input);

            default:
                LOG.warn("[MetaAI] Unknown pattern type: {}", pattern.type);
                return 0.0;
        }
    }

    private static double evaluateKeywordPattern(PatternDefinition pattern, String input) {
        String lower = input.toLowerCase();

        // Check exact matches first
        if (pattern.exact_matches != null) {
            for (String exact : pattern.exact_matches) {
                if (lower.equals(exact.toLowerCase())) {
                    return 1.0;
                }
            }
        }

        // Count keyword matches
        long keywordMatches = 0;
        if (pattern.keywords != null) {
            keywordMatches = pattern.keywords.stream()
                    .filter(kw -> lower.contains(kw.toLowerCase()))
                    .count();
        }

        if (keywordMatches == 0)
            return 0.0;

        // Check must_contain requirements
        if (pattern.must_contain != null && !pattern.must_contain.isEmpty()) {
            boolean hasRequired = pattern.must_contain.stream()
                    .anyMatch(req -> lower.contains(req.toLowerCase()));
            if (!hasRequired)
                return 0.0;
        }

        // Score based on match ratio
        double matchRatio = pattern.keywords != null && !pattern.keywords.isEmpty()
                ? (double) keywordMatches / pattern.keywords.size()
                : 1.0;

        return matchRatio;
    }

    private static double evaluateExactMatch(PatternDefinition pattern, String input) {
        String lower = input.toLowerCase().trim();

        if (pattern.exact_matches != null) {
            for (String exact : pattern.exact_matches) {
                if (lower.equals(exact.toLowerCase())) {
                    return 1.0;
                }
            }
        }

        return 0.0;
    }

    private static double evaluateApprovalPhrase(PatternDefinition pattern, String input) {
        String lower = input.toLowerCase().trim();

        // Remove punctuation
        lower = lower.replaceAll("[!.?]", "");

        if (pattern.exact_matches != null) {
            for (String phrase : pattern.exact_matches) {
                if (lower.equals(phrase.toLowerCase())) {
                    return 1.0;
                }
            }
        }

        return 0.0;
    }

    private static double evaluateEntityOverlap(PatternDefinition pattern,
            String input,
            Map<String, Object> context) {
        // Extract entities from context source
        List<String> contextEntities = getContextList(context, pattern.source);
        if (contextEntities == null || contextEntities.isEmpty()) {
            return 0.0;
        }

        // Extract entities from input (simple word extraction)
        String lower = input.toLowerCase();
        List<String> inputWords = Arrays.asList(lower.split("\\s+"));

        // Count overlap
        long overlap = inputWords.stream()
                .filter(word -> contextEntities.stream()
                        .anyMatch(entity -> entity.toLowerCase().contains(word) || word.contains(entity.toLowerCase())))
                .count();

        if (overlap < (pattern.min_overlap != null ? pattern.min_overlap : 1)) {
            return 0.0;
        }

        return Math.min(1.0, (double) overlap / contextEntities.size());
    }

    private static double evaluateQuestionWord(PatternDefinition pattern, String input) {
        String lower = input.toLowerCase();

        // Check if contains question words
        if (pattern.keywords != null) {
            boolean hasQuestionWord = pattern.keywords.stream()
                    .anyMatch(qw -> lower.startsWith(qw.toLowerCase()) ||
                            lower.contains(" " + qw.toLowerCase()));

            if (hasQuestionWord) {
                return 1.0;
            }
        }

        return 0.0;
    }

    private static double evaluateOrdinal(PatternDefinition pattern, String input) {
        String lower = input.toLowerCase();

        if (pattern.keywords != null) {
            boolean hasOrdinal = pattern.keywords.stream()
                    .anyMatch(ord -> lower.contains(ord.toLowerCase()));

            if (hasOrdinal && pattern.must_contain != null) {
                boolean hasRequired = pattern.must_contain.stream()
                        .anyMatch(req -> lower.contains(req.toLowerCase()));
                if (hasRequired)
                    return 1.0;
            }
        }

        return 0.0;
    }

    private static boolean evaluateCondition(String condition, Map<String, Object> context) {
        if (context == null)
            return false;

        switch (condition) {
            case "no_active_app":
                return context.get("lastDiscussedAppDescription") == null;

            case "has_active_app_discussion":
                return context.get("lastDiscussedAppDescription") != null;

            case "last_discussed_app":
                return context.get("lastDiscussedAppDescription") != null;

            case "recent_app_structure_shown":
            case "structure_just_shown":
                // Check if structure was shown in last message
                Object lastAction = context.get("lastAction");
                return "show_structure".equals(lastAction) || "generate_app".equals(lastAction);

            default:
                return context.containsKey(condition);
        }
    }

    private static boolean hasContextValue(Map<String, Object> context, String key) {
        return context != null && context.get(key) != null;
    }

    @SuppressWarnings("unchecked")
    private static List<String> getContextList(Map<String, Object> context, String key) {
        if (context == null || key == null)
            return Collections.emptyList();

        Object value = context.get(key);
        if (value instanceof List) {
            return (List<String>) value;
        }
        return Collections.emptyList();
    }

    private static IntentResult applyFallbackStrategy(String input,
            Map<String, Object> context,
            double confidence,
            String bestIntent) {
        FallbackStrategy fallback = config.fallback_strategy;

        if (fallback == null || confidence >= fallback.confidence_below) {
            return new IntentResult(bestIntent, confidence, getIntent(bestIntent));
        }

        if ("use_gpt".equals(fallback.action)) {
            LOG.info("[MetaAI] Confidence {:.2f} below threshold, falling back to GPT", confidence);
            return new IntentResult("gpt_fallback", confidence, null);
        }

        return new IntentResult("unknown", confidence, null);
    }

    private static void loadIntentPatterns() throws IOException {
        File file = new File(INTENT_PATTERNS_PATH);
        if (!file.exists()) {
            LOG.warn("[MetaAI] Intent patterns file not found: {}", INTENT_PATTERNS_PATH);
            config = createFallbackConfig();
            return;
        }

        config = MAPPER.readValue(file, IntentPatternsConfig.class);
        LOG.info("[MetaAI] Loaded intent patterns: version {}, {} intents",
                config.metaVersion, config.intents.size());
    }

    private static void startHotReload() {
        // TODO: Implement file watcher for hot-reload
        // For now, manual reload via API endpoint
    }

    private static IntentPatternsConfig createFallbackConfig() {
        IntentPatternsConfig fallback = new IntentPatternsConfig();
        fallback.intents = new ArrayList<>();
        fallback.fallback_strategy = new FallbackStrategy();
        fallback.fallback_strategy.action = "use_gpt";
        fallback.fallback_strategy.confidence_below = 0.5;
        return fallback;
    }

    private static IntentDefinition getIntent(String intentId) {
        if (config == null || config.intents == null)
            return null;

        return config.intents.stream()
                .filter(i -> i.id.equals(intentId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Reload patterns from disk (for hot-reload)
     */
    public static synchronized void reload() {
        try {
            loadIntentPatterns();
            LOG.info("[MetaAI] Reloaded intent patterns");
        } catch (IOException e) {
            LOG.error("[MetaAI] Failed to reload patterns", e);
        }
    }

    // ========== Data Classes ==========

    public static class IntentPatternsConfig {
        @JsonProperty("metaVersion")
        public String metaVersion;

        @JsonProperty("lastUpdated")
        public String lastUpdated;

        @JsonProperty("description")
        public String description;

        @JsonProperty("intents")
        public List<IntentDefinition> intents;

        @JsonProperty("fallback_strategy")
        public FallbackStrategy fallback_strategy;

        @JsonProperty("learning_config")
        public LearningConfig learning_config;
    }

    public static class IntentDefinition {
        @JsonProperty("id")
        public String id;

        @JsonProperty("name")
        public String name;

        @JsonProperty("description")
        public String description;

        @JsonProperty("confidence_threshold")
        public double confidence_threshold;

        @JsonProperty("patterns")
        public List<PatternDefinition> patterns;

        @JsonProperty("context_boosters")
        public List<ContextBooster> context_boosters;

        @JsonProperty("context_requirements")
        public ContextRequirements context_requirements;

        @JsonProperty("actions")
        public List<String> actions;

        @JsonProperty("skip_gpt")
        public Boolean skip_gpt;

        @JsonProperty("use_smalltalk_engine")
        public Boolean use_smalltalk_engine;
    }

    public static class PatternDefinition {
        @JsonProperty("type")
        public String type;

        @JsonProperty("keywords")
        public List<String> keywords;

        @JsonProperty("exact_matches")
        public List<String> exact_matches;

        @JsonProperty("must_contain")
        public List<String> must_contain;

        @JsonProperty("weight")
        public double weight = 1.0;

        @JsonProperty("context_required")
        public String context_required;

        @JsonProperty("min_overlap")
        public Integer min_overlap;

        @JsonProperty("source")
        public String source;

        @JsonProperty("description")
        public String description;
    }

    public static class ContextBooster {
        @JsonProperty("condition")
        public String condition;

        @JsonProperty("boost")
        public double boost;

        @JsonProperty("description")
        public String description;
    }

    public static class ContextRequirements {
        @JsonProperty("must_have")
        public String must_have;

        @JsonProperty("action")
        public String action;
    }

    public static class FallbackStrategy {
        @JsonProperty("confidence_below")
        public double confidence_below;

        @JsonProperty("action")
        public String action;

        @JsonProperty("include_context")
        public Boolean include_context;

        @JsonProperty("log_for_learning")
        public Boolean log_for_learning;
    }

    public static class LearningConfig {
        @JsonProperty("enabled")
        public Boolean enabled;

        @JsonProperty("correction_threshold")
        public Integer correction_threshold;

        @JsonProperty("auto_update_patterns")
        public Boolean auto_update_patterns;

        @JsonProperty("feedback_weight")
        public Double feedback_weight;

        @JsonProperty("suggestions_file")
        public String suggestions_file;
    }

    public static class IntentResult {
        public final String intent;
        public final double confidence;
        public final IntentDefinition definition;
        public final String explanation;

        public IntentResult(String intent, double confidence, IntentDefinition definition) {
            this(intent, confidence, definition, null);
        }

        public IntentResult(String intent, double confidence, IntentDefinition definition, String explanation) {
            this.intent = intent;
            this.confidence = confidence;
            this.definition = definition;
            this.explanation = explanation;
        }

        public boolean shouldUseGPT() {
            return "gpt_fallback".equals(intent) ||
                    (definition != null && definition.skip_gpt != null && !definition.skip_gpt);
        }

        public boolean shouldUseSmallTalk() {
            return definition != null && definition.use_smalltalk_engine != null &&
                    definition.use_smalltalk_engine;
        }
    }
}
