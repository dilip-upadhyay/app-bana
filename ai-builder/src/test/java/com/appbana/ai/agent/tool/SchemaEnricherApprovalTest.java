package com.appbana.approval;

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
}
