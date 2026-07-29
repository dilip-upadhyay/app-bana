package com.appbana.ai.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task C4.1 — {@code approvalRequired} must survive the whole way from the LLM's
 * tool call into the body POSTed to the backend's {@code /schema} endpoint.
 *
 * <p>Before C4.1 it did not. {@code SchemaEnricher} read the flag and injected the
 * 8 approval columns, so the physical table came out approval-shaped, but
 * {@code CreateEntityTool.buildEntityMetadata} — which constructs the ENTIRE POST
 * body and therefore silently drops anything it does not explicitly copy — never
 * forwarded the flag itself. The schema record landed with
 * {@code approvalRequired=false}, and every backend guard branches on
 * {@code schema.isApprovalRequired()} rather than on the presence of the columns.
 * Net effect: an entity that looked approval-enabled and behaved as if it were not.
 */
class CreateEntityToolApprovalTest {

    private static final ObjectMapper M = new ObjectMapper();

    // Both methods under test are pure payload/schema construction and never touch
    // the validator, so a null one keeps these tests off the RAG corpus and fast.
    private static CreateEntityTool tool() {
        return new CreateEntityTool(null, "http://localhost:8080");
    }

    private static Map<String, Object> args(String name, Object approvalRequired) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("name", name);
        Map<String, Object> id = new LinkedHashMap<>();
        id.put("name", "id");
        id.put("type", "integer");
        id.put("required", true);
        a.put("fields", List.of(id));
        if (approvalRequired != null) {
            a.put("approvalRequired", approvalRequired);
        }
        return a;
    }

    @Test
    void approvalRequiredTrueSurvivesIntoTheSchemaPayload() {
        Map<String, Object> body = tool().buildEntityMetadata(args("LoanApplication", true));
        assertEquals(Boolean.TRUE, body.get("approvalRequired"),
                "the flag the backend branches on must reach the backend: " + body);
    }

    @Test
    void approvalRequiredIsOmittedWhenNotRequested() {
        // Deliberately absent rather than false: a non-approval entity must keep
        // exactly the payload shape it had before C4.1, so this change cannot
        // alter behaviour for the 100% of existing entities that don't use it.
        assertFalse(tool().buildEntityMetadata(args("Product", null)).containsKey("approvalRequired"));
        assertFalse(tool().buildEntityMetadata(args("Product", false)).containsKey("approvalRequired"));
    }

    @Test
    void approvalRequiredIsNotForwardedFromANonBooleanValue() {
        // The LLM emits JSON; a stringly-typed "true" must not silently enable a
        // workflow the user never agreed to.
        assertFalse(tool().buildEntityMetadata(args("Product", "true")).containsKey("approvalRequired"));
    }

    @Test
    void bothToolParameterSchemasAreValidJsonAndDeclareApprovalRequired() throws Exception {
        // These schemas are raw text blocks handed to the LLM. If one is malformed
        // or silently loses the property, the model can never emit the flag and the
        // whole C4 feature is unreachable from chat — with no error anywhere.
        JsonNode createEntity = M.readTree(tool().getParameterSchema());
        JsonNode entityProp = createEntity.path("properties").path("approvalRequired");
        assertEquals("boolean", entityProp.path("type").asText(),
                "create_entity must expose approvalRequired: " + createEntity.path("properties").fieldNames());

        JsonNode scaffold = M.readTree(
                new ScaffoldAppTool(null, "http://localhost:8080").getParameterSchema());
        JsonNode scaffoldProp = scaffold.path("properties").path("entities")
                .path("items").path("properties").path("approvalRequired");
        assertEquals("boolean", scaffoldProp.path("type").asText(),
                "scaffold_app must expose approvalRequired per entity: " + scaffold.toString());

        // The description is what actually steers the model. Assert it carries the
        // decision rule, not just the type, so a future edit that guts it fails here.
        for (JsonNode p : List.of(entityProp, scaffoldProp)) {
            String desc = p.path("description").asText();
            assertTrue(desc.contains("maker-checker"), "description names the workflow: " + desc);
            assertTrue(desc.contains("DIFFERENT user"), "description states separation of duties: " + desc);
            assertTrue(desc.contains("Do NOT"), "description states when NOT to set it: " + desc);
        }
    }
}
