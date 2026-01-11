package com.appbana.ai.agent.tool;

import com.appbana.ai.agent.AgentContext;
import com.appbana.ai.knowledge.AppBanaSchemaLoader;
import com.appbana.ai.knowledge.MetadataValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Story 2: Unit tests for ScaffoldAppTool parameter validation
 */
class ScaffoldAppToolTest {

    private ScaffoldAppTool tool;
    private AgentContext context;

    @BeforeEach
    void setUp() {
        AppBanaSchemaLoader schemaLoader = new AppBanaSchemaLoader();
        MetadataValidator validator = new MetadataValidator(schemaLoader);
        tool = new ScaffoldAppTool(validator, "http://localhost:8080");
        context = AgentContext.create("test-tenant", "test-app-id", "test-user", "test-session", "test-token");
    }

    @Test
    void testToolMetadata() {
        assertEquals("scaffold_app", tool.getName());
        assertNotNull(tool.getDescription());
        assertNotNull(tool.getParameterSchema());
        assertTrue(tool.getDescription().contains("ONE SHOT"));
    }

    @Test
    void testMissingAppName() {
        Map<String, Object> args = new HashMap<>();
        args.put("entities", List.of(Map.of("name", "Customer", "fields", List.of())));

        ToolResult result = tool.execute(args, context);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("appName"));
    }

    @Test
    void testEmptyAppName() {
        Map<String, Object> args = new HashMap<>();
        args.put("appName", "");
        args.put("entities", List.of(Map.of("name", "Customer", "fields", List.of())));

        ToolResult result = tool.execute(args, context);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("appName"));
    }

    @Test
    void testMissingEntities() {
        Map<String, Object> args = new HashMap<>();
        args.put("appName", "Test App");

        ToolResult result = tool.execute(args, context);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("entities"));
    }

    @Test
    void testEmptyEntitiesList() {
        Map<String, Object> args = new HashMap<>();
        args.put("appName", "Test App");
        args.put("entities", new ArrayList<>());

        ToolResult result = tool.execute(args, context);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("entities"));
    }

    @Test
    void testValidSkeletonInput() {
        Map<String, Object> args = new HashMap<>();
        args.put("appName", "Salon Manager");

        List<Map<String, Object>> entities = new ArrayList<>();
        Map<String, Object> entity = new HashMap<>();
        entity.put("name", "Customer");
        entity.put("fields", List.of(
                Map.of("id", "name", "name", "name", "type", "text", "required", true)));
        entities.add(entity);
        args.put("entities", entities);

        List<Map<String, Object>> pages = new ArrayList<>();
        pages.add(Map.of(
                "name", "CustomerList",
                "path", "/customers",
                "type", "list",
                "entityName", "Customer"));
        args.put("pages", pages);

        ToolResult result = tool.execute(args, context);

        assertTrue(result.isSuccess());

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();

        assertEquals("skeleton", data.get("status"));
        assertEquals("Salon Manager", data.get("appName"));
        assertEquals(1, data.get("entitiesProvided"));
        assertEquals(1, data.get("pagesProvided"));
    }

    @Test
    void testPagesOptional() {
        Map<String, Object> args = new HashMap<>();
        args.put("appName", "Simple App");
        args.put("entities", List.of(
                Map.of("name", "Entity1", "fields", List.of())));
        // No pages provided

        ToolResult result = tool.execute(args, context);

        assertTrue(result.isSuccess());

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();

        assertEquals(0, data.get("pagesProvided"));
    }
}
