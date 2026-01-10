package com.appbana.ai.llm;

import com.appbana.ai.config.AiConfig;
import com.appbana.ai.knowledge.AppBanaPromptEnhancer;
import com.appbana.ai.rag.ConversationMemory;
import lombok.extern.slf4j.Slf4j;
import java.util.*;

/**
 * Advanced prompt engine with RAG enhancement
 * Story: 3.3 - Implement Advanced Prompt Engine
 * Updated: Story 7.3 - RAG-Enhanced Prompt Engineering
 */
@Slf4j
public class AdvancedPromptEngine {

    private final AiConfig config;
    private final ConversationMemory conversationMemory;
    private final AppBanaPromptEnhancer promptEnhancer;

    public AdvancedPromptEngine(
            AiConfig config,
            ConversationMemory conversationMemory,
            AppBanaPromptEnhancer promptEnhancer) {
        this.config = config;
        this.conversationMemory = conversationMemory;
        this.promptEnhancer = promptEnhancer;
        log.info("Advanced Prompt Engine initialized with AppBana enhancement");
    }

    public String buildPrompt(String userMessage, String userId, Map<String, Object> context) {
        // Build base prompt
        String basePrompt = buildBasePrompt(userMessage, userId, context);

        // Enhance with AppBana schemas if enhancer is available
        if (promptEnhancer != null) {
            return promptEnhancer.enhancePrompt(userMessage, basePrompt);
        }

        return basePrompt;
    }

    /**
     * Build base prompt without AppBana enhancement
     */
    private String buildBasePrompt(String userMessage, String userId, Map<String, Object> context) {
        StringBuilder prompt = new StringBuilder();

        // AppBana-specific system prompt
        prompt.append("""
                You are an AppBana AI Assistant - an expert in the AppBana low-code platform.

                ABOUT APPBANA:
                AppBana is a metadata-driven platform for building business applications without code.
                Everything is defined through metadata (JSON) - entities, pages, forms, workflows.

                APPBANA CAPABILITIES:

                1. ENTITIES (Data Models):
                   - 39 field types available: text, number, email, phone, date, datetime, boolean,
                     select, multiselect, file, image, currency, url, color, json, etc.
                   - Automatic CRUD API generation
                   - Relationships: one-to-one, one-to-many, many-to-many
                   - Field validation rules

                2. PAGES (User Interface):
                   - List Pages: Display data in tables with sorting, filtering, pagination
                   - Form Pages: Create/edit records with validation
                   - Detail Pages: View individual records
                   - All pages use AppBana components (no custom HTML/CSS)

                3. COMPONENTS:
                   - Data: table, form, input, select, checkbox, radio, date-picker
                   - Layout: container, grid, section, tabs
                   - Actions: button, link
                   - Display: text, heading, image, icon

                4. WORKFLOWS:
                   - State machines for business processes
                   - Automated actions and notifications
                   - Approval workflows

                5. SECURITY:
                   - Multi-tenancy built-in
                   - Role-based access control (RBAC)
                   - Field-level permissions

                IMPORTANT CONSTRAINTS:
                - ONLY suggest solutions using AppBana features
                - NO custom code, external libraries, or frameworks
                - NO direct HTML/CSS editing
                - Everything must be metadata-driven
                - Use ONLY the 39 available field types
                - Use ONLY AppBana components

                YOUR RESPONSES SHOULD:
                - Be specific about AppBana entities, fields, and components
                - Provide concrete metadata examples when helpful
                - Guide users through AppBana's capabilities
                - If something isn't possible in AppBana, explain why and suggest alternatives
                - Be encouraging and educational

                """);

        // Add context from past conversations (if available)
        if (conversationMemory != null) {
            try {
                List<ConversationMemory.Conversation> recentConvs = conversationMemory.getRecentByUser(userId, 5);
                if (!recentConvs.isEmpty()) {
                    prompt.append("Recent conversation history:\n");
                    for (ConversationMemory.Conversation conv : recentConvs) {
                        prompt.append("User: ").append(conv.getMessage()).append("\n");
                        prompt.append("Assistant: ").append(conv.getResponse()).append("\n");
                    }
                    prompt.append("\n");
                }
            } catch (Exception e) {
                log.warn("Failed to fetch conversation history", e);
            }
        }

        // Add current context
        if (context != null && !context.isEmpty()) {
            prompt.append("Current context:\n");
            context.forEach((k, v) -> prompt.append(k).append(": ").append(v).append("\n"));
            prompt.append("\n");
        }

        // Add user message
        prompt.append("User: ").append(userMessage).append("\n");
        prompt.append("Assistant:");

        return prompt.toString();
    }

    /**
     * Build agent-specific prompt with tool descriptions
     * Story 8.4: Agent-LLM Integration
     */
    public String buildAgentPrompt(
            String userMessage,
            String toolDescriptions,
            String conversationHistory,
            Map<String, Object> context) {

        StringBuilder prompt = new StringBuilder();

        // System instructions for agent
        prompt.append("You are AppBana AI Builder - an intelligent agent that creates applications.\n\n");
        prompt.append("You can take REAL ACTIONS by calling tools. ");
        prompt.append("Think step-by-step and use tools to accomplish the user's goal.\n\n");

        // Available tools
        prompt.append("## Available Tools\n\n");
        prompt.append(toolDescriptions);
        prompt.append("\n");

        // AppBana schema context (if enhancer available)
        if (promptEnhancer != null) {
            try {
                String schemaContext = promptEnhancer.getRelevantSchemas(userMessage, 5);
                if (schemaContext != null && !schemaContext.isEmpty()) {
                    prompt.append("## AppBana Schemas\n\n");
                    prompt.append(schemaContext);
                    prompt.append("\n");
                }
            } catch (Exception e) {
                log.warn("Failed to get schema context", e);
            }
        }

        // Response format instructions
        prompt.append("## Response Format\n\n");
        prompt.append("You MUST respond with valid JSON in one of two formats:\n\n");
        prompt.append("**Format 1: Call Tools**\n");
        prompt.append("```json\n");
        prompt.append("{\n");
        prompt.append("  \"thinking\": \"Your step-by-step reasoning...\",\n");
        prompt.append("  \"tool_calls\": [\n");
        prompt.append("    {\"name\": \"tool_name\", \"arguments\": {\"arg1\": \"value1\"}}\n");
        prompt.append("  ]\n");
        prompt.append("}\n");
        prompt.append("```\n\n");
        prompt.append("**Format 2: Final Answer**\n");
        prompt.append("```json\n");
        prompt.append("{\n");
        prompt.append("  \"thinking\": \"My reasoning...\",\n");
        prompt.append("  \"final_answer\": \"Complete response to user...\"\n");
        prompt.append("}\n");
        prompt.append("```\n\n");

        // Conversation history
        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            prompt.append("## Previous Steps\n\n");
            prompt.append(conversationHistory);
            prompt.append("\n");
        }

        // User message
        prompt.append("## User Request\n\n");
        prompt.append(userMessage);
        prompt.append("\n\n");
        prompt.append("Respond with JSON only:");

        return prompt.toString();
    }
}
