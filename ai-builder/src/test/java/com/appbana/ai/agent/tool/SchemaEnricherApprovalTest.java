package com.appbana.ai.agent.tool;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task C4.6 — the enricher must NOT produce approval columns.
 *
 * <p>This class previously asserted the opposite. Injection lived here (C1.2) and was
 * hardened here (C4.3), but ai-builder is only one of four writers of
 * {@code approvalRequired}, so the invariant "flag set ⟹ the eight columns exist" held
 * only for entities that happened to arrive via {@code scaffold_app}. C4.1 made the flag
 * reachable from {@code create_entity} too, which turned that gap into a live defect:
 * a table with no approval columns that accepts inserts and then 500s on submit/approve.
 *
 * <p>{@code SchemaManager} now owns injection on both the create and alter paths, keyed
 * on {@code schema.isApprovalRequired()}, so the flag alone is sufficient for every
 * writer present and future. These tests pin the enricher's side of that split: it
 * forwards the flag and touches nothing else.
 */
class SchemaEnricherApprovalTest {

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> enrichedFields(Map<String, Object> entity) {
        new SchemaEnricher().enrich(entity);
        List<Map<String, Object>> fields = (List<Map<String, Object>>) entity.get("fields");
        assertNotNull(fields);
        return fields;
    }

    private static List<String> names(List<Map<String, Object>> fields) {
        return fields.stream().map(f -> (String) f.get("name")).toList();
    }

    private static Map<String, Object> entity(String name, Object approvalRequired,
            List<Map<String, Object>> fields) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("name", name);
        if (approvalRequired != null) {
            e.put("approvalRequired", approvalRequired);
        }
        e.put("fields", fields);
        return e;
    }

    private static Map<String, Object> field(String name, String type) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("id", name);
        f.put("name", name);
        f.put("type", type);
        f.put("label", name);
        f.put("required", false);
        return f;
    }

    @Test
    void enricherDoesNotInjectApprovalColumnsEvenWhenFlagIsSet() {
        List<String> produced = names(enrichedFields(
                entity("Customer", true, new ArrayList<>(List.of(field("full_name", "text"))))));

        for (String reserved : SchemaEnricher.RESERVED_APPROVAL_COLUMNS) {
            assertFalse(produced.contains(reserved),
                    "SchemaManager owns approval columns now; enricher must not emit " + reserved
                            + ". Produced: " + produced);
        }
    }

    @Test
    void theFlagItselfSurvivesEnrichment() {
        // The enricher is a pass-through for the flag: CreateEntityTool forwards it
        // (C4.1) and SchemaManager acts on it (C4.6). If enrichment dropped it, the
        // backend would never see it and the columns would never be created.
        Map<String, Object> e = entity("LoanApplication", true,
                new ArrayList<>(List.of(field("amount", "decimal"))));
        enrichedFields(e);
        assertEquals(Boolean.TRUE, e.get("approvalRequired"));
    }

    /**
     * The C4.4 regression this closes. Those RAG templates will show the agent what an
     * approval entity looks like, and the natural way to do that is to list the eight
     * columns. C4.3's unconditional-inject-and-rename would have turned every such
     * entity into eight {@code workflow_*} junk columns plus eight real ones. Deleting
     * injection makes a template that declares them a harmless no-op on this side, and
     * the backend dedupes against declared names.
     */
    @Test
    void declaredApprovalColumnsArePassedThroughUntouched() {
        List<Map<String, Object>> declared = new ArrayList<>();
        declared.add(field("id", "number"));
        for (String reserved : List.of("approval_status", "submitted_by", "approved_at")) {
            declared.add(field(reserved, "text"));
        }

        List<String> produced = names(enrichedFields(entity("Onboarding", true, declared)));

        for (String reserved : List.of("approval_status", "submitted_by", "approved_at")) {
            assertEquals(1, produced.stream().filter(reserved::equals).count(),
                    "declared approval column kept exactly once, not duplicated or renamed: " + produced);
        }
        assertTrue(produced.stream().noneMatch(n -> n.startsWith("workflow_")),
                "no workflow_* renames survive C4.6: " + produced);
    }

    @Test
    void nonApprovalEntitiesAreUnaffected() {
        List<String> produced = names(enrichedFields(
                entity("Product", false, new ArrayList<>(List.of(field("title", "text"))))));

        for (String reserved : SchemaEnricher.RESERVED_APPROVAL_COLUMNS) {
            assertFalse(produced.contains(reserved), produced.toString());
        }
    }
}
