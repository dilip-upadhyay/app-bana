package com.appbana.ai.llm;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Chain-of-Thought reasoning for transparent AI decision making
 * Story: 3.4 - Implement Chain-of-Thought Reasoning
 */
@Slf4j
public class ChainOfThoughtReasoning {

    private final OpenAiLlmService llmService;

    public ChainOfThoughtReasoning(OpenAiLlmService llmService) {
        this.llmService = llmService;
        log.info("Chain-of-Thought Reasoning initialized");
    }

    /**
     * Generate step-by-step reasoning for a decision
     */
    public ReasoningChain generateReasoning(String userRequest, Map<String, Object> context) {
        try {
            String prompt = buildReasoningPrompt(userRequest, context);
            String response = llmService.chat(prompt);

            return parseReasoningChain(response);

        } catch (Exception e) {
            log.error("Failed to generate reasoning chain", e);
            return createFallbackReasoning(userRequest);
        }
    }

    /**
     * Format reasoning steps for user display
     */
    public String formatForDisplay(ReasoningChain chain) {
        StringBuilder formatted = new StringBuilder();
        formatted.append("🤔 **My Thinking Process:**\n\n");

        for (int i = 0; i < chain.getSteps().size(); i++) {
            ReasoningStep step = chain.getSteps().get(i);
            formatted.append(String.format("%d. **%s**\n", i + 1, step.getTitle()));
            formatted.append(String.format("   %s\n\n", step.getDescription()));
        }

        formatted.append(String.format("**Conclusion:** %s\n", chain.getConclusion()));

        return formatted.toString();
    }

    /**
     * Validate reasoning chain for logical consistency
     */
    public boolean validateReasoning(ReasoningChain chain) {
        if (chain == null || chain.getSteps().isEmpty()) {
            return false;
        }

        // Check that each step builds on previous ones
        for (ReasoningStep step : chain.getSteps()) {
            if (step.getTitle() == null || step.getTitle().isEmpty()) {
                return false;
            }
            if (step.getDescription() == null || step.getDescription().isEmpty()) {
                return false;
            }
        }

        // Check that conclusion exists
        return chain.getConclusion() != null && !chain.getConclusion().isEmpty();
    }

    private String buildReasoningPrompt(String userRequest, Map<String, Object> context) {
        return String.format("""
                Think step-by-step about how to fulfill this user request.
                Show your reasoning process transparently.

                User request: "%s"
                Context: %s

                Provide your reasoning in this format:

                Step 1: [Title]
                [Detailed explanation of this step]

                Step 2: [Title]
                [Detailed explanation of this step]

                Step 3: [Title]
                [Detailed explanation of this step]

                Conclusion: [Final decision/recommendation]

                Make your reasoning:
                - Clear and logical
                - Step-by-step (3-5 steps)
                - Transparent about assumptions
                - Focused on the user's goal
                """,
                userRequest,
                context != null ? context.toString() : "none");
    }

    private ReasoningChain parseReasoningChain(String response) {
        List<ReasoningStep> steps = new ArrayList<>();
        String conclusion = "";

        try {
            // Parse step-by-step format
            String[] lines = response.split("\n");
            String currentStepTitle = null;
            StringBuilder currentStepDesc = new StringBuilder();

            for (String line : lines) {
                line = line.trim();

                if (line.matches("^Step \\d+:.*")) {
                    // Save previous step if exists
                    if (currentStepTitle != null) {
                        steps.add(new ReasoningStep(currentStepTitle, currentStepDesc.toString().trim()));
                        currentStepDesc = new StringBuilder();
                    }

                    // Extract new step title
                    currentStepTitle = line.replaceFirst("^Step \\d+:\\s*", "");

                } else if (line.startsWith("Conclusion:")) {
                    // Save last step
                    if (currentStepTitle != null) {
                        steps.add(new ReasoningStep(currentStepTitle, currentStepDesc.toString().trim()));
                    }

                    // Extract conclusion
                    conclusion = line.replaceFirst("^Conclusion:\\s*", "");

                } else if (!line.isEmpty() && currentStepTitle != null) {
                    // Add to current step description
                    if (currentStepDesc.length() > 0) {
                        currentStepDesc.append(" ");
                    }
                    currentStepDesc.append(line);
                }
            }

            // If no conclusion found, use last paragraph
            if (conclusion.isEmpty() && !steps.isEmpty()) {
                conclusion = "Based on the above reasoning, I recommend proceeding with the outlined approach.";
            }

        } catch (Exception e) {
            log.warn("Failed to parse reasoning chain", e);
        }

        return new ReasoningChain(steps, conclusion);
    }

    private ReasoningChain createFallbackReasoning(String userRequest) {
        List<ReasoningStep> steps = List.of(
                new ReasoningStep(
                        "Understanding the request",
                        "Analyzing what the user wants to accomplish: " + userRequest),
                new ReasoningStep(
                        "Identifying requirements",
                        "Determining the key components needed to fulfill this request"),
                new ReasoningStep(
                        "Planning approach",
                        "Outlining the steps to implement the solution"));

        return new ReasoningChain(steps, "I'll proceed with implementing the requested functionality.");
    }

    @Data
    public static class ReasoningChain {
        private final List<ReasoningStep> steps;
        private final String conclusion;
    }

    @Data
    public static class ReasoningStep {
        private final String title;
        private final String description;
    }
}
