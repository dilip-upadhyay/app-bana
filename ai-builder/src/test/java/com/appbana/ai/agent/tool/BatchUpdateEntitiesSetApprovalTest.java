package com.appbana.ai.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for the {@code set_approval} operation added to {@code batch_update_entities}.
 *
 * <p>Before this fix, there was no supported way to turn on maker-checker approval for an entity
 * that had already been created (via {@code create_entity}/{@code scaffold_app}) — only at
 * creation time. Asking the AI in chat to "enable approval on entity X" caused it to call
 * {@code update_fields} instead, which silently did nothing to the entity-level
 * {@code approvalRequired} flag (it only ever touches the {@code fields} array). Found live
 * while testing a freshly-scaffolded app: {@code GET /schema/{key}} confirmed
 * {@code approvalRequired} stayed {@code false} after the "fix" round-tripped through chat.
 *
 * <p>These tests only cover the parameter schema surface (what the LLM sees) since
 * {@link BatchUpdateEntitiesTool#execute} makes real HTTP calls to the backend and has no
 * existing seam for that in this test suite (see {@link BatchUpdateApprovalColumnGuardTest} for
 * why the removal-side guard is instead tested via the extracted static helper).
 */
class BatchUpdateEntitiesSetApprovalTest {

    private static final ObjectMapper M = new ObjectMapper();

    private static BatchUpdateEntitiesTool tool() {
        return new BatchUpdateEntitiesTool(null, "http://localhost:8080");
    }

    @Test
    void parameterSchemaDeclaresSetApprovalOperation() throws Exception {
        JsonNode schema = M.readTree(tool().getParameterSchema());
        JsonNode operationEnum = schema.path("properties").path("updates")
                .path("items").path("properties").path("operation").path("enum");

        boolean hasSetApproval = false;
        for (JsonNode n : operationEnum) {
            if ("set_approval".equals(n.asText())) {
                hasSetApproval = true;
            }
        }
        assertTrue(hasSetApproval, "batch_update_entities must expose a set_approval operation: " + operationEnum);
    }

    @Test
    void parameterSchemaDeclaresApprovalRequiredAsBoolean() throws Exception {
        JsonNode schema = M.readTree(tool().getParameterSchema());
        JsonNode approvalRequiredProp = schema.path("properties").path("updates")
                .path("items").path("properties").path("approvalRequired");

        assertEquals("boolean", approvalRequiredProp.path("type").asText(),
                "set_approval needs a boolean approvalRequired argument: " + schema);
    }
}
