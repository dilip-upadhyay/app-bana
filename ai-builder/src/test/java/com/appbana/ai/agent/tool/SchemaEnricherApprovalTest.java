package com.appbana.ai.agent.tool;

import com.appbana.ai.agent.tool.SchemaEnricher;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class SchemaEnricherApprovalTest {

    @Test
    @SuppressWarnings("unchecked")
    public void testApprovalColumnsInjectedWhenRequired() {
        SchemaEnricher enricher = new SchemaEnricher();

        Map<String, Object> entity = new LinkedHashMap<>();
        entity.put("name", "Customer");
        entity.put("approvalRequired", true);
        List<Map<String, Object>> fields = new ArrayList<>();
        entity.put("fields", fields);

        enricher.enrich(entity);

        List<Map<String, Object>> enrichedFields = (List<Map<String, Object>>) entity.get("fields");
        assertNotNull(enrichedFields);

        List<String> names = enrichedFields.stream()
                .map(f -> (String) f.get("name"))
                .toList();

        assertTrue(names.contains("approval_status"));
        assertTrue(names.contains("approval_revision"));
        assertTrue(names.contains("approval_parent_id"));
        assertTrue(names.contains("submitted_by"));
        assertTrue(names.contains("submitted_at"));
        assertTrue(names.contains("approved_by"));
        assertTrue(names.contains("approved_at"));
        assertTrue(names.contains("rejection_reason"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testApprovalColumnsNotInjectedWhenNotRequired() {
        SchemaEnricher enricher = new SchemaEnricher();

        Map<String, Object> entity = new LinkedHashMap<>();
        entity.put("name", "Product");
        entity.put("approvalRequired", false);
        List<Map<String, Object>> fields = new ArrayList<>();
        entity.put("fields", fields);

        enricher.enrich(entity);

        List<Map<String, Object>> enrichedFields = (List<Map<String, Object>>) entity.get("fields");
        assertNotNull(enrichedFields);

        List<String> names = enrichedFields.stream()
                .map(f -> (String) f.get("name"))
                .toList();

        assertFalse(names.contains("approval_status"));
        assertFalse(names.contains("approval_revision"));
    }

    /**
     * Task C4.3 — a user/LLM-authored field that squats on a reserved approval
     * column name must be renamed out of the way, and the canonical definition
     * injected anyway. Before C4.3 the collision made injection SKIP that column,
     * so the entity was flagged approvalRequired but carried an approval_status
     * the state machine could not drive (wrong type, wrong option set).
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testUserAuthoredApprovalStatusIsRenamedAndCanonicalOneWins() {
        SchemaEnricher enricher = new SchemaEnricher();

        Map<String, Object> entity = new LinkedHashMap<>();
        entity.put("name", "LoanApplication");
        entity.put("approvalRequired", true);
        List<Map<String, Object>> fields = new ArrayList<>();
        Map<String, Object> squatter = new LinkedHashMap<>();
        squatter.put("id", "approval_status");
        squatter.put("name", "approval_status");
        squatter.put("type", "text");
        squatter.put("label", "Approval Status");
        squatter.put("required", false);
        squatter.put("options", new ArrayList<>(List.of("Yes", "No")));
        fields.add(squatter);
        entity.put("fields", fields);

        enricher.enrich(entity);

        List<Map<String, Object>> enriched = (List<Map<String, Object>>) entity.get("fields");
        List<String> names = enriched.stream().map(f -> (String) f.get("name")).toList();

        assertTrue(names.contains("workflow_approval_status"),
                "colliding user field is renamed, not dropped: " + names);
        assertEquals(1, names.stream().filter("approval_status"::equals).count(),
                "exactly one approval_status definition survives: " + names);

        Map<String, Object> canonical = enriched.stream()
                .filter(f -> "approval_status".equals(f.get("name")))
                .findFirst().orElseThrow();
        assertEquals("status", canonical.get("type"), "canonical definition wins, not the LLM's 'text'");
        assertEquals(List.of("DRAFT", "PENDING", "APPROVED", "REJECTED"), canonical.get("options"));
    }

    /**
     * Task C4.3 — the rename must not collide with a field the entity already has.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testRenameAvoidsAnExistingWorkflowPrefixedField() {
        SchemaEnricher enricher = new SchemaEnricher();

        Map<String, Object> entity = new LinkedHashMap<>();
        entity.put("name", "ExpenseClaim");
        entity.put("approvalRequired", true);
        List<Map<String, Object>> fields = new ArrayList<>();
        fields.add(namedField("workflow_submitted_by"));
        fields.add(namedField("submitted_by"));
        entity.put("fields", fields);

        enricher.enrich(entity);

        List<Map<String, Object>> enriched = (List<Map<String, Object>>) entity.get("fields");
        List<String> names = enriched.stream().map(f -> (String) f.get("name")).toList();

        assertTrue(names.contains("workflow_submitted_by_2"),
                "rename must not clobber the pre-existing workflow_submitted_by: " + names);
        assertEquals(1, names.stream().filter("workflow_submitted_by"::equals).count());
        assertEquals(1, names.stream().filter("submitted_by"::equals).count());
        assertEquals(names.size(), names.stream().distinct().count(), "no duplicate field names: " + names);
    }

    private static Map<String, Object> namedField(String name) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("id", name);
        f.put("name", name);
        f.put("type", "text");
        f.put("label", name);
        f.put("required", false);
        return f;
    }
}
