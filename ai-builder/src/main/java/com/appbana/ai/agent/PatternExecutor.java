package com.appbana.ai.agent;

import com.appbana.ai.agent.tool.Tool;
import com.appbana.ai.agent.tool.ToolRegistry;
import com.appbana.ai.agent.tool.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PatternExecutor - Execute Pre-Compiled Common Flows Without LLM
 * 
 * This component recognizes common user intent patterns and executes
 * the appropriate tools directly, bypassing the LLM entirely.
 * 
 * Benefits:
 * - 100% cost savings for pattern-matched requests
 * - Sub-second response time (no API call)
 * - Consistent, predictable behavior
 * 
 * Supported patterns:
 * 1. "List all apps" → list_apps tool
 * 2. "Show entities in {app}" → list_entities tool
 * 3. "Delete app {name}" → delete_app tool
 * 4. "Create app called {name}" → scaffold_app with minimal setup
 * 5. "Help" / "What can you do?" → Static help response
 */
@Slf4j
public class PatternExecutor {

    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;
    private final List<PatternMatcher> patterns;

    public PatternExecutor(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
        this.objectMapper = new ObjectMapper();
        this.patterns = initializePatterns();
        log.info("PatternExecutor initialized with {} patterns", patterns.size());
    }

    /**
     * Attempt to execute a pattern match
     * 
     * @param userMessage User's input
     * @param context Agent context
     * @return Optional result if pattern matched, empty otherwise
     */
    public Optional<AgentResponse> tryExecute(String userMessage, AgentContext context) {
        if (userMessage == null || userMessage.isBlank()) {
            return Optional.empty();
        }

        String normalized = normalize(userMessage);

        for (PatternMatcher pattern : patterns) {
            Optional<Map<String, String>> match = pattern.match(normalized);
            if (match.isPresent()) {
                log.info("[PatternExecutor] Matched pattern: {} for input: {}", 
                        pattern.getName(), truncate(userMessage, 50));
                
                try {
                    long startTime = System.currentTimeMillis();
                    AgentResponse response = pattern.execute(match.get(), context);
                    log.info("[PatternExecutor] Executed in {}ms", System.currentTimeMillis() - startTime);
                    return Optional.of(response);
                } catch (Exception e) {
                    log.error("[PatternExecutor] Execution failed for pattern: {}", pattern.getName(), e);
                    // Fall through to LLM
                    return Optional.empty();
                }
            }
        }

        log.debug("[PatternExecutor] No pattern matched for: {}", truncate(userMessage, 50));
        return Optional.empty();
    }

    /**
     * Initialize pattern matchers
     */
    private List<PatternMatcher> initializePatterns() {
        List<PatternMatcher> list = new ArrayList<>();

        // Pattern 1: List all apps
        list.add(new PatternMatcher(
                "LIST_APPS",
                List.of(
                        Pattern.compile("^list\\s+(all\\s+)?apps?$", Pattern.CASE_INSENSITIVE),
                        Pattern.compile("^show\\s+(me\\s+)?(all\\s+)?apps?$", Pattern.CASE_INSENSITIVE),
                        Pattern.compile("^what apps\\s+(do\\s+)?(i\\s+)?have\\??$", Pattern.CASE_INSENSITIVE),
                        Pattern.compile("^my apps?$", Pattern.CASE_INSENSITIVE)
                ),
                (params, ctx) -> executeListApps(ctx)
        ));

        // Pattern 2: List entities in current app
        list.add(new PatternMatcher(
                "LIST_ENTITIES",
                List.of(
                        Pattern.compile("^list\\s+(all\\s+)?entities?$", Pattern.CASE_INSENSITIVE),
                        Pattern.compile("^show\\s+(me\\s+)?(all\\s+)?entities?$", Pattern.CASE_INSENSITIVE),
                        Pattern.compile("^what entities\\s+are\\s+(there|in\\s+this\\s+app)\\??$", Pattern.CASE_INSENSITIVE)
                ),
                (params, ctx) -> executeListEntities(ctx)
        ));

        // Pattern 3: Help / capabilities
        list.add(new PatternMatcher(
                "HELP",
                List.of(
                        Pattern.compile("^help$", Pattern.CASE_INSENSITIVE),
                        Pattern.compile("^what can you do\\??$", Pattern.CASE_INSENSITIVE),
                        Pattern.compile("^how\\s+do\\s+i\\s+use\\s+(this|you)\\??$", Pattern.CASE_INSENSITIVE),
                        Pattern.compile("^capabilities\\??$", Pattern.CASE_INSENSITIVE),
                        Pattern.compile("^commands\\??$", Pattern.CASE_INSENSITIVE)
                ),
                (params, ctx) -> executeHelp()
        ));

        // Pattern 4: Greeting (no tool call, just respond)
        list.add(new PatternMatcher(
                "GREETING",
                List.of(
                        Pattern.compile("^(hi|hello|hey|greetings|howdy)\\!?$", Pattern.CASE_INSENSITIVE),
                        Pattern.compile("^good\\s+(morning|afternoon|evening)\\!?$", Pattern.CASE_INSENSITIVE)
                ),
                (params, ctx) -> executeGreeting()
        ));

        // Pattern 5: Simple app creation with just a name
        list.add(new PatternMatcher(
                "CREATE_SIMPLE_APP",
                List.of(
                        Pattern.compile("^create\\s+(?:an?\\s+)?app\\s+(?:called\\s+|named\\s+)?[\"']?(.+?)[\"']?$", Pattern.CASE_INSENSITIVE),
                        Pattern.compile("^new\\s+app\\s+[\"']?(.+?)[\"']?$", Pattern.CASE_INSENSITIVE),
                        Pattern.compile("^make\\s+(?:an?\\s+)?app\\s+(?:called\\s+|named\\s+)?[\"']?(.+?)[\"']?$", Pattern.CASE_INSENSITIVE)
                ),
                (params, ctx) -> {
                    // This is a partial match - we need more info from LLM
                    // Return empty to fall through to LLM for entity design
                    return null;
                }
        ));

        // Pattern 6: Delete app (with confirmation prompt)
        list.add(new PatternMatcher(
                "DELETE_APP",
                List.of(
                        Pattern.compile("^delete\\s+(?:the\\s+)?app\\s+[\"']?(.+?)[\"']?$", Pattern.CASE_INSENSITIVE),
                        Pattern.compile("^remove\\s+(?:the\\s+)?app\\s+[\"']?(.+?)[\"']?$", Pattern.CASE_INSENSITIVE)
                ),
                (params, ctx) -> {
                    // Don't auto-delete - require confirmation
                    String appName = params.get("1");
                    return AgentResponse.success(
                            "⚠️ **Are you sure you want to delete the app '" + appName + "'?**\n\n" +
                            "This action cannot be undone. All entities, pages, and data will be permanently deleted.\n\n" +
                            "Reply with **\"Yes, delete " + appName + "\"** to confirm.",
                            Collections.emptyList(),
                            0
                    );
                }
        ));

        // Pattern 7: Confirm delete
        list.add(new PatternMatcher(
                "CONFIRM_DELETE",
                List.of(
                        Pattern.compile("^yes,?\\s+delete\\s+[\"']?(.+?)[\"']?$", Pattern.CASE_INSENSITIVE)
                ),
                (params, ctx) -> executeDeleteApp(params.get("1"), ctx)
        ));

        // Pattern 8: Status check
        list.add(new PatternMatcher(
                "STATUS",
                List.of(
                        Pattern.compile("^status$", Pattern.CASE_INSENSITIVE),
                        Pattern.compile("^current\\s+context$", Pattern.CASE_INSENSITIVE),
                        Pattern.compile("^where\\s+am\\s+i\\??$", Pattern.CASE_INSENSITIVE)
                ),
                (params, ctx) -> executeStatus(ctx)
        ));

        return list;
    }

    // ==================== Execution Methods ====================

    private AgentResponse executeListApps(AgentContext context) {
        Tool tool = toolRegistry.getTool("list_apps");
        if (tool == null) {
            return AgentResponse.error("list_apps tool not available", Collections.emptyList(), 0);
        }

        ToolResult result = tool.execute(Map.of(), context);
        
        if (result.isSuccess()) {
            return AgentResponse.success(
                    formatListAppsResponse(result),
                    createSteps("list_apps", result),
                    result.getExecutionTimeMs()
            );
        } else {
            return AgentResponse.error(result.getError(), createSteps("list_apps", result), result.getExecutionTimeMs());
        }
    }

    private AgentResponse executeListEntities(AgentContext context) {
        if (context.appId() == null || context.appId().equals("default")) {
            return AgentResponse.success(
                    "📋 Please select an app first to see its entities.\n\n" +
                    "Use **\"List apps\"** to see available apps, then tell me which app to work with.",
                    Collections.emptyList(),
                    0
            );
        }

        Tool tool = toolRegistry.getTool("list_entities");
        if (tool == null) {
            return AgentResponse.error("list_entities tool not available", Collections.emptyList(), 0);
        }

        ToolResult result = tool.execute(Map.of("appId", context.appId()), context);
        
        if (result.isSuccess()) {
            return AgentResponse.success(
                    formatListEntitiesResponse(result, context.appId()),
                    createSteps("list_entities", result),
                    result.getExecutionTimeMs()
            );
        } else {
            return AgentResponse.error(result.getError(), createSteps("list_entities", result), result.getExecutionTimeMs());
        }
    }

    private AgentResponse executeHelp() {
        String helpText = """
                # 🤖 AppBana AI Builder - Help
                
                I can help you build applications quickly. Here's what I can do:
                
                ## 📱 App Management
                - **"Create a [type] app"** - Build a new application (e.g., "Create a CRM app")
                - **"List apps"** - Show all your applications
                - **"Delete app [name]"** - Remove an application
                
                ## 🗂️ Entity Management
                - **"Add entity [name] with fields..."** - Create a new data entity
                - **"List entities"** - Show entities in current app
                - **"Add field [name] to [entity]"** - Add a field to an entity
                
                ## 📄 Page Generation
                - **"Create a list page for [entity]"** - Generate a table view
                - **"Create a form for [entity]"** - Generate an input form
                
                ## 💡 Tips
                - Be specific about what you want to build
                - I'll design entities and fields automatically
                - You can always modify what I create
                
                **Try:** "Build a Salon Booking App" or "Create a simple CRM"
                """;

        return AgentResponse.success(helpText, Collections.emptyList(), 0);
    }

    private AgentResponse executeGreeting() {
        String greeting = "👋 Hello! I'm the AppBana AI Builder. I can help you create applications quickly.\n\n" +
                "**What would you like to build today?**\n\n" +
                "You can say things like:\n" +
                "- \"Create a CRM app\"\n" +
                "- \"Build an Inventory Management System\"\n" +
                "- \"List my apps\"\n\n" +
                "Type **help** for more options.";

        return AgentResponse.success(greeting, Collections.emptyList(), 0);
    }

    private AgentResponse executeDeleteApp(String appName, AgentContext context) {
        Tool tool = toolRegistry.getTool("delete_app");
        if (tool == null) {
            return AgentResponse.error("delete_app tool not available", Collections.emptyList(), 0);
        }

        // Need to find appId from appName - this would require list_apps first
        // For now, assume appName is appId or handle in delete_app tool
        ToolResult result = tool.execute(Map.of("appId", appName), context);
        
        if (result.isSuccess()) {
            return AgentResponse.success(
                    "✅ App **" + appName + "** has been deleted successfully.",
                    createSteps("delete_app", result),
                    result.getExecutionTimeMs()
            );
        } else {
            return AgentResponse.error(
                    "Failed to delete app: " + result.getError(),
                    createSteps("delete_app", result),
                    result.getExecutionTimeMs()
            );
        }
    }

    private AgentResponse executeStatus(AgentContext context) {
        StringBuilder status = new StringBuilder();
        status.append("# 📊 Current Context\n\n");
        status.append("| Property | Value |\n");
        status.append("|----------|-------|\n");
        status.append(String.format("| Tenant ID | `%s` |\n", context.tenantId()));
        status.append(String.format("| App ID | `%s` |\n", 
                context.appId() != null ? context.appId() : "(none selected)"));
        status.append(String.format("| User ID | `%s` |\n", context.userId()));
        
        return AgentResponse.success(status.toString(), Collections.emptyList(), 0);
    }

    // ==================== Helper Methods ====================

    private String normalize(String input) {
        return input.trim()
                .replaceAll("\\s+", " ")
                .replaceAll("[.!?]+$", "");  // Remove trailing punctuation
    }

    private String truncate(String str, int maxLen) {
        if (str == null) return "";
        return str.length() > maxLen ? str.substring(0, maxLen) + "..." : str;
    }

    @SuppressWarnings("unchecked")
    private String formatListAppsResponse(ToolResult result) {
        Object data = result.getData();
        if (data instanceof List) {
            List<Map<String, Object>> apps = (List<Map<String, Object>>) data;
            if (apps.isEmpty()) {
                return "📱 You don't have any apps yet.\n\nWant me to create one? Just say **\"Create a [type] app\"**";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("# 📱 Your Applications\n\n");
            for (Map<String, Object> app : apps) {
                String name = (String) app.getOrDefault("name", "Unnamed");
                String id = (String) app.getOrDefault("id", "");
                sb.append(String.format("- **%s** (`%s`)\n", name, id));
            }
            sb.append("\nTo work with an app, tell me which one (e.g., \"Work with ").append(apps.get(0).get("name")).append("\")");
            return sb.toString();
        }
        return "Found " + data;
    }

    @SuppressWarnings("unchecked")
    private String formatListEntitiesResponse(ToolResult result, String appId) {
        Object data = result.getData();
        if (data instanceof List) {
            List<Map<String, Object>> entities = (List<Map<String, Object>>) data;
            if (entities.isEmpty()) {
                return "📋 No entities found in this app.\n\nWant me to create some? Describe what data you need to store.";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("# 🗂️ Entities in `").append(appId).append("`\n\n");
            for (Map<String, Object> entity : entities) {
                String name = (String) entity.getOrDefault("name", "Unnamed");
                Object fields = entity.get("fields");
                int fieldCount = fields instanceof List ? ((List<?>) fields).size() : 0;
                sb.append(String.format("- **%s** (%d fields)\n", name, fieldCount));
            }
            return sb.toString();
        }
        return "Entities: " + data;
    }

    private List<AgentResponse.AgentStep> createSteps(String toolName, ToolResult result) {
        AgentResponse.AgentStep step = new AgentResponse.AgentStep(1, "Pattern-matched execution");
        step.addToolResult(result);
        return List.of(step);
    }

    // ==================== Inner Classes ====================

    @FunctionalInterface
    private interface PatternHandler {
        AgentResponse handle(Map<String, String> params, AgentContext context) throws Exception;
    }

    private static class PatternMatcher {
        private final String name;
        private final List<Pattern> patterns;
        private final PatternHandler handler;

        PatternMatcher(String name, List<Pattern> patterns, PatternHandler handler) {
            this.name = name;
            this.patterns = patterns;
            this.handler = handler;
        }

        String getName() {
            return name;
        }

        Optional<Map<String, String>> match(String input) {
            for (Pattern pattern : patterns) {
                Matcher matcher = pattern.matcher(input);
                if (matcher.matches()) {
                    Map<String, String> groups = new HashMap<>();
                    for (int i = 0; i <= matcher.groupCount(); i++) {
                        groups.put(String.valueOf(i), matcher.group(i));
                    }
                    return Optional.of(groups);
                }
            }
            return Optional.empty();
        }

        AgentResponse execute(Map<String, String> params, AgentContext context) throws Exception {
            return handler.handle(params, context);
        }
    }
}
