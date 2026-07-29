package com.appbana.ai.knowledge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * C4.4 — the two maker-checker blueprints, tested at the layer their value is claimed for.
 *
 * <p>The claim is "the agent is shown what an approval-required app looks like". That is a claim
 * about the <b>prompt</b>, so asserting the loader holds the templates proves almost nothing: they
 * were held, indexed into Qdrant and retrievable for eight existing domains while their entity
 * structure never reached the model at all. {@code "domain-template"} has no
 * {@link SchemaDefinition.SchemaType} constant, so {@code getTypeAsEnum()} returned null and
 * {@code buildSchemaContext}'s grouping discarded every template before any render branch ran.
 *
 * <p>So {@link #retrievedApprovalBlueprintReachesThePromptWithItsEntitiesAndFlag()} is the test that
 * matters, and it goes through the public {@code enhancePrompt} entry point rather than the render
 * helper, so that a future change re-introducing a filter upstream of the helper still fails.
 */
@ExtendWith(MockitoExtension.class)
class ApprovalDomainTemplateTest {

    @Mock
    private KnowledgeBaseService knowledgeBaseService;

    private static SchemaDefinition template(String id, Map<String, String> entities,
                                             Collection<String> approvalEntities) {
        SchemaDefinition schema = new SchemaDefinition();
        schema.setId("domain_" + id);
        schema.setName(id);
        schema.setRawType("domain-template");
        schema.setCategory("domain-template");
        schema.setDescription("blueprint for " + id);
        schema.setExamples(List.of(id));

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("entities", entities);
        if (approvalEntities != null) {
            metadata.put("approvalRequiredEntities", approvalEntities);
        }
        schema.setMetadata(metadata);
        return schema;
    }

    // ---------------------------------------------------------------- loader

    @Test
    void loaderPublishesBothApprovalBlueprintsAsDomainTemplates() {
        AppBanaSchemaLoader loader = new AppBanaSchemaLoader();

        for (String id : List.of("domain_customer_onboarding_with_approval",
                                 "domain_loan_origination_with_approval")) {
            SchemaDefinition schema = loader.getSchema(id);
            assertNotNull(schema, id + " must be registered by loadDomainTemplates()");
            assertEquals("domain-template", schema.getCategory(),
                    "category is the filter both syncDomainTemplates() and the prompt renderer use — "
                            + "a template with any other category is silently invisible");
        }

        Object approval = loader.getSchema("domain_loan_origination_with_approval")
                .getMetadata().get("approvalRequiredEntities");
        assertInstanceOf(Collection.class, approval);
        assertTrue(((Collection<?>) approval).containsAll(List.of("LoanApplication", "Disbursement")),
                "both the application and the disbursement are maker-checker steps");
    }

    /**
     * The blueprints must NOT declare the eight approval columns as fields. `approvalRequired` is the
     * entire contract — SchemaManager materialises the columns from the flag (C4.6). A template that
     * declared them would still converge on the same physical table via dedupe, but it would teach
     * the agent a shape in which SchemaManager is not the owner, and that shape is what hid the
     * original defect across 281 green tests.
     */
    @Test
    void approvalBlueprintsDeclareTheFlagAndNoneOfTheApprovalColumns() {
        AppBanaSchemaLoader loader = new AppBanaSchemaLoader();
        List<String> reserved = List.of("approval_status", "approval_revision", "approval_parent_id",
                "submitted_by", "submitted_at", "approved_by", "approved_at", "rejection_reason");

        for (String id : List.of("domain_customer_onboarding_with_approval",
                                 "domain_loan_origination_with_approval")) {
            @SuppressWarnings("unchecked")
            Map<String, String> entities =
                    (Map<String, String>) loader.getSchema(id).getMetadata().get("entities");

            entities.forEach((entityName, fields) ->
                    reserved.forEach(column -> assertFalse(fields.contains(column),
                            id + "/" + entityName + " declares the platform-owned column '" + column
                                    + "'. Set approvalRequired and let SchemaManager create it.")));
        }
    }

    // ---------------------------------------------------------------- prompt

    @Test
    void retrievedApprovalBlueprintReachesThePromptWithItsEntitiesAndFlag() throws Exception {
        Map<String, String> entities = new LinkedHashMap<>();
        entities.put("LoanApplication", "applicant_name:text, loan_amount:decimal");
        entities.put("Disbursement", "application:reference->LoanApplication, disbursed_amount:decimal");

        // A List, not a Set — this metadata round-trips through JSON in the Qdrant payload, so a
        // renderer that pattern-matches on Set<String> would work in a unit test and fail in prod.
        SchemaDefinition blueprint =
                template("loan_origination_with_approval", entities, List.of("LoanApplication", "Disbursement"));

        when(knowledgeBaseService.searchRelevantSchemas(anyString(), anyInt()))
                .thenReturn(List.of(blueprint));

        AppBanaPromptEnhancer enhancer = new AppBanaPromptEnhancer(knowledgeBaseService);
        String prompt = enhancer.enhancePrompt(
                "scaffold a lending app where a credit officer signs off each loan", "BASE_PROMPT");

        assertAll("the blueprint must be legible to the model",
                () -> assertTrue(prompt.contains("LoanApplication"),
                        "entity names must reach the prompt"),
                () -> assertTrue(prompt.contains("applicant_name:text, loan_amount:decimal"),
                        "the field DSL is the actual example content — keywords alone taught nothing"),
                () -> assertTrue(prompt.contains("application:reference->LoanApplication"),
                        "relationships must survive too"),
                () -> assertTrue(prompt.contains("approvalRequired: true"),
                        "the agent has to be told the exact parameter name to pass to scaffold_app"),
                () -> assertTrue(prompt.contains("LoanApplication, Disbursement"),
                        "and which entities to pass it for"),
                () -> assertTrue(prompt.contains("BASE_PROMPT"),
                        "enhancement must not drop the caller's prompt"));
    }

    /**
     * Guards the instruction that keeps C4.6's ownership rule intact end to end: the model is told
     * to set the flag and explicitly told not to invent the columns.
     */
    @Test
    void promptTellsTheModelNotToDeclareTheApprovalColumnsItself() throws Exception {
        SchemaDefinition blueprint = template("customer_onboarding_with_approval",
                Map.of("CustomerApplication", "full_name:text"), List.of("CustomerApplication"));

        when(knowledgeBaseService.searchRelevantSchemas(anyString(), anyInt()))
                .thenReturn(List.of(blueprint));

        String prompt = new AppBanaPromptEnhancer(knowledgeBaseService)
                .enhancePrompt("scaffold a KYC onboarding app", "BASE");

        assertTrue(prompt.contains("Do NOT add approval_status"),
                "without this the model copies the columns into the schema, which is the shape "
                        + "C4.6 removed from SchemaEnricher");
    }

    @Test
    void aTemplateWithoutApprovalRendersItsEntitiesButNoApprovalInstruction() throws Exception {
        SchemaDefinition plain = template("ecommerce", Map.of("Product", "name:text, price:decimal"), null);

        when(knowledgeBaseService.searchRelevantSchemas(anyString(), anyInt()))
                .thenReturn(List.of(plain));

        String prompt = new AppBanaPromptEnhancer(knowledgeBaseService)
                .enhancePrompt("scaffold a spice shop", "BASE");

        assertTrue(prompt.contains("name:text, price:decimal"), "entities still render");
        assertFalse(prompt.contains("approvalRequired"),
                "a plain domain must not acquire an approval flow — this is the regression that "
                        + "would push maker-checker onto every app built from a blueprint");
    }

    @Test
    void nonTemplateSchemasProduceNoBlueprintSection() {
        SchemaDefinition field = new SchemaDefinition();
        field.setId("field_email");
        field.setName("email");
        field.setType(SchemaDefinition.SchemaType.ENTITY_FIELD);
        field.setCategory("schema");
        field.setDescription("Email with validation");

        String section = new AppBanaPromptEnhancer(knowledgeBaseService)
                .buildDomainTemplateSection(new ArrayList<>(List.of(field)));

        assertEquals("", section, "no blueprints retrieved means no section and no wasted tokens");
    }
}
