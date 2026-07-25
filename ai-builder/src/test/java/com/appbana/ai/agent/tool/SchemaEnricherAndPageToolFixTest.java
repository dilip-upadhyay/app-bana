package com.appbana.ai.agent.tool;

import com.appbana.ai.knowledge.MetadataValidator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Regression coverage for the two AI-Builder gaps fixed in commit 9cde9d9.
 *
 * <p><b>GAP 1</b> — {@link SchemaEnricher} must normalise every field name to
 * snake_case before the metadata reaches the backend, so that non-canonical
 * identifiers ("Full Name", "firstName") never create quoted DB columns that
 * later drift with schema evolution.
 *
 * <p><b>GAP 2</b> — {@link GeneratePageTool#buildFormPage} must propagate the
 * {@code referenceEntity} of each foreign-key field into the emitted input
 * node's props, so the runtime ReferenceField queries the correct target
 * entity even when the FK column is aliased (e.g. "owner" \u2192 User).
 */
class SchemaEnricherAndPageToolFixTest {

    // ---------- GAP 1: SchemaEnricher.enrich() name normalisation ----------

    @Test
    void enrich_normalisesFieldNamesToSnakeCase() {
        SchemaEnricher enricher = new SchemaEnricher();

        Map<String, Object> customerField = new LinkedHashMap<>();
        customerField.put("name", "Full Name");
        customerField.put("type", "text");

        Map<String, Object> camelField = new LinkedHashMap<>();
        camelField.put("name", "firstName");
        camelField.put("type", "text");
        camelField.put("label", "First Name"); // explicit label must be preserved

        Map<String, Object> hyphenField = new LinkedHashMap<>();
        hyphenField.put("name", "user-email");
        hyphenField.put("type", "email");

        Map<String, Object> alreadyOkField = new LinkedHashMap<>();
        alreadyOkField.put("name", "phone_number");
        alreadyOkField.put("type", "phone");

        Map<String, Object> entity = new LinkedHashMap<>();
        entity.put("name", "Customer");
        entity.put("fields", new ArrayList<>(List.of(
                customerField, camelField, hyphenField, alreadyOkField)));

        enricher.enrich(entity);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) entity.get("fields");

        // After enrich(), baseline fields (id, created_at, updated_at) are prepended.
        // The four LLM-provided fields end up at indices 3..6.
        assertEquals("full_name",    fields.get(3).get("name"),
                "Space-separated name should be normalised to snake_case");
        assertEquals("Full Name",    fields.get(3).get("label"),
                "Original human-readable name must be preserved as the label when none was provided");

        assertEquals("first_name",   fields.get(4).get("name"),
                "camelCase name should be normalised to snake_case");
        assertEquals("First Name",   fields.get(4).get("label"),
                "Explicit label must NOT be overwritten by the normaliser");

        assertEquals("user_email",   fields.get(5).get("name"),
                "Hyphenated name should be normalised to snake_case");

        assertEquals("phone_number", fields.get(6).get("name"),
                "Already-canonical name must be left untouched");
    }

    @Test
    void toSnakeCase_handlesCommonHumanForms() {
        assertEquals("full_name",     SchemaEnricher.toSnakeCase("Full Name"));
        assertEquals("first_name",    SchemaEnricher.toSnakeCase("firstName"));
        assertEquals("user_email",    SchemaEnricher.toSnakeCase("user-email"));
        assertEquals("start_date",    SchemaEnricher.toSnakeCase("Start Date"));
        assertEquals("phone_number",  SchemaEnricher.toSnakeCase("phone_number"));
        assertEquals("customer_id",   SchemaEnricher.toSnakeCase("CustomerID"));
        assertEquals("http_response", SchemaEnricher.toSnakeCase("HTTPResponse"));
        assertEquals("a_b_c",         SchemaEnricher.toSnakeCase("  a  b  c  "));
    }

    // ---------- GAP 2: GeneratePageTool.buildFormPage referenceEntity ----------

    @Test
    @SuppressWarnings("unchecked")
    void buildFormPage_propagatesReferenceEntityIntoInputProps() throws Exception {
        GeneratePageTool tool = new GeneratePageTool(mock(MetadataValidator.class), "http://unused");

        Map<String, Object> ownerField = new LinkedHashMap<>();
        ownerField.put("name", "owner");
        ownerField.put("type", "reference");
        ownerField.put("referenceEntity", "User"); // aliased FK: field != entity name
        ownerField.put("label", "Owner");

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("name", "AddTask");
        args.put("path", "/tasks/new");
        args.put("entityName", "Task");
        args.put("appId", "app-1");
        args.put("entityFields", List.of(ownerField));

        Method m = GeneratePageTool.class.getDeclaredMethod("buildFormPage", Map.class);
        m.setAccessible(true);
        Map<String, Object> page = (Map<String, Object>) m.invoke(tool, args);

        List<Map<String, Object>> nodes = (List<Map<String, Object>>) page.get("nodes");
        Map<String, Object> inputNode = nodes.stream()
                .filter(n -> "input".equals(n.get("type")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no input node emitted for the reference field"));

        Map<String, Object> inputProps = (Map<String, Object>) inputNode.get("props");
        assertEquals("reference", inputProps.get("type"),
                "Input type must be mapped to 'reference'");
        assertEquals("User", inputProps.get("referenceEntity"),
                "referenceEntity must be propagated from field into input props "
                        + "so the runtime ReferenceField queries the correct target entity");
        assertNull(inputProps.get("placeholder"),
                "Reference inputs must not carry a text placeholder");
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildFormPage_omitsReferenceEntityWhenAbsentOnField() throws Exception {
        GeneratePageTool tool = new GeneratePageTool(mock(MetadataValidator.class), "http://unused");

        Map<String, Object> field = new LinkedHashMap<>();
        field.put("name", "customer");
        field.put("type", "reference"); // no referenceEntity — legacy fallback path

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("name", "AddOnboarding");
        args.put("path", "/onboarding/new");
        args.put("entityName", "OnboardingProcess");
        args.put("appId", "app-1");
        args.put("entityFields", List.of(field));

        Method m = GeneratePageTool.class.getDeclaredMethod("buildFormPage", Map.class);
        m.setAccessible(true);
        Map<String, Object> page = (Map<String, Object>) m.invoke(tool, args);

        Map<String, Object> inputNode = ((List<Map<String, Object>>) page.get("nodes")).stream()
                .filter(n -> "input".equals(n.get("type")))
                .findFirst()
                .orElseThrow();

        Map<String, Object> inputProps = (Map<String, Object>) inputNode.get("props");
        assertFalse(inputProps.containsKey("referenceEntity"),
                "When the field lacks referenceEntity, the input node must not carry a stale key");
    }
}
