package com.appbana.ai.dialogue;

import com.appbana.ai.llm.OpenAiLlmService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AmbiguityResolver
 */
class AmbiguityResolverTest {

    @Mock
    private OpenAiLlmService llmService;

    private AmbiguityResolver resolver;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        resolver = new AmbiguityResolver(llmService);
    }

    @Test
    void testDetectAmbiguity_Clear() {
        // Mock LLM response for clear input
        String llmResponse = """
                {
                  "isAmbiguous": false,
                  "ambiguousAspects": [],
                  "reason": "Request is clear and specific",
                  "confidence": 0.95
                }
                """;

        when(llmService.chat(anyString())).thenReturn(llmResponse);

        AmbiguityResolver.AmbiguityResult result = resolver.detectAmbiguity(
                "Create a User entity with name, email, and age fields",
                Map.of());

        assertFalse(result.isAmbiguous());
        assertEquals(0.95, result.getConfidence(), 0.01);
    }

    @Test
    void testDetectAmbiguity_Ambiguous() {
        // Mock LLM response for ambiguous input
        String llmResponse = """
                {
                  "isAmbiguous": true,
                  "ambiguousAspects": ["entity name", "field types"],
                  "reason": "Missing specific entity name and field type details",
                  "confidence": 0.85
                }
                """;

        when(llmService.chat(anyString())).thenReturn(llmResponse);

        AmbiguityResolver.AmbiguityResult result = resolver.detectAmbiguity(
                "Create an entity with some fields",
                Map.of());

        assertTrue(result.isAmbiguous());
        assertEquals(2, result.getAmbiguousAspects().size());
        assertTrue(result.getAmbiguousAspects().contains("entity name"));
    }

    @Test
    void testGenerateClarifyingQuestions() {
        // Mock LLM response
        String llmResponse = """
                ["What should the entity be called?", "What fields do you need?", "What data types for the fields?"]
                """;

        when(llmService.chat(anyString())).thenReturn(llmResponse);

        List<String> questions = resolver.generateClarifyingQuestions(
                "Create an entity",
                List.of("entity name", "fields"));

        assertEquals(3, questions.size());
        assertTrue(questions.get(0).contains("entity"));
    }

    @Test
    void testOfferInterpretations() {
        // Mock LLM response
        String llmResponse = """
                [
                  {"interpretation": "Create a Customer entity", "confidence": 0.7},
                  {"interpretation": "Create a Product entity", "confidence": 0.5}
                ]
                """;

        when(llmService.chat(anyString())).thenReturn(llmResponse);

        List<AmbiguityResolver.Interpretation> interpretations = resolver
                .offerInterpretations("Create an entity for sales");

        assertEquals(2, interpretations.size());
        assertEquals(0.7, interpretations.get(0).getConfidence(), 0.01);
    }

    @Test
    void testDetectAmbiguity_ErrorHandling() {
        // Mock LLM throwing exception
        when(llmService.chat(anyString())).thenThrow(new RuntimeException("API error"));

        AmbiguityResolver.AmbiguityResult result = resolver.detectAmbiguity(
                "test message",
                Map.of());

        // Should return non-ambiguous as fallback
        assertFalse(result.isAmbiguous());
    }
}
