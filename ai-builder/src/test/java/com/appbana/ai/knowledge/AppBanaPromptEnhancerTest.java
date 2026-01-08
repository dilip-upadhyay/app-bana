package com.appbana.ai.knowledge;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AppBanaPromptEnhancer
 * Story 7.3: RAG-Enhanced Prompt Engineering
 */
@ExtendWith(MockitoExtension.class)
class AppBanaPromptEnhancerTest {

    @Mock
    private KnowledgeBaseService knowledgeBaseService;

    private AppBanaPromptEnhancer promptEnhancer;

    @BeforeEach
    void setUp() {
        promptEnhancer = new AppBanaPromptEnhancer(knowledgeBaseService);
    }

    @Test
    void testEnhancePrompt_CustomerForm() throws Exception {
        // Arrange
        String userMessage = "create a customer form with email and phone";
        String basePrompt = "User: create a customer form with email and phone\nAssistant:";

        // Create mock schemas
        List<SchemaDefinition> mockSchemas = new ArrayList<>();

        // Email field
        SchemaDefinition emailSchema = new SchemaDefinition();
        emailSchema.setId("field_email");
        emailSchema.setName("email");
        emailSchema.setType(SchemaDefinition.SchemaType.ENTITY_FIELD);
        emailSchema.setDescription("Email with validation");
        emailSchema.setExamples(List.of("user@example.com"));
        Map<String, String> emailMeta = new HashMap<>();
        emailMeta.put("htmlType", "email");
        emailSchema.setMetadata(emailMeta);
        mockSchemas.add(emailSchema);

        // Phone field
        SchemaDefinition phoneSchema = new SchemaDefinition();
        phoneSchema.setId("field_phone");
        phoneSchema.setName("phone");
        phoneSchema.setType(SchemaDefinition.SchemaType.ENTITY_FIELD);
        phoneSchema.setDescription("Phone number with formatting");
        phoneSchema.setExamples(List.of("+1-555-0123"));
        Map<String, String> phoneMeta = new HashMap<>();
        phoneMeta.put("htmlType", "tel");
        phoneSchema.setMetadata(phoneMeta);
        mockSchemas.add(phoneSchema);

        when(knowledgeBaseService.searchRelevantSchemas(eq(userMessage), anyInt()))
                .thenReturn(mockSchemas);

        // Act
        String enhanced = promptEnhancer.enhancePrompt(userMessage, basePrompt);

        // Assert
        assertNotNull(enhanced);
        assertTrue(enhanced.contains("AppBana Platform Context"));
        assertTrue(enhanced.contains("email"));
        assertTrue(enhanced.contains("phone"));
        assertTrue(enhanced.contains("Email with validation"));
        assertTrue(enhanced.contains("Phone number with formatting"));
        assertTrue(enhanced.contains(basePrompt));

        verify(knowledgeBaseService).searchRelevantSchemas(eq(userMessage), anyInt());
    }

    @Test
    void testEnhancePrompt_DataTable() throws Exception {
        // Arrange
        String userMessage = "build a data table for products";
        String basePrompt = "User: build a data table for products\nAssistant:";

        // Create mock table component schema
        SchemaDefinition tableSchema = new SchemaDefinition();
        tableSchema.setId("component_table");
        tableSchema.setName("table");
        tableSchema.setType(SchemaDefinition.SchemaType.COMPONENT);
        tableSchema.setDescription("Data table");

        Map<String, SchemaDefinition.PropertyDefinition> props = new HashMap<>();
        SchemaDefinition.PropertyDefinition entityProp = new SchemaDefinition.PropertyDefinition();
        entityProp.setName("entity");
        entityProp.setType("string");
        entityProp.setDescription("Entity to display");
        entityProp.setRequired(true);
        props.put("entity", entityProp);

        tableSchema.setProperties(props);
        tableSchema.setExamples(List.of("{\"type\":\"table\",\"props\":{\"entity\":\"Product\"}}"));

        when(knowledgeBaseService.searchRelevantSchemas(eq(userMessage), anyInt()))
                .thenReturn(List.of(tableSchema));

        // Act
        String enhanced = promptEnhancer.enhancePrompt(userMessage, basePrompt);

        // Assert
        assertNotNull(enhanced);
        assertTrue(enhanced.contains("Components:"));
        assertTrue(enhanced.contains("table"));
        assertTrue(enhanced.contains("Data table"));
        assertTrue(enhanced.contains("entity"));
        assertTrue(enhanced.contains("[required]"));

        verify(knowledgeBaseService).searchRelevantSchemas(eq(userMessage), anyInt());
    }

    @Test
    void testEnhancePrompt_NoRelevantSchemas() throws Exception {
        // Arrange
        String userMessage = "hello";
        String basePrompt = "User: hello\nAssistant:";

        when(knowledgeBaseService.searchRelevantSchemas(eq(userMessage), anyInt()))
                .thenReturn(Collections.emptyList());

        // Act
        String enhanced = promptEnhancer.enhancePrompt(userMessage, basePrompt);

        // Assert
        assertEquals(basePrompt, enhanced);
        verify(knowledgeBaseService).searchRelevantSchemas(eq(userMessage), anyInt());
    }

    @Test
    void testEnhancePrompt_KnowledgeBaseFailure() throws Exception {
        // Arrange
        String userMessage = "create a form";
        String basePrompt = "User: create a form\nAssistant:";

        when(knowledgeBaseService.searchRelevantSchemas(eq(userMessage), anyInt()))
                .thenThrow(new KnowledgeBaseException("Search failed"));

        // Act
        String enhanced = promptEnhancer.enhancePrompt(userMessage, basePrompt);

        // Assert - should return base prompt on error
        assertEquals(basePrompt, enhanced);
    }

    @Test
    void testEnhancePrompt_MixedSchemaTypes() throws Exception {
        // Arrange
        String userMessage = "create an input field for email";
        String basePrompt = "User: create an input field for email\nAssistant:";

        List<SchemaDefinition> mockSchemas = new ArrayList<>();

        // Email field type
        SchemaDefinition emailField = new SchemaDefinition();
        emailField.setId("field_email");
        emailField.setName("email");
        emailField.setType(SchemaDefinition.SchemaType.ENTITY_FIELD);
        emailField.setDescription("Email with validation");
        mockSchemas.add(emailField);

        // Input component
        SchemaDefinition inputComponent = new SchemaDefinition();
        inputComponent.setId("component_input");
        inputComponent.setName("input");
        inputComponent.setType(SchemaDefinition.SchemaType.COMPONENT);
        inputComponent.setDescription("Text input field");
        mockSchemas.add(inputComponent);

        when(knowledgeBaseService.searchRelevantSchemas(eq(userMessage), anyInt()))
                .thenReturn(mockSchemas);

        // Act
        String enhanced = promptEnhancer.enhancePrompt(userMessage, basePrompt);

        // Assert
        assertTrue(enhanced.contains("Field Types:"));
        assertTrue(enhanced.contains("Components:"));
        assertTrue(enhanced.contains("email"));
        assertTrue(enhanced.contains("input"));
    }

    @Test
    void testGetComponentExamples() throws Exception {
        // Arrange
        List<String> mockExamples = List.of(
                "{\"type\":\"input\",\"props\":{\"label\":\"Email\"}}",
                "{\"type\":\"input\",\"props\":{\"label\":\"Name\"}}");

        when(knowledgeBaseService.getExamples("input")).thenReturn(mockExamples);

        // Act
        List<String> examples = promptEnhancer.getComponentExamples("input");

        // Assert
        assertEquals(2, examples.size());
        assertEquals(mockExamples, examples);
        verify(knowledgeBaseService).getExamples("input");
    }

    @Test
    void testGetComponentExamples_Failure() throws Exception {
        // Arrange
        when(knowledgeBaseService.getExamples("input"))
                .thenThrow(new KnowledgeBaseException("Failed"));

        // Act
        List<String> examples = promptEnhancer.getComponentExamples("input");

        // Assert
        assertTrue(examples.isEmpty());
    }

    @Test
    void testSearchSchemasByType() throws Exception {
        // Arrange
        SchemaDefinition mockSchema = new SchemaDefinition();
        mockSchema.setId("component_button");
        mockSchema.setName("button");
        mockSchema.setType(SchemaDefinition.SchemaType.COMPONENT);

        when(knowledgeBaseService.searchByType(
                eq(SchemaDefinition.SchemaType.COMPONENT),
                eq("action button"),
                eq(5))).thenReturn(List.of(mockSchema));

        // Act
        List<SchemaDefinition> results = promptEnhancer.searchSchemasByType(
                SchemaDefinition.SchemaType.COMPONENT,
                "action button",
                5);

        // Assert
        assertEquals(1, results.size());
        assertEquals("button", results.get(0).getName());
    }

    @Test
    void testEnhancePrompt_WithExamples() throws Exception {
        // Arrange
        String userMessage = "create a button";
        String basePrompt = "User: create a button\nAssistant:";

        SchemaDefinition buttonSchema = new SchemaDefinition();
        buttonSchema.setId("component_button");
        buttonSchema.setName("button");
        buttonSchema.setType(SchemaDefinition.SchemaType.COMPONENT);
        buttonSchema.setDescription("Action button");
        buttonSchema.setExamples(List.of(
                "{\"type\":\"button\",\"props\":{\"label\":\"Save\"}}",
                "{\"type\":\"button\",\"props\":{\"label\":\"Cancel\"}}"));

        when(knowledgeBaseService.searchRelevantSchemas(eq(userMessage), anyInt()))
                .thenReturn(List.of(buttonSchema));

        // Act
        String enhanced = promptEnhancer.enhancePrompt(userMessage, basePrompt);

        // Assert
        assertTrue(enhanced.contains("Examples:"));
        assertTrue(enhanced.contains("Save"));
        assertTrue(enhanced.contains("Cancel"));
    }
}
