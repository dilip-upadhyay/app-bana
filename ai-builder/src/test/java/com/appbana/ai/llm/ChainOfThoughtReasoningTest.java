package com.appbana.ai.llm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ChainOfThoughtReasoning
 */
class ChainOfThoughtReasoningTest {

    @Mock
    private OpenAiLlmService llmService;

    private ChainOfThoughtReasoning reasoning;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        reasoning = new ChainOfThoughtReasoning(llmService);
    }

    @Test
    void testGenerateReasoning() {
        // Mock LLM response with step-by-step reasoning
        String llmResponse = """
                Step 1: Understand the requirement
                The user wants to create a new entity for managing customers.

                Step 2: Identify key fields
                Based on typical customer management, we need name, email, phone, and address fields.

                Step 3: Plan the implementation
                Create the entity schema with appropriate field types and validations.

                Conclusion: I'll create a Customer entity with name (text), email (email), phone (text), and address (text) fields.
                """;

        when(llmService.chat(anyString())).thenReturn(llmResponse);

        ChainOfThoughtReasoning.ReasoningChain chain = reasoning.generateReasoning(
                "Create a customer entity",
                Map.of());

        assertNotNull(chain);
        assertEquals(3, chain.getSteps().size());
        assertFalse(chain.getConclusion().isEmpty());

        // Verify first step
        assertEquals("Understand the requirement", chain.getSteps().get(0).getTitle());
        assertTrue(chain.getSteps().get(0).getDescription().contains("customer"));
    }

    @Test
    void testFormatForDisplay() {
        // Create a sample reasoning chain
        List<ChainOfThoughtReasoning.ReasoningStep> steps = List.of(
                new ChainOfThoughtReasoning.ReasoningStep("Step 1", "Description 1"),
                new ChainOfThoughtReasoning.ReasoningStep("Step 2", "Description 2"));

        ChainOfThoughtReasoning.ReasoningChain chain = new ChainOfThoughtReasoning.ReasoningChain(steps,
                "Final conclusion");

        String formatted = reasoning.formatForDisplay(chain);

        assertTrue(formatted.contains("My Thinking Process"));
        assertTrue(formatted.contains("1. **Step 1**"));
        assertTrue(formatted.contains("2. **Step 2**"));
        assertTrue(formatted.contains("Conclusion"));
    }

    @Test
    void testValidateReasoning_Valid() {
        List<ChainOfThoughtReasoning.ReasoningStep> steps = List.of(
                new ChainOfThoughtReasoning.ReasoningStep("Title 1", "Description 1"),
                new ChainOfThoughtReasoning.ReasoningStep("Title 2", "Description 2"));

        ChainOfThoughtReasoning.ReasoningChain chain = new ChainOfThoughtReasoning.ReasoningChain(steps, "Conclusion");

        assertTrue(reasoning.validateReasoning(chain));
    }

    @Test
    void testValidateReasoning_Invalid() {
        // Empty steps
        ChainOfThoughtReasoning.ReasoningChain emptyChain = new ChainOfThoughtReasoning.ReasoningChain(List.of(),
                "Conclusion");

        assertFalse(reasoning.validateReasoning(emptyChain));

        // Null chain
        assertFalse(reasoning.validateReasoning(null));

        // Empty conclusion
        List<ChainOfThoughtReasoning.ReasoningStep> steps = List.of(
                new ChainOfThoughtReasoning.ReasoningStep("Title", "Description"));
        ChainOfThoughtReasoning.ReasoningChain noConclusion = new ChainOfThoughtReasoning.ReasoningChain(steps, "");

        assertFalse(reasoning.validateReasoning(noConclusion));
    }

    @Test
    void testGenerateReasoning_ErrorHandling() {
        // Mock LLM throwing exception
        when(llmService.chat(anyString())).thenThrow(new RuntimeException("API error"));

        ChainOfThoughtReasoning.ReasoningChain chain = reasoning.generateReasoning(
                "test request",
                Map.of());

        // Should return fallback reasoning
        assertNotNull(chain);
        assertFalse(chain.getSteps().isEmpty());
        assertFalse(chain.getConclusion().isEmpty());
    }
}
