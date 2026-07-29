package com.appbana.ai.knowledge;

import com.appbana.ai.agent.AgentConfig;
import com.appbana.ai.agent.AgentContext;
import com.appbana.ai.agent.AiAgent;
import com.appbana.ai.agent.tool.ToolRegistry;
import com.appbana.ai.llm.LlmRegistry;
import com.appbana.ai.llm.LlmService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * C4.4 — the two maker-checker blueprints, tested against the prompt the LLM actually receives.
 *
 * <p><b>Why this class drives {@link AiAgent} and not {@code AppBanaPromptEnhancer.enhancePrompt}:</b>
 * the first version of these tests drove {@code enhancePrompt}, on the reasoning that it is the
 * public entry point of the class that renders the prompt. It is — and nothing calls it.
 * {@code enhancePrompt}'s only caller is {@code AdvancedPromptEngine.buildPrompt}, which has zero
 * call sites repo-wide; {@code AiChatController} takes the engine as a constructor parameter it does
 * not even store. The layer was chosen by reading the class instead of grepping for the caller, so
 * six tests and a mutation check all validated a chain production never executes.
 *
 * <p>The live chain is {@code AiAgent.think()} → {@code llmService.chatWithJsonMode(prompt)}. These
 * tests capture that exact string, which is the narrowest statement of the claim "the agent is shown
 * what an approval-required app looks like" and cannot be satisfied by a dead branch.
 */
class ApprovalDomainTemplateTest {

    private static final String LLM_STOP_RESPONSE =
            "{\"thinking\": \"monologue\", \"final_answer\": \"done\"}";
    private static final String DOMAIN_TEMPLATE = "domain-template";
    private static final String ONBOARDING = "domain_customer_onboarding_with_approval";
    private static final String LOAN = "domain_loan_origination_with_approval";

    @Mock
    private KnowledgeBaseService knowledgeBaseService;

    @Mock
    private LlmRegistry llmRegistry;

    @Mock
    private LlmService llmService;

    private AiAgent agent;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        AgentConfig config = AgentConfig.defaults();
        config.setDebugMode(false);

        // process(msg, ctx) delegates with provider = null, and anyString() does not match null.
        when(llmRegistry.getService(nullable(String.class))).thenReturn(llmService);

        agent = new AiAgent(llmRegistry, new ToolRegistry(), config)
                .withKnowledgeBase(knowledgeBaseService);
        // The pattern executor short-circuits before any LLM call; these tests assert on the prompt.
        agent.setPatternMatchingEnabled(false);
    }

    private static SchemaDefinition template(String id, Map<String, String> entities,
                                             Collection<String> approvalEntities) {
        SchemaDefinition schema = new SchemaDefinition();
        schema.setId("domain_" + id);
        schema.setName(id);
        schema.setRawType(DOMAIN_TEMPLATE);
        schema.setCategory(DOMAIN_TEMPLATE);
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

    /** Runs one agent turn and returns the prompt string handed to the LLM. */
    private String promptFor(String userMessage, List<SchemaDefinition> retrieved) throws Exception {
        when(knowledgeBaseService.getDomainExamples(anyString(), anyInt())).thenReturn(retrieved);
        when(llmService.chatWithJsonMode(anyString())).thenReturn(LLM_STOP_RESPONSE);

        agent.process(userMessage,
                AgentContext.create("tenant1", "app1", "user1", "session1", "test-token"));

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmService).chatWithJsonMode(promptCaptor.capture());
        return promptCaptor.getValue();
    }

    // ---------------------------------------------------------------- loader

    @Test
    void loaderPublishesBothApprovalBlueprintsAsDomainTemplates() {
        AppBanaSchemaLoader loader = new AppBanaSchemaLoader();

        for (String id : List.of(ONBOARDING, LOAN)) {
            SchemaDefinition schema = loader.getSchema(id);
            assertNotNull(schema, id + " must be registered by loadDomainTemplates()");
            assertEquals(DOMAIN_TEMPLATE, schema.getCategory(),
                    "category is the filter syncDomainTemplates(), the Qdrant payload filter in "
                            + "getDomainExamples() and the renderer all key on — a template with any "
                            + "other category is silently invisible");
        }

        Object approval = loader.getSchema(LOAN).getMetadata().get("approvalRequiredEntities");
        assertInstanceOf(Collection.class, approval);
        assertTrue(((Collection<?>) approval).containsAll(List.of("LoanApplication", "Disbursement")),
                "both the application and the disbursement are maker-checker steps");
    }

    /**
     * The blueprints must NOT declare the eight approval columns as fields. {@code approvalRequired}
     * is the entire contract — SchemaManager materialises the columns from the flag (C4.6). A
     * template that declared them would still converge on the same physical table via dedupe, but it
     * would teach the agent a shape in which SchemaManager is not the owner, and that shape is what
     * hid the original defect across 281 green tests.
     */
    @Test
    void approvalBlueprintsDeclareTheFlagAndNoneOfTheApprovalColumns() {
        AppBanaSchemaLoader loader = new AppBanaSchemaLoader();
        List<String> reserved = List.of("approval_status", "approval_revision", "approval_parent_id",
                "submitted_by", "submitted_at", "approved_by", "approved_at", "rejection_reason");

        for (String id : List.of(ONBOARDING, LOAN)) {
            @SuppressWarnings("unchecked")
            Map<String, String> entities =
                    (Map<String, String>) loader.getSchema(id).getMetadata().get("entities");

            entities.forEach((entityName, fields) ->
                    reserved.forEach(column -> assertFalse(fields.contains(column),
                            id + "/" + entityName + " declares the platform-owned column '" + column
                                    + "'. Set approvalRequired and let SchemaManager create it.")));
        }
    }

    // ------------------------------------------------- the live agent prompt

    @Test
    void retrievedApprovalBlueprintReachesTheAgentPromptWithItsEntitiesAndFlag() throws Exception {
        Map<String, String> entities = new LinkedHashMap<>();
        entities.put("LoanApplication", "applicant_name:text, loan_amount:decimal");
        entities.put("Disbursement", "application:reference->LoanApplication, disbursed_amount:decimal");

        // A List, not a Set — this metadata round-trips through JSON in the Qdrant payload, so a
        // renderer that pattern-matches on Set<String> would pass here and fail in production.
        SchemaDefinition blueprint = template("loan_origination_with_approval", entities,
                List.of("LoanApplication", "Disbursement"));

        String prompt = promptFor(
                "scaffold a lending app where a credit officer signs off each loan", List.of(blueprint));

        assertAll("the blueprint must be legible to the model",
                () -> assertTrue(prompt.contains("SIMILAR APP BLUEPRINTS"),
                        "the section must be labelled in the agent prompt, not merely concatenated"),
                () -> assertTrue(prompt.contains("LoanApplication"),
                        "entity names must reach the prompt"),
                () -> assertTrue(prompt.contains("applicant_name:text, loan_amount:decimal"),
                        "the field DSL is the actual example content — keywords alone taught nothing"),
                () -> assertTrue(prompt.contains("application:reference->LoanApplication"),
                        "relationships must survive too"),
                () -> assertTrue(prompt.contains("approvalRequired: true"),
                        "the agent has to be told the exact parameter name to pass to scaffold_app"),
                () -> assertTrue(prompt.contains("LoanApplication, Disbursement"),
                        "and which entities to pass it for"));
    }

    /**
     * Guards the instruction that keeps C4.6's ownership rule intact end to end: the model is told to
     * set the flag and explicitly told not to invent the columns.
     */
    @Test
    void agentPromptTellsTheModelNotToDeclareTheApprovalColumnsItself() throws Exception {
        SchemaDefinition blueprint = template("customer_onboarding_with_approval",
                Map.of("CustomerApplication", "full_name:text"), List.of("CustomerApplication"));

        String prompt = promptFor("scaffold a KYC onboarding app", List.of(blueprint));

        assertTrue(prompt.contains("Do NOT add approval_status"),
                "without this the model copies the columns into the schema, which is the shape "
                        + "C4.6 removed from SchemaEnricher");
    }

    @Test
    void aTemplateWithoutApprovalRendersItsEntitiesButNoApprovalInstruction() throws Exception {
        SchemaDefinition plain = template("ecommerce", Map.of("Product", "name:text, price:decimal"), null);

        String prompt = promptFor("scaffold a spice shop", List.of(plain));

        assertTrue(prompt.contains("name:text, price:decimal"), "entities still render");
        assertFalse(prompt.contains("approvalRequired"),
                "a plain domain must not acquire an approval flow — this is the regression that "
                        + "would push maker-checker onto every app built from a blueprint");
    }

    @Test
    void noRetrievedBlueprintsMeansNoSectionInTheAgentPrompt() throws Exception {
        String prompt = promptFor("scaffold something ordinary", List.of());

        assertFalse(prompt.contains("SIMILAR APP BLUEPRINTS"),
                "an empty retrieval must not emit a heading with nothing under it");
    }

    // ------------------------------------------------------------- rendering

    @Test
    void nonTemplateSchemasProduceNoBlueprintSection() {
        SchemaDefinition field = new SchemaDefinition();
        field.setId("field_email");
        field.setName("email");
        field.setType(SchemaDefinition.SchemaType.ENTITY_FIELD);
        field.setCategory("schema");
        field.setDescription("Email with validation");

        assertEquals("", DomainBlueprintPrompt.render(new ArrayList<>(List.of(field))),
                "no blueprints retrieved means no section and no wasted tokens");
    }

    /**
     * Round-4 nit: the renderer used to append the raw metadata value, so a non-String would emit
     * Java's {@code {a=b}} map syntax into the prompt as if it were the field DSL.
     */
    @Test
    void aMalformedEntityValueIsSkippedRatherThanRenderedAsJavaMapSyntax() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("entities", Map.of("Broken", Map.of("nested", "value")));
        SchemaDefinition malformed = new SchemaDefinition();
        malformed.setName("malformed");
        malformed.setRawType(DOMAIN_TEMPLATE);
        malformed.setCategory(DOMAIN_TEMPLATE);
        malformed.setDescription("malformed blueprint");
        malformed.setMetadata(metadata);

        String rendered = DomainBlueprintPrompt.render(List.of(malformed));

        assertFalse(rendered.contains("{nested=value}"),
                "Java map toString() is not the field DSL and the tools cannot parse it");
        assertFalse(rendered.contains("Broken:"), "the malformed entity is skipped entirely");
    }
}
