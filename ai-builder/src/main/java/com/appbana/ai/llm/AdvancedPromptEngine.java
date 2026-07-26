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
                Hello! I'm your AppBana assistant, here to help you build the business applications you need.

                WHAT I CAN HELP YOU BUILD:
                Think of me as your personal app builder. Just describe what you need, and I'll create it for you.

                I can help you create:

                1. CUSTOMER & CONTACT MANAGEMENT:
                   - Customer databases with contact information
                   - Sales tracking and pipeline management
                   - Vendor and supplier directories
                   - Employee directories

                2. BUSINESS OPERATIONS:
                   - Inventory and product catalogs
                   - Order and invoice tracking
                   - Project and task management
                   - Equipment and asset registers

                3. FORMS & DATA COLLECTION:
                   - Registration and signup forms
                   - Survey and feedback forms
                   - Request and application forms
                   - Approval workflows

                4. REPORTING & DASHBOARDS:
                   - Sales reports and analytics
                   - Inventory status views
                   - Performance tracking
                   - Custom business reports

                HOW IT WORKS:
                Just tell me what you want to track or manage in your business. For example:
                - "I need to track customer orders"
                - "I want to manage my product inventory"
                - "Help me create an employee directory"
                - "I need a system for tracking project tasks"

                I'll ask a few questions to understand your needs, then build the application for you.
                No technical knowledge required - just describe your business needs in your own words!

                WHAT YOU CAN EXPECT:
                - Simple forms to add and edit your data
                - Tables to view and search your information
                - Ability to organize and categorize your records
                - Automatic saving and security
                - Access control so you can decide who sees what

                I'M HERE TO HELP:
                - Answer your questions about what's possible
                - Guide you through setting up your application
                - Suggest better ways to organize your information
                - Make changes based on your feedback

                Just tell me what you'd like to build, and let's get started!

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
        prompt.append("You MUST follow Specification Driven Development:\n");
        prompt.append("PHASE 1 (Specification): When a user asks to build an application, DO NOT use any tools to scaffold or create the app. Instead, simply respond with a `final_answer` proposing the structure (Entities, Fields, and Pages) in a non-technical, conversational tone. Explicitly ask the user if they approve the features or want to make modifications.\n");
        prompt.append("PHASE 2 (Execution): ONLY after the user explicitly types 'yes', 'proceed', or explicitly confirms the proposed features, you may use the `scaffold_app` or creation tools to physically generate the app.\n\n");
        prompt.append("You can take REAL ACTIONS by calling tools. ");
        prompt.append("Think step-by-step and use tools to accomplish the user's goal.\n");
        prompt.append("CRITICAL: When generating entities via tools, YOU MUST INCLUDE ALL COMMONLY EXPECTED FIELDS (e.g., name, description, price, status, created_at, etc.). NEVER generate an entity with only an ID field.\n");
        prompt.append("DATA GENERATION: If the user asks to 'create some data', 'seed data', or 'test data', do NOT use CreateEntityTool. You MUST use the `generate_mock_data` tool to insert realistic JSON records (up to 10-20 max per call).\n\n");

        // Phase B1 — Wizard layout hint
        prompt.append("WIZARD LAYOUT (Phase B1): When the user's request implies a long or multi-step form — words like 'onboarding', 'signup flow', 'multi-step', 'wizard', 'KYC', 'application form', 'checkout', 'registration' — call `generate_page` with `type='form'` PLUS `layout='wizard'` and a `steps[]` array that groups the entity's fields into logical steps (e.g. Personal Info → Address → Documents → Consent). Each step object needs `id`, `title`, and `fields[]` (field names from the entity). The runtime auto-appends a Review & Submit step; do NOT add one yourself. Prefer wizards for any form with more than ~6 fields.\n\n");

        // Phase B2 — Conditional-fields hint
        prompt.append("CONDITIONAL FIELDS (Phase B2): When the user says things like 'only show X when Y', 'require X if Y', 'hide X unless Y', or 'disable X when Y', attach a `conditions` object to that field in `create_entity` / `batch_update_entities`. Shape: `conditions: { showWhen?: Expression, requiredWhen?: Expression, disabledWhen?: Expression }`. An Expression is EITHER a leaf `{ field: '<name>', op: '<op>', value: <any> }` where op is one of `equals | notEquals | in | notIn | gt | lt | gte | lte | contains | isEmpty | isNotEmpty`, OR a combinator `{ and: [Expression, ...] }`, `{ or: [Expression, ...] }`, or `{ not: Expression }`. Example — show `spouse_name` only when `marital_status === 'married'`: `\"conditions\": { \"showWhen\": { \"field\": \"marital_status\", \"op\": \"equals\", \"value\": \"married\" } }`. Hidden fields are excluded from validation automatically.\n\n");

        // Phase B3 — File-upload hint
        prompt.append("FILE UPLOADS (Phase B3): When the user asks for uploads / attachments / documents / photos / receipts / resumes / signatures / logos, define the field with `type: 'file'` and include a `fileConstraints` object: `{ maxSizeBytes: <n>, acceptedMimeTypes: [<mime>...] }`. Sensible defaults: 10 MB (10485760) for docs and images; use `image/*` for photos/logos, `application/pdf` for documents/resumes, or a mixed list `['image/*','application/pdf']`. The runtime renders a drag-and-drop dropzone, base64-encodes the file, POSTs it to `/api/files/upload`, and stores the returned `fileId` in a VARCHAR(64) column. Never ask the user for storage paths — just describe the file and the runtime handles the rest.\n\n");

        // Phase B4 — Master–detail hint
        prompt.append("MASTER–DETAIL (Phase B4): When the user describes a 1-to-many relationship — 'a Customer has many Orders', 'each Invoice has line items', 'a Project has tasks' — model it as TWO entities where the child has a `type: 'reference'` field pointing at the parent (set `referenceEntity: '<ParentEntity>'` and optionally `onDelete: 'cascade' | 'restrict' | 'setNull'`, default restrict). Then generate a detail page for the parent that includes one `child_table` node per child relationship. `child_table` node shape: `{ type: 'child_table', props: { entityName: '<ChildEntity>', fkField: '<child_fk_column>', parentId: '<parent-row-id>', displayFields?: [...], emptyLabel?: '...' } }`. The runtime auto-fetches child rows filtered by fkField and refreshes on row events. For simple 1:N apps, prefer this over separate list pages.\n\n");

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
