package com.appbana.ai.knowledge;

import com.appbana.ai.rag.EmbeddingService;
import com.appbana.ai.rag.QdrantService;
import com.appbana.ai.rag.VectorStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * C4.4c — round-trips a domain template through the real indexing path and the real search-result
 * conversion, sharing one metadata map between them.
 *
 * <p>This exists because {@code ApprovalDomainTemplateTest} mocks {@code getDomainExamples} and
 * hands the agent {@code SchemaDefinition}s it built itself. That fixture was *more complete than
 * production*: it set {@code category}, which {@code searchResultToSchema} never read back from the
 * payload. So every schema returned by every real search had {@code category == null}, and
 * {@link DomainBlueprintPrompt#render} — which selects domain templates by category — discarded a
 * correctly-retrieved, correctly-ranked pair of maker-checker blueprints and returned {@code ""}.
 * Nine passing tests and a live retrieval log that named both blueprints; a prompt with nothing in
 * it.
 *
 * <p>The guard is structural, not another assertion about a hand-built object: the map asserted on
 * is the exact map {@code indexSchemaInternal} produced. A key the writer emits and the reader
 * ignores cannot survive that, whatever the key is called.
 */
class KnowledgeBaseRoundTripTest {

    private static final String COLLECTION = "appbana_knowledge";
    private static final String ONBOARDING_TEMPLATE_ID = "domain_customer_onboarding_with_approval";

    private QdrantService qdrantService;
    private VectorStoreService vectorStoreService;
    private EmbeddingService embeddingService;
    private KnowledgeBaseService knowledgeBase;

    @BeforeEach
    void setUp() throws Exception {
        qdrantService = mock(QdrantService.class);
        vectorStoreService = mock(VectorStoreService.class);
        embeddingService = mock(EmbeddingService.class);

        when(qdrantService.getAppBanaKnowledgeCollection()).thenReturn(COLLECTION);
        when(embeddingService.embed(anyString())).thenReturn(new float[] { 0.1f, 0.2f, 0.3f });

        // A real loader, not a mock: the blueprint under test is the one production ships.
        knowledgeBase = new KnowledgeBaseService(
                qdrantService, vectorStoreService, embeddingService, new AppBanaSchemaLoader());
    }

    @Test
    @DisplayName("a domain template survives index -> search -> render with its approval metadata intact")
    void domainTemplateSurvivesTheRoundTrip() throws Exception {
        SchemaDefinition template = new AppBanaSchemaLoader().getSchema(ONBOARDING_TEMPLATE_ID);
        assertNotNull(template, "the maker-checker onboarding blueprint must exist in the shipped loader");

        Map<String, Object> payload = indexAndCapturePayload(template);

        when(vectorStoreService.search(eq(COLLECTION), any(float[].class), anyInt(), any()))
                .thenReturn(List.of(new VectorStoreService.SearchResult("point-1", 0.93f, payload)));

        List<SchemaDefinition> retrieved =
                knowledgeBase.getDomainExamples("I want a customer onboarding app", 2);

        assertEquals(1, retrieved.size(), "the stubbed search returns exactly one blueprint");
        assertEquals("domain-template", retrieved.get(0).getCategory(),
                "category is written into the payload and must be read back out of it");

        String section = DomainBlueprintPrompt.render(retrieved);
        assertFalse(section.isEmpty(),
                "a retrieved maker-checker blueprint must reach the prompt as more than an empty string");
        assertTrue(section.contains("CustomerApplication"),
                "the blueprint's entities must be rendered, got: " + section);
        assertTrue(section.contains("approvalRequired: true"),
                "the approval instruction must be rendered, got: " + section);
    }

    /**
     * Runs the production indexing path and returns the metadata map it handed the vector store —
     * so the assertion above is made against the writer's own output rather than a hand-copied
     * approximation of it.
     */
    private Map<String, Object> indexAndCapturePayload(SchemaDefinition schema) throws Exception {
        knowledgeBase.indexSchema(schema);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(vectorStoreService).store(eq(COLLECTION), anyString(), any(float[].class), captor.capture());
        return captor.getValue();
    }
}
