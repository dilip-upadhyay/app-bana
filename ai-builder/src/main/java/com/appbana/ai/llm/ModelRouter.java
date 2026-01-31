package com.appbana.ai.llm;

import lombok.extern.slf4j.Slf4j;

import java.util.Set;

/**
 * Intelligent Model Selection Router
 * 
 * Routes LLM requests to the appropriate model based on task complexity:
 * - Premium Model (gpt-4o): High-stakes tasks like metadata generation
 * - Standard Model (gpt-4o-mini): Reasoning loops, error recovery, general chat
 * 
 * Cost Optimization: Use expensive models only when quality is critical
 */
@Slf4j
public class ModelRouter {

    private final String premiumModel;
    private final String standardModel;

    // Tasks that require the premium model for quality
    private static final Set<String> HIGH_STAKES_TASKS = Set.of(
            "generate_metadata",
            "create_entity",
            "create_app",
            "scaffold_app",
            "generate_page",
            "create_schema",
            "final_answer",
            "complex_validation"
    );

    // Tasks that can use the cheaper model
    private static final Set<String> STANDARD_TASKS = Set.of(
            "think",
            "reason",
            "classify_intent",
            "error_recovery",
            "conversation",
            "summarize",
            "parse_response"
    );

    public ModelRouter(String premiumModel, String standardModel) {
        this.premiumModel = premiumModel != null ? premiumModel : "gpt-4o";
        this.standardModel = standardModel != null ? standardModel : "gpt-4o-mini";
        log.info("[ModelRouter] Initialized with premium={}, standard={}", this.premiumModel, this.standardModel);
    }

    /**
     * Select the appropriate model based on task type
     * 
     * @param taskType The type of task being performed
     * @return The model name to use
     */
    public String selectModel(String taskType) {
        if (taskType == null || taskType.isBlank()) {
            log.debug("[ModelRouter] No task type specified, using standard model");
            return standardModel;
        }

        String normalizedTask = taskType.toLowerCase().trim();

        if (isHighStakesTask(normalizedTask)) {
            log.debug("[ModelRouter] High-stakes task '{}' -> premium model: {}", taskType, premiumModel);
            return premiumModel;
        }

        log.debug("[ModelRouter] Standard task '{}' -> standard model: {}", taskType, standardModel);
        return standardModel;
    }

    /**
     * Select model based on prompt content analysis
     * 
     * @param prompt The prompt being sent
     * @param taskType Optional task type hint
     * @return The model name to use
     */
    public String selectModelForPrompt(String prompt, String taskType) {
        // First check explicit task type
        if (taskType != null && !taskType.isBlank()) {
            return selectModel(taskType);
        }

        // Analyze prompt content for task indicators
        if (prompt != null) {
            String lowerPrompt = prompt.toLowerCase();

            // Premium model indicators
            if (lowerPrompt.contains("generate complete json") ||
                lowerPrompt.contains("create entity") ||
                lowerPrompt.contains("scaffold app") ||
                lowerPrompt.contains("generate metadata") ||
                lowerPrompt.contains("create application")) {
                log.debug("[ModelRouter] Prompt content suggests high-stakes task -> premium model");
                return premiumModel;
            }

            // Standard model indicators
            if (lowerPrompt.contains("what tool should i use") ||
                lowerPrompt.contains("analyze this error") ||
                lowerPrompt.contains("summarize the following")) {
                log.debug("[ModelRouter] Prompt content suggests standard task -> standard model");
                return standardModel;
            }
        }

        // Default to standard model for cost efficiency
        return standardModel;
    }

    /**
     * Check if a task is high-stakes and requires premium model
     */
    public boolean isHighStakesTask(String taskType) {
        if (taskType == null) {
            return false;
        }

        String normalized = taskType.toLowerCase().trim();

        // Direct match
        if (HIGH_STAKES_TASKS.contains(normalized)) {
            return true;
        }

        // Partial match for compound task names
        for (String highStakesTask : HIGH_STAKES_TASKS) {
            if (normalized.contains(highStakesTask) || highStakesTask.contains(normalized)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get cost multiplier for a model (for metrics/reporting)
     * Approximate based on OpenAI pricing
     */
    public double getCostMultiplier(String model) {
        if (model == null) {
            return 1.0;
        }

        // gpt-4o is roughly 10x more expensive than gpt-4o-mini
        if (model.equals(premiumModel) || model.contains("gpt-4o") && !model.contains("mini")) {
            return 10.0;
        }

        return 1.0; // Standard model baseline
    }

    /**
     * Get model statistics for monitoring
     */
    public ModelStats getStats() {
        return new ModelStats(premiumModel, standardModel, HIGH_STAKES_TASKS.size(), STANDARD_TASKS.size());
    }

    public String getPremiumModel() {
        return premiumModel;
    }

    public String getStandardModel() {
        return standardModel;
    }

    /**
     * Model router statistics
     */
    public record ModelStats(
            String premiumModel,
            String standardModel,
            int highStakesTaskCount,
            int standardTaskCount
    ) {}
}
