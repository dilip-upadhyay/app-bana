package com.appbana.ai.knowledge;

import com.appbana.ai.rag.EmbeddingService;
import com.appbana.ai.rag.EmbeddingService.EmbeddingException;
import com.appbana.ai.rag.QdrantService;
import com.appbana.ai.rag.VectorStoreService;
import com.appbana.ai.rag.VectorStoreService.SearchResult;
import com.appbana.ai.rag.VectorStoreService.VectorStoreException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for KnowledgeBaseService
 * Story 7.2: Vector Store Integration
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceTest {

    @Mock
    private QdrantService qdrantService;

    @Mock
    private VectorStoreService vectorStoreService;

    @Mock
    private EmbeddingService embeddingService;

    private AppBanaSchemaLoader schemaLoader;
    private KnowledgeBaseService knowledgeBaseService;
    /** The loader's schema set grows over time, so never hard-code the expected count. */
    private int schemaCount;

    private static final String COLLECTION_NAME = "appbana_knowledge";
    private static final float[] FAKE_EMBEDDING = new float[1536];

    @BeforeEach
    void setUp() {
        // Use real schema loader for test data
        schemaLoader = new AppBanaSchemaLoader();
        schemaCount = schemaLoader.getAllSchemas().size();

        // Initialize service with mocks
        knowledgeBaseService = new KnowledgeBaseService(
                qdrantService,
                vectorStoreService,
                embeddingService,
                schemaLoader);

        // Setup default mock behaviors (lenient for tests that don't use them)
        lenient().when(qdrantService.getAppBanaKnowledgeCollection()).thenReturn(COLLECTION_NAME);

        // Fill fake embedding with values
        Arrays.fill(FAKE_EMBEDDING, 0.1f);
    }

    /**
     * indexAllSchemas() batches its embedding calls through embedBatch(), not embed().
     * Stubbing only embed() leaves embedBatch() returning an empty list, which makes every
     * single schema fail on embeddings.get(i).
     */
    private void stubBatchEmbedding() throws Exception {
        when(embeddingService.embedBatch(anyList())).thenAnswer(invocation -> {
            List<String> texts = invocation.getArgument(0);
            List<float[]> out = new ArrayList<>(texts.size());
            for (int i = 0; i < texts.size(); i++) {
                out.add(FAKE_EMBEDDING);
            }
            return out;
        });
    }

    @Test
    void testIndexAllSchemas_Success() throws Exception {
        // Arrange
        stubBatchEmbedding();
        doNothing().when(vectorStoreService).store(anyString(), anyString(), any(float[].class), anyMap());

        // Act
        knowledgeBaseService.indexAllSchemas();

        // Assert
        assertTrue(knowledgeBaseService.isInitialized());
        assertTrue(knowledgeBaseService.getIndexedCount() > 0);

        // Verify all schemas were indexed (31 field types + 5 components + 1 page + 1
        // validation + 1 = 39)
        assertEquals(schemaCount, knowledgeBaseService.getIndexedCount());

        // Verify store was called for each schema
        verify(vectorStoreService, times(schemaCount)).store(
                eq(COLLECTION_NAME),
                anyString(),
                any(float[].class),
                anyMap());
    }

    @Test
    void testSearchRelevantSchemas_EmailField() throws Exception {
        // Arrange - Index first
        stubBatchEmbedding();
        when(embeddingService.embed(anyString())).thenReturn(FAKE_EMBEDDING);
        doNothing().when(vectorStoreService).store(anyString(), anyString(), any(float[].class), anyMap());
        knowledgeBaseService.indexAllSchemas();

        // Setup search result for email field
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("schemaId", "field_email");
        metadata.put("schemaType", "field-type");
        metadata.put("schemaName", "email");
        metadata.put("description", "Email with validation");
        metadata.put("examples", "[\"user@example.com\"]");

        SearchResult searchResult = new SearchResult("field_email", 0.95f, metadata);
        when(vectorStoreService.search(eq(COLLECTION_NAME), any(float[].class), eq(5), isNull()))
                .thenReturn(List.of(searchResult));

        // Act
        List<SchemaDefinition> results = knowledgeBaseService.searchRelevantSchemas("email validation", 5);

        // Assert
        assertNotNull(results);
        assertEquals(1, results.size());

        SchemaDefinition emailSchema = results.get(0);
        assertEquals("field_email", emailSchema.getId());
        assertEquals("email", emailSchema.getName());
        assertEquals("field-type", emailSchema.getType());
    }

    @Test
    void testSearchByType_ComponentsOnly() throws Exception {
        // Arrange - Index first
        stubBatchEmbedding();
        when(embeddingService.embed(anyString())).thenReturn(FAKE_EMBEDDING);
        doNothing().when(vectorStoreService).store(anyString(), anyString(), any(float[].class), anyMap());
        knowledgeBaseService.indexAllSchemas();

        // Setup search result for input component
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("schemaId", "component_input");
        metadata.put("schemaType", "component");
        metadata.put("schemaName", "input");
        metadata.put("description", "Text input field");
        metadata.put("examples", "[]");

        SearchResult searchResult = new SearchResult("component_input", 0.90f, metadata);

        Map<String, Object> expectedFilter = Map.of("schemaType", "component");
        when(vectorStoreService.search(eq(COLLECTION_NAME), any(float[].class), eq(10), eq(expectedFilter)))
                .thenReturn(List.of(searchResult));

        // Act
        List<SchemaDefinition> results = knowledgeBaseService.searchByType(
                SchemaDefinition.SchemaType.COMPONENT,
                "form input",
                10);

        // Assert
        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("component", results.get(0).getType());

        // Verify filter was used
        verify(vectorStoreService).search(
                eq(COLLECTION_NAME),
                any(float[].class),
                eq(10),
                eq(expectedFilter));
    }

    @Test
    void testGetExamples_InputComponent() throws Exception {
        // Act
        List<String> examples = knowledgeBaseService.getExamples("input");

        // Assert
        assertNotNull(examples);
        assertFalse(examples.isEmpty());

        // Verify examples contain JSON
        assertTrue(examples.get(0).contains("\"type\":\"input\""));
    }

    @Test
    void testRefreshKnowledge_ClearsAndReindexes() throws Exception {
        // Arrange
        stubBatchEmbedding();
        doNothing().when(vectorStoreService).store(anyString(), anyString(), any(float[].class), anyMap());
        when(qdrantService.collectionExists(COLLECTION_NAME)).thenReturn(true);
        doNothing().when(qdrantService).deleteCollection(COLLECTION_NAME);
        doNothing().when(qdrantService).initializeCollections();

        // Act
        knowledgeBaseService.refreshKnowledge();

        // Assert
        assertTrue(knowledgeBaseService.isInitialized());
        assertEquals(schemaCount, knowledgeBaseService.getIndexedCount());

        // Verify collection was deleted and recreated
        verify(qdrantService).deleteCollection(COLLECTION_NAME);
        verify(qdrantService).initializeCollections();

        // Verify re-indexing happened
        verify(vectorStoreService, times(schemaCount)).store(
                eq(COLLECTION_NAME),
                anyString(),
                any(float[].class),
                anyMap());
    }

    @Test
    void testSearchRelevantSchemas_EmptyQuery() throws Exception {
        // Arrange - Index first
        stubBatchEmbedding();
        when(embeddingService.embed(anyString())).thenReturn(FAKE_EMBEDDING);
        doNothing().when(vectorStoreService).store(anyString(), anyString(), any(float[].class), anyMap());
        knowledgeBaseService.indexAllSchemas();

        when(vectorStoreService.search(eq(COLLECTION_NAME), any(float[].class), eq(5), isNull()))
                .thenReturn(Collections.emptyList());

        // Act
        List<SchemaDefinition> results = knowledgeBaseService.searchRelevantSchemas("", 5);

        // Assert
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void testIndexAllSchemas_EmbeddingFailure() throws Exception {
        // Arrange — the batch call is what indexAllSchemas actually uses.
        when(embeddingService.embedBatch(anyList()))
                .thenThrow(new EmbeddingException("OpenAI API error"));

        // Act & Assert
        KnowledgeBaseException exception = assertThrows(
                KnowledgeBaseException.class,
                () -> knowledgeBaseService.indexAllSchemas());

        assertTrue(exception.getMessage().contains("Failed to index schemas"));
        // Note: initialized may be true if some schemas succeeded before failure
        assertTrue(knowledgeBaseService.getIndexedCount() < schemaCount);
    }

    @Test
    void testSearchRelevantSchemas_NotInitialized() {
        // Act & Assert
        KnowledgeBaseException exception = assertThrows(
                KnowledgeBaseException.class,
                () -> knowledgeBaseService.searchRelevantSchemas("test", 5));

        assertTrue(exception.getMessage().contains("not initialized"));
    }

    @Test
    void testGetExamples_NonExistentComponent() throws Exception {
        // Act
        List<String> examples = knowledgeBaseService.getExamples("nonexistent");

        // Assert
        assertNotNull(examples);
        assertTrue(examples.isEmpty());
    }
}
