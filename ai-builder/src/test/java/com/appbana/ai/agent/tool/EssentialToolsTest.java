package com.appbana.ai.agent.tool;

import com.appbana.ai.agent.AgentContext;
import com.appbana.ai.knowledge.MetadataValidator;
import com.appbana.ai.knowledge.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for Essential Tools
 * Story 8.3: Essential Tools Implementation
 */
class EssentialToolsTest {

    @Mock
    private MetadataValidator validator;

    private AgentContext context;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        context = AgentContext.create("tenant1", "app1", "user1", "session1", "test-token");
    }

    @Test
    void testCreateEntityTool_SuccessfulCreation() {
        // Arrange
        CreateEntityTool tool = new CreateEntityTool(validator, "http://localhost:8080");

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "Customer");
        arguments.put("displayName", "Customer");
        arguments.put("fields", List.of(
                Map.of("name", "email", "type", "email", "required", true)));

        ValidationResult validResult = new ValidationResult();
        when(validator.validateEntity(any())).thenReturn(validResult);

        // Act
        ToolResult result = tool.execute(arguments, context);

        // Assert
        assertNotNull(result);
        assertEquals("create_entity", result.getToolName());

        // Verify validation was called
        verify(validator, atLeastOnce()).validateEntity(any());
    }

    @Test
    void testCreateEntityTool_ValidationFailure() {
        // Arrange
        CreateEntityTool tool = new CreateEntityTool(validator, "http://localhost:8080");

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("name", "Customer");
        arguments.put("fields", List.of()); // Empty fields - invalid

        ValidationResult invalidResult = new ValidationResult();
        invalidResult.addError(com.appbana.ai.knowledge.ValidationError.error("fields", "Fields cannot be empty"));

        when(validator.validateEntity(any())).thenReturn(invalidResult);
        when(validator.autoFix(any(), any())).thenReturn(new HashMap<>());

        // Act
        ToolResult result = tool.execute(arguments, context);

        // Assert
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("validation failed"));
    }

    @Test
    void testCreateEntityTool_GetName() {
        CreateEntityTool tool = new CreateEntityTool(validator, "http://localhost:8080");
        assertEquals("create_entity", tool.getName());
    }

    @Test
    void testCreateEntityTool_GetDescription() {
        CreateEntityTool tool = new CreateEntityTool(validator, "http://localhost:8080");
        assertNotNull(tool.getDescription());
        assertTrue(tool.getDescription().contains("entity"));
    }

    @Test
    void testCreateEntityTool_GetParameterSchema() {
        CreateEntityTool tool = new CreateEntityTool(validator, "http://localhost:8080");
        String schema = tool.getParameterSchema();
        assertNotNull(schema);
        assertTrue(schema.contains("name"));
        assertTrue(schema.contains("fields"));
    }

    @Test
    void testListEntitiesTool_GetName() {
        ListEntitiesTool tool = new ListEntitiesTool("http://localhost:8080");
        assertEquals("list_entities", tool.getName());
    }

    @Test
    void testListEntitiesTool_GetDescription() {
        ListEntitiesTool tool = new ListEntitiesTool("http://localhost:8080");
        assertNotNull(tool.getDescription());
        assertTrue(tool.getDescription().contains("entities"));
    }

    @Test
    void testGeneratePageTool_GetName() {
        GeneratePageTool tool = new GeneratePageTool(validator, "http://localhost:8080");
        assertEquals("generate_page", tool.getName());
    }

    @Test
    void testGeneratePageTool_GetDescription() {
        GeneratePageTool tool = new GeneratePageTool(validator, "http://localhost:8080");
        assertNotNull(tool.getDescription());
        assertTrue(tool.getDescription().contains("page"));
    }

    @Test
    void testGeneratePageTool_GetParameterSchema() {
        GeneratePageTool tool = new GeneratePageTool(validator, "http://localhost:8080");
        String schema = tool.getParameterSchema();
        assertNotNull(schema);
        assertTrue(schema.contains("name"));
        assertTrue(schema.contains("path"));
        assertTrue(schema.contains("type"));
    }

    @Test
    void testToolRegistry_RegisterAllTools() {
        // Arrange
        ToolRegistry registry = new ToolRegistry();
        CreateEntityTool createTool = new CreateEntityTool(validator, "http://localhost:8080");
        ListEntitiesTool listTool = new ListEntitiesTool("http://localhost:8080");
        GeneratePageTool pageTool = new GeneratePageTool(validator, "http://localhost:8080");

        // Act
        registry.register(createTool);
        registry.register(listTool);
        registry.register(pageTool);

        // Assert
        assertEquals(3, registry.getToolCount());
        assertTrue(registry.hasTool("create_entity"));
        assertTrue(registry.hasTool("list_entities"));
        assertTrue(registry.hasTool("generate_page"));
    }

    @Test
    void testToolRegistry_GetToolDescriptions() {
        // Arrange
        ToolRegistry registry = new ToolRegistry();
        CreateEntityTool createTool = new CreateEntityTool(validator, "http://localhost:8080");
        registry.register(createTool);

        // Act
        String descriptions = registry.getToolDescriptions();

        // Assert
        assertNotNull(descriptions);
        assertTrue(descriptions.contains("create_entity"));
        assertTrue(descriptions.contains("Parameters"));
    }
}
