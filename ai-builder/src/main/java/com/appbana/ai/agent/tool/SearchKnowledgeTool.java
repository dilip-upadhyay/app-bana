package com.appbana.ai.agent.tool;

import com.appbana.ai.agent.AgentContext;
import com.appbana.ai.knowledge.KnowledgeBaseService;
import com.appbana.ai.knowledge.SchemaDefinition;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Tool for searching AppBana knowledge base
 * Story 8.3: Essential Tools Implementation
 */
@Slf4j
public class SearchKnowledgeTool implements Tool {

    private final KnowledgeBaseService knowledgeBaseService;

    public SearchKnowledgeTool(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @Override
    public String getName() {
        return "search_knowledge";
    }

    @Override
    public String getDescription() {
        return "Search the AppBana knowledge base for information about field types, components, and schemas. " +
                "Use this when you need to know what field types are available or how to structure components.";
    }

    @Override
    public String getParameterSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "query": {
                      "type": "string",
                      "description": "Search query (e.g., 'email field', 'button component', 'form validation')"
                    },
                    "limit": {
                      "type": "integer",
                      "description": "Maximum number of results (default: 5)",
                      "default": 5
                    }
                  },
                  "required": ["query"]
                }
                """;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments, AgentContext context) {
        long startTime = System.currentTimeMillis();

        try {
            String query = (String) arguments.get("query");
            int limit = arguments.containsKey("limit") ? ((Number) arguments.get("limit")).intValue() : 5;

            log.info("[SearchKnowledgeTool] Searching for: {} (limit: {})", query, limit);

            // Search knowledge base
            List<SchemaDefinition> schemas = knowledgeBaseService.searchRelevantSchemas(query, limit);

            long executionTime = System.currentTimeMillis() - startTime;

            // Format results
            List<Map<String, Object>> results = schemas.stream()
                    .map(schema -> {
                        Map<String, Object> schemaInfo = new HashMap<>();
                        schemaInfo.put("name", schema.getName());
                        schemaInfo.put("type", schema.getType().toString());
                        schemaInfo.put("description", schema.getDescription());
                        return schemaInfo;
                    })
                    .collect(Collectors.toList());

            log.info("[SearchKnowledgeTool] Found {} results", results.size());

            Map<String, Object> result = new HashMap<>();
            result.put("results", results);
            result.put("count", results.size());
            result.put("query", query);

            return ToolResult.success(getName(), result, executionTime);

        } catch (Exception e) {
            log.error("[SearchKnowledgeTool] Execution failed", e);
            return ToolResult.error(getName(), "Execution error: " + e.getMessage());
        }
    }
}
