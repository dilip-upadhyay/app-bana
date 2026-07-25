package com.appbana.ai.knowledge;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MetadataValidator
 * Story 7.4: Metadata Validation Service
 */
class MetadataValidatorTest {

    private MetadataValidator validator;
    private AppBanaSchemaLoader schemaLoader;

    @BeforeEach
    void setUp() {
        schemaLoader = new AppBanaSchemaLoader();
        validator = new MetadataValidator(schemaLoader);
    }

    @Test
    void testValidateEntity_Valid() {
        // Arrange
        Map<String, Object> entity = new HashMap<>();
        entity.put("id", "customer");
        entity.put("name", "Customer");
        entity.put("displayName", "Customer");

        List<Map<String, Object>> fields = new ArrayList<>();
        Map<String, Object> field = new HashMap<>();
        field.put("id", "email");
        field.put("name", "email");
        field.put("type", "email");
        field.put("required", true);
        fields.add(field);

        entity.put("fields", fields);

        // Act
        ValidationResult result = validator.validateEntity(entity);

        // Assert
        assertTrue(result.isValid());
        assertFalse(result.hasErrors());
        assertEquals("Validation passed with no issues", result.getSummary());
    }

    @Test
    void testValidateEntity_MissingRequiredFields() {
        // Arrange
        Map<String, Object> entity = new HashMap<>();
        entity.put("id", "customer");
        // Missing name, displayName, fields

        // Act
        ValidationResult result = validator.validateEntity(entity);

        // Assert
        assertFalse(result.isValid());
        assertTrue(result.hasErrors());
        assertEquals(3, result.getErrors().size());
        assertTrue(result.getDetailedErrors().contains("name"));
        assertTrue(result.getDetailedErrors().contains("displayName"));
        assertTrue(result.getDetailedErrors().contains("fields"));
    }

    @Test
    void testValidateEntity_InvalidFieldType() {
        // Arrange
        Map<String, Object> entity = createBasicEntity();

        List<Map<String, Object>> fields = new ArrayList<>();
        Map<String, Object> field = new HashMap<>();
        field.put("id", "field1");
        field.put("name", "field1");
        field.put("type", "invalid_type");
        field.put("required", true);
        fields.add(field);

        entity.put("fields", fields);

        // Act
        ValidationResult result = validator.validateEntity(entity);

        // Assert
        assertFalse(result.isValid());
        assertTrue(result.hasErrors());
        assertTrue(result.getDetailedErrors().contains("Unknown field type"));
    }

    @Test
    void testValidateEntity_ReferenceFieldMissingEntity() {
        // Arrange
        Map<String, Object> entity = createBasicEntity();

        List<Map<String, Object>> fields = new ArrayList<>();
        Map<String, Object> field = new HashMap<>();
        field.put("id", "customerId");
        field.put("name", "customerId");
        field.put("type", "reference");
        field.put("required", true);
        // Missing referenceEntity
        fields.add(field);

        entity.put("fields", fields);

        // Act
        ValidationResult result = validator.validateEntity(entity);

        // Assert
        assertFalse(result.isValid());
        assertTrue(result.hasErrors());
        assertTrue(result.getDetailedErrors().contains("referenceEntity"));
    }

    @Test
    void testValidateEntity_SelectionFieldMissingOptions() {
        // Arrange
        Map<String, Object> entity = createBasicEntity();

        List<Map<String, Object>> fields = new ArrayList<>();
        Map<String, Object> field = new HashMap<>();
        field.put("id", "status");
        field.put("name", "status");
        field.put("type", "status");
        field.put("required", true);
        // Missing options
        fields.add(field);

        entity.put("fields", fields);

        // Act
        ValidationResult result = validator.validateEntity(entity);

        // Assert
        assertTrue(result.isValid()); // Valid but has warning
        assertTrue(result.hasWarnings());
        assertTrue(result.getDetailedWarnings().contains("options"));
    }

    @Test
    void testValidatePage_Valid() {
        // Arrange
        Map<String, Object> page = new HashMap<>();
        page.put("id", "customer-form");
        page.put("name", "CustomerForm");
        page.put("path", "/customers/form");
        page.put("rootId", "root");

        List<Map<String, Object>> nodes = new ArrayList<>();
        Map<String, Object> node = new HashMap<>();
        node.put("id", "input1");
        node.put("type", "input");
        nodes.add(node);

        page.put("nodes", nodes);

        // Act
        ValidationResult result = validator.validatePage(page);

        // Assert
        assertTrue(result.isValid());
        assertFalse(result.hasErrors());
    }

    @Test
    void testValidatePage_MissingRequiredFields() {
        // Arrange
        Map<String, Object> page = new HashMap<>();
        page.put("id", "page1");
        // Missing name, path, rootId, nodes

        // Act
        ValidationResult result = validator.validatePage(page);

        // Assert
        assertFalse(result.isValid());
        assertTrue(result.hasErrors());
        assertEquals(4, result.getErrors().size());
    }

    @Test
    void testValidatePage_InvalidComponentType() {
        // Arrange
        Map<String, Object> page = createBasicPage();

        List<Map<String, Object>> nodes = new ArrayList<>();
        Map<String, Object> node = new HashMap<>();
        node.put("id", "node1");
        node.put("type", "invalid_component");
        nodes.add(node);

        page.put("nodes", nodes);

        // Act
        ValidationResult result = validator.validatePage(page);

        // Assert
        assertFalse(result.isValid());
        assertTrue(result.hasErrors());
        assertTrue(result.getDetailedErrors().contains("Unknown component type"));
    }

    @Test
    void testValidatePage_InputMissingBindingProps() {
        // Arrange
        Map<String, Object> page = createBasicPage();

        List<Map<String, Object>> nodes = new ArrayList<>();
        Map<String, Object> node = new HashMap<>();
        node.put("id", "input1");
        node.put("type", "input");
        node.put("props", new HashMap<>()); // Empty props
        nodes.add(node);

        page.put("nodes", nodes);

        // Act
        ValidationResult result = validator.validatePage(page);

        // Assert
        assertTrue(result.isValid()); // Valid but has warning
        assertTrue(result.hasWarnings());
        assertTrue(result.getDetailedWarnings().contains("entity"));
    }

    @Test
    void testAutoFix_FieldTypeTypo() {
        // Arrange
        Map<String, Object> entity = createBasicEntity();

        List<Map<String, Object>> fields = new ArrayList<>();
        Map<String, Object> field = new HashMap<>();
        field.put("id", "email");
        field.put("name", "email");
        field.put("type", "emails"); // Typo
        field.put("required", true);
        fields.add(field);

        entity.put("fields", fields);

        ValidationResult validationResult = new ValidationResult();

        // Act
        Map<String, Object> fixed = validator.autoFix(entity, validationResult);

        // Assert
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fixedFields = (List<Map<String, Object>>) fixed.get("fields");
        assertEquals("email", fixedFields.get(0).get("type"));
    }

    @Test
    void testAutoFix_MissingIds() {
        // Arrange
        Map<String, Object> entity = createBasicEntity();

        List<Map<String, Object>> fields = new ArrayList<>();
        Map<String, Object> field = new HashMap<>();
        // Missing id
        field.put("name", "email");
        field.put("type", "email");
        field.put("required", true);
        fields.add(field);

        entity.put("fields", fields);

        ValidationResult validationResult = new ValidationResult();

        // Act
        Map<String, Object> fixed = validator.autoFix(entity, validationResult);

        // Assert
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fixedFields = (List<Map<String, Object>>) fixed.get("fields");
        assertNotNull(fixedFields.get(0).get("id"));
    }

    @Test
    void testAutoFix_MissingLabels() {
        // Arrange
        Map<String, Object> entity = createBasicEntity();

        List<Map<String, Object>> fields = new ArrayList<>();
        Map<String, Object> field = new HashMap<>();
        field.put("id", "firstName");
        field.put("name", "firstName");
        field.put("type", "text");
        field.put("required", true);
        // Missing label
        fields.add(field);

        entity.put("fields", fields);

        ValidationResult validationResult = new ValidationResult();

        // Act
        Map<String, Object> fixed = validator.autoFix(entity, validationResult);

        // Assert
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fixedFields = (List<Map<String, Object>>) fixed.get("fields");
        assertEquals("First Name", fixedFields.get(0).get("label"));
    }

    /**
     * Plan Stage 0.1 — node ids must survive regeneration.
     * Verifies that auto-fixing the same input twice produces identical ids
     * (i.e. UUID.randomUUID() has been replaced with a deterministic scheme).
     */
    @Test
    void testAutoFix_NodeIdsAreDeterministic() {
        Map<String, Object> page1 = new HashMap<>();
        Map<String, Object> page2 = new HashMap<>();

        List<Map<String, Object>> nodes1 = new ArrayList<>();
        List<Map<String, Object>> nodes2 = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Map<String, Object> n1 = new HashMap<>();
            n1.put("type", i == 0 ? "container" : "input");
            n1.put("name", "field" + i);
            nodes1.add(n1);
            Map<String, Object> n2 = new HashMap<>();
            n2.put("type", i == 0 ? "container" : "input");
            n2.put("name", "field" + i);
            nodes2.add(n2);
        }
        page1.put("nodes", nodes1);
        page2.put("nodes", nodes2);

        Map<String, Object> fixed1 = validator.autoFix(page1, new ValidationResult());
        Map<String, Object> fixed2 = validator.autoFix(page2, new ValidationResult());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> f1 = (List<Map<String, Object>>) fixed1.get("nodes");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> f2 = (List<Map<String, Object>>) fixed2.get("nodes");

        for (int i = 0; i < 3; i++) {
            String id1 = (String) f1.get(i).get("id");
            String id2 = (String) f2.get(i).get("id");
            assertNotNull(id1, "Node " + i + " missing id");
            assertEquals(id1, id2, "Node " + i + " id must be identical across regenerations");
            assertTrue(id1.startsWith("n_"), "Deterministic id should start with 'n_' prefix, got: " + id1);
        }
    }

    @Test
    void testAutoFix_FieldIdsDeriveFromName() {
        Map<String, Object> entity = createBasicEntity();

        List<Map<String, Object>> fields = new ArrayList<>();
        Map<String, Object> field = new HashMap<>();
        field.put("name", "customerEmail");
        field.put("type", "email");
        fields.add(field);
        entity.put("fields", fields);

        // Auto-fix twice — ids must be stable and derived from the field name.
        Map<String, Object> fixedA = validator.autoFix(entity, new ValidationResult());
        Map<String, Object> fixedB = validator.autoFix(entity, new ValidationResult());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fa = (List<Map<String, Object>>) fixedA.get("fields");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fb = (List<Map<String, Object>>) fixedB.get("fields");

        String idA = (String) fa.get(0).get("id");
        String idB = (String) fb.get(0).get("id");
        assertEquals(idA, idB, "Field id must be stable across regenerations");
        assertEquals("f_customeremail", idA, "Field id should derive from lowercased name");
    }

    // Helper methods

    private Map<String, Object> createBasicEntity() {
        Map<String, Object> entity = new HashMap<>();
        entity.put("id", "test");
        entity.put("name", "Test");
        entity.put("displayName", "Test Entity");
        return entity;
    }

    private Map<String, Object> createBasicPage() {
        Map<String, Object> page = new HashMap<>();
        page.put("id", "test-page");
        page.put("name", "TestPage");
        page.put("path", "/test");
        page.put("rootId", "root");
        return page;
    }
}
