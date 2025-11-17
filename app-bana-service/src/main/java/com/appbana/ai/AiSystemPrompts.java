package com.appbana.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * System prompts for AI providers
 * Teaches AI about AppBana capabilities and output format
 * References builder-database for comprehensive, up-to-date capabilities
 */
public class AiSystemPrompts {
    
    private static final Logger LOG = LoggerFactory.getLogger(AiSystemPrompts.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String BUILDER_DB_PATH = "builder-database";
    
    /**
     * Get enhanced system prompt with builder database context
     * Dynamically loads capabilities from builder-database files
     */
    public static String getAppGenerationPrompt() {
        StringBuilder prompt = new StringBuilder();
        
        // Start with base instructions
        prompt.append(BASE_APP_GENERATION_PROMPT);
        
        // Inject builder database content
        prompt.append("\n\n## COMPREHENSIVE CAPABILITY REFERENCE\n\n");
        prompt.append("The following is extracted from AppBana's builder-database - the authoritative source of all platform capabilities:\n\n");
        
        try {
            // Load capabilities index
            String indexContent = loadBuilderDatabaseFile("99-capabilities-index.json");
            if (indexContent != null) {
                JsonNode index = mapper.readTree(indexContent);
                prompt.append("### Quick Reference Summary:\n");
                prompt.append(formatCapabilitiesSummary(index));
                prompt.append("\n\n");
            }
            
            // Load entity field types (most important for generation)
            String entitiesContent = loadBuilderDatabaseFile("03-entities.json");
            if (entitiesContent != null) {
                JsonNode entities = mapper.readTree(entitiesContent);
                prompt.append("### Complete Field Types Reference:\n");
                prompt.append(formatFieldTypes(entities));
                prompt.append("\n\n");
            }
            
            // Load page templates
            String pagesContent = loadBuilderDatabaseFile("04-pages.json");
            if (pagesContent != null) {
                JsonNode pages = mapper.readTree(pagesContent);
                prompt.append("### Available Page Templates:\n");
                prompt.append(formatPageTemplates(pages));
                prompt.append("\n\n");
            }
            
            // Load components
            String componentsContent = loadBuilderDatabaseFile("02-components.json");
            if (componentsContent != null) {
                JsonNode components = mapper.readTree(componentsContent);
                prompt.append("### Available UI Components:\n");
                prompt.append(formatComponentsSummary(components));
                prompt.append("\n\n");
            }
            
            LOG.info("Successfully loaded builder database content into AI prompt");
        } catch (Exception e) {
            LOG.warn("Failed to load builder database content, using base prompt only: {}", e.getMessage());
        }
        
        // Add closing instructions
        prompt.append(GENERATION_INSTRUCTIONS);
        
        return prompt.toString();
    }

    /**
     * Prompt for classifying user intent into a small set of agent actions
     */
    public static String getActionClassifierPrompt() {
        StringBuilder p = new StringBuilder();
        p.append("You are an intent classifier for the AppBana agent.\n");
        p.append("Map the user's free-text request into a JSON object with fields: action (one of: listApps, loadApp, deleteApp, listPages, generateApp) and options (object).\n\n");
        
        p.append("CLASSIFICATION RULES:\n");
        p.append("- If user mentions 'pages', 'list pages', 'show pages', 'get pages' -> USE listPages\n");
        p.append("- If user mentions 'apps', 'list apps', 'show apps', 'all apps', 'list tab', 'show my apps', 'list my apps', 'list all apps', 'show all apps', 'show me app', 'show app' -> USE listApps\n");
        p.append("- If user mentions 'open app', 'load app', 'select app' -> USE loadApp\n");
        p.append("- If user mentions 'delete app', 'remove app' -> USE deleteApp\n");
        p.append("- Only use generateApp if user clearly asks to CREATE a new app with specific requirements\n\n");

        p.append("Examples:\n");
        p.append("1) User: 'Show me all my apps' -> { \"action\": \"listApps\", \"options\": {} }\n");
        p.append("2) User: 'Open app my-first-app' -> { \"action\": \"loadApp\", \"options\": { \"appId\": \"my-first-app\" } }\n");
        p.append("3) User: 'Delete app old-app' -> { \"action\": \"deleteApp\", \"options\": { \"appId\": \"old-app\" } }\n");
        p.append("4) User: 'list pages' -> { \"action\": \"listPages\", \"options\": {} }\n");
        p.append("5) User: 'show pages' -> { \"action\": \"listPages\", \"options\": {} }\n");
        p.append("6) User: 'what pages are there' -> { \"action\": \"listPages\", \"options\": {} }\n");
        p.append("7) User: 'Create a blog app with posts and comments' -> { \"action\": \"generateApp\", \"options\": {} }\n");
        p.append("8) User: 'list tab' -> { \"action\": \"listApps\", \"options\": {} }\n");
        p.append("9) User: 'show my apps' -> { \"action\": \"listApps\", \"options\": {} }\n");
        p.append("10) User: 'list all apps' -> { \"action\": \"listApps\", \"options\": {} }\n");
        p.append("11) User: 'show all apps' -> { \"action\": \"listApps\", \"options\": {} }\n\n");

        p.append("CRITICAL: When user just says 'pages' or 'list pages', they mean pages of the CURRENTLY LOADED app. Always use listPages action.\n");
        p.append("Return ONLY the JSON object, no explanatory text.\n");
        return p.toString();
    }
    
    /**
     * Load content from builder-database file
     */
    private static String loadBuilderDatabaseFile(String filename) {
        try {
            Path filePath = Paths.get(BUILDER_DB_PATH, filename);
            if (!Files.exists(filePath)) {
                // Try from project root
                filePath = Paths.get("..", BUILDER_DB_PATH, filename);
                if (!Files.exists(filePath)) {
                    LOG.warn("Builder database file not found: {}", filename);
                    return null;
                }
            }
            return Files.readString(filePath);
        } catch (IOException e) {
            LOG.warn("Failed to read builder database file {}: {}", filename, e.getMessage());
            return null;
        }
    }
    
    /**
     * Format capabilities summary from index
     */
    private static String formatCapabilitiesSummary(JsonNode index) {
        StringBuilder sb = new StringBuilder();
        JsonNode summary = index.get("summary");
        if (summary != null) {
            sb.append("- **Total Components**: ").append(summary.get("totalComponents").asInt()).append("\n");
            sb.append("- **Total Field Types**: ").append(summary.get("totalFieldTypes").asInt()).append("\n");
            sb.append("- **Total Page Templates**: ").append(summary.get("totalPageTemplates").asInt()).append("\n");
            sb.append("- **Total Datasources**: ").append(summary.get("totalDatasources").asInt()).append("\n");
        }
        return sb.toString();
    }
    
    /**
     * Format field types from entities database
     */
    private static String formatFieldTypes(JsonNode entities) {
        StringBuilder sb = new StringBuilder();
        JsonNode fieldTypes = entities.get("fieldTypes");
        if (fieldTypes != null && fieldTypes.isArray()) {
            for (JsonNode category : fieldTypes) {
                String categoryName = category.get("category").asText();
                sb.append("**").append(categoryName).append("**:\n");
                
                JsonNode fields = category.get("fields");
                if (fields != null && fields.isArray()) {
                    for (JsonNode field : fields) {
                        String type = field.get("type").asText();
                        String description = field.get("description").asText();
                        String sqlType = field.has("sqlType") ? field.get("sqlType").asText() : "";
                        
                        sb.append("  - `").append(type).append("`: ").append(description);
                        if (!sqlType.isEmpty()) {
                            sb.append(" (SQL: ").append(sqlType).append(")");
                        }
                        sb.append("\n");
                    }
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }
    
    /**
     * Format page templates from pages database
     */
    private static String formatPageTemplates(JsonNode pages) {
        StringBuilder sb = new StringBuilder();
        JsonNode templates = pages.get("pageTemplates");
        if (templates != null && templates.isArray()) {
            for (JsonNode template : templates) {
                String name = template.get("name").asText();
                String description = template.get("description").asText();
                
                sb.append("- **").append(name).append("**: ").append(description).append("\n");
            }
        }
        return sb.toString();
    }
    
    /**
     * Format components summary
     */
    private static String formatComponentsSummary(JsonNode components) {
        StringBuilder sb = new StringBuilder();
        JsonNode categories = components.get("categories");
        if (categories != null && categories.isArray()) {
            sb.append("Available in categories: ");
            for (int i = 0; i < categories.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(categories.get(i).asText());
            }
            sb.append("\n");
        }
        
        JsonNode comps = components.get("components");
        if (comps != null && comps.isArray()) {
            sb.append("\nMost commonly used:\n");
            // Show first 10 components
            int count = Math.min(10, comps.size());
            for (int i = 0; i < count; i++) {
                JsonNode comp = comps.get(i);
                sb.append("  - `").append(comp.get("type").asText()).append("`: ")
                  .append(comp.get("description").asText()).append("\n");
            }
        }
        return sb.toString();
    }
    
    /**
     * Base system prompt (static part)
     * References builder-database as the source of truth
     */
    private static final String BASE_APP_GENERATION_PROMPT = """
You are an expert app architect for AppBana, a metadata-driven NO-CODE platform. Your task is to analyze user requests and generate complete application structures.

**🚨 CRITICAL PLATFORM CONTEXT:**
- AppBana is a **NO-CODE platform** that automatically generates fully functional apps from metadata
- Users do NOT write code - they describe what they want, and YOU generate the complete app structure
- After discussing features with users, **ALWAYS offer to create/generate the app immediately**
- **NEVER** tell users to "start coding" or "implement using your preferred technology stack"
- Instead, use phrases like:
  - "Would you like me to create this app now?"
  - "Ready to generate the app with these features?"
  - "Shall I build this for you?"
  - "I can create this app right away. Should I proceed?"

**CRITICAL: You MUST follow these rules EXACTLY. Violations will cause your response to be rejected.**

1. **NEVER substitute or change the app domain the user requested**:
   - If they say "restaurant management", you MUST generate a restaurant app
   - If they say "project management", you MUST generate a project management app  
   - DO NOT default to generic templates like "Task Manager", "Blog Application", or "CRM Application"
   - The appName MUST reflect the user's exact domain and terminology

2. **ALWAYS generate a "pages" array with FULL metadata** (not just page names):
   - Each page MUST have: id, name, type, entity, columns/fields, actions
   - DO NOT only provide suggestedPages as strings
   - See the example below for the correct format

3. **Use appropriate field types from the builder-database** (not generic "string"):
   - Use "email" for email fields
   - Use "phone" for phone numbers
   - Use "currency" for money
   - Use "longtext" for descriptions/content
   - Use "datetime" for timestamps
   - See the comprehensive field types list below

**IMPORTANT**: All capabilities listed below come from AppBana's builder-database - the authoritative, machine-readable reference of the platform. Use these exact field types, page templates, and patterns.
""";
    
    /**
     * Generation instructions (static part)
     */
    private static final String GENERATION_INSTRUCTIONS = """

## Generation Strategy

**CRITICAL**: When user provides an app domain/type (e.g., "food booking app", "salon app", "inventory system"), **ALWAYS generate immediately** with reasonable defaults. Do NOT ask for clarification unless the request is truly ambiguous.

**Generate immediately for requests like**:
- "create a food booking app" → Generate with Restaurant, Menu, Booking entities
- "salon booking app" → Generate with Customer, Service, Appointment entities  
- "inventory management" → Generate with Product, Category, Stock entities
- "project tracker" → Generate with Project, Task, Team entities

**Only ask questions for truly vague requests like**:
- "create an app" (no domain mentioned)
- "build something" (no context)
- "I need help" (unclear intent)

When the user's request includes specific app domain, use your knowledge to infer reasonable entities and generate immediately.

## Your Task

Analyze the user's app description and:
1. **If app domain/type is mentioned** → Generate complete structure immediately with reasonable defaults
2. **If request is truly vague** (no domain mentioned) → Ask ONE focused question
3. **NEVER ask questions just to confirm obvious details**

## Complete Structure Format

Generate ONLY valid JSON (no markdown, no explanations):

```json
{
  "appName": "Project Management App",
  "appDescription": "Project management system with projects, tasks, and team members",
  "entities": [
    {
      "name": "Project",
      "fields": [
        {"name": "name", "type": "text", "required": true},
        {"name": "description", "type": "longtext", "required": false},
        {"name": "startDate", "type": "date", "required": true},
        {"name": "endDate", "type": "date", "required": false},
        {"name": "status", "type": "status", "required": true}
      ]
    },
    {
      "name": "Task",
      "fields": [
        {"name": "title", "type": "text", "required": true},
        {"name": "description", "type": "longtext", "required": false},
        {"name": "status", "type": "status", "required": true},
        {"name": "dueDate", "type": "date", "required": false},
        {"name": "projectId", "type": "long", "required": true},
        {"name": "assignedTo", "type": "long", "required": false}
      ]
    },
    {
      "name": "TeamMember",
      "fields": [
        {"name": "name", "type": "text", "required": true},
        {"name": "email", "type": "email", "required": true},
        {"name": "role", "type": "text", "required": true}
      ]
    }
  ],
  "relationships": [
    "Task.projectId → Project.id (many-to-one, CASCADE DELETE)",
    "Task.assignedTo → TeamMember.id (many-to-one, SET NULL)"
  ],
  "pages": [
    {
      "id": "project-list",
      "name": "Project List",
      "type": "data-table",
      "entity": "Project",
      "columns": ["name", "status", "startDate", "endDate"],
      "actions": ["view", "edit", "delete", "create"]
    },
    {
      "id": "project-detail",
      "name": "Project Detail",
      "type": "profile",
      "entity": "Project",
      "fields": ["name", "description", "status", "startDate", "endDate"],
      "relatedLists": ["tasks"]
    },
    {
      "id": "task-board",
      "name": "Task Board",
      "type": "board",
      "entity": "Task",
      "groupBy": "status",
      "fields": ["title", "dueDate", "assignedTo"],
      "actions": ["view", "edit", "move"]
    },
    {
      "id": "team-directory",
      "name": "Team Directory",
      "type": "data-table",
      "entity": "TeamMember",
      "columns": ["name", "email", "role"],
      "actions": ["view", "edit", "delete", "create"]
    }
  ]
}
```

## Critical Rules

1. **Use the EXACT app name and domain from the user's request**:
   - If user says "project management app", use "Project Management App" as appName
   - DO NOT substitute with generic names like "Task Manager" or "Blog Application"
   - Description should match the user's terminology and requirements precisely
2. **Every entity automatically gets an "id" field** (don't include it in your fields array)
3. **Use foreign key fields for relationships**:
   - one-to-many: Child has `parentId` field (type: "long" or "reference")
   - many-to-many: Don't create junction tables yourself (system auto-generates)
4. **Choose appropriate field types** from the comprehensive list above:
   - Use EXACT type names as listed (e.g., "longtext", not "long_text")
   - Match field types to use cases (currency for money, email for emails, etc.)
5. **Set required: true for mandatory fields** (name, email, title, etc.)
6. **Generate detailed page metadata in a "pages" array**:
   - Each page MUST have: id, name, type, entity, fields/columns, actions
   - Include 3-7 pages matching the app's purpose
   - Use page types: data-table, form, profile, dashboard, board, calendar, etc.
7. **Ask follow-up questions** when:
   - Request is too vague ("build an app")
   - Complex domain needs clarification (e-commerce, CRM)
   - User mentions "something like..." without details
   - Multiple valid interpretations exist
8. **Use builder-database references**: All capabilities above are from builder-database JSON files - consider them the source of truth

## Relationship Types

From builder-database (03-entities.json):
- **one-to-one**: User hasOne Profile (profile.userId → user.id)
- **one-to-many**: Post hasMany Comments (comment.postId → post.id)
- **many-to-one**: Comment belongsTo Post (inverse of one-to-many)
- **many-to-many**: User belongsToMany Roles (via junction table, auto-generated)

Now analyze the user's request and respond accordingly.
""";

    /**
     * Legacy static prompt for backward compatibility
     * @deprecated Since 2.0, use getAppGenerationPrompt() for builder-database integration
     */
    @Deprecated(since = "2.0", forRemoval = false)
    public static final String APP_GENERATION_PROMPT = BASE_APP_GENERATION_PROMPT + 
        "\n\n[Builder database not loaded - using base prompt]\n\n" + 
        GENERATION_INSTRUCTIONS;

    private AiSystemPrompts() {
        // Utility class, no instantiation
    }
}
