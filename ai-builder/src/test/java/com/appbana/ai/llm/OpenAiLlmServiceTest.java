package com.appbana.ai.llm;

import com.appbana.ai.config.AiConfig;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OpenAiLlmService
 * 
 * Note: These are basic tests. Integration tests with real API
 * would require OPENAI_API_KEY and make actual API calls.
 */
class OpenAiLlmServiceTest {

    private AiConfig config;

    @BeforeEach
    void setUp() {
        config = new AiConfig();
        config.setOpenaiApiKey("sk-test-key-for-unit-tests");
        config.setOpenaiModel("gpt-4");
    }

    @Test
    @DisplayName("Should initialize successfully")
    void testInitialization() {
        // When
        OpenAiLlmService service = new OpenAiLlmService(config);

        // Then
        assertNotNull(service);
    }

    @Test
    @DisplayName("Should close without errors")
    void testClose() {
        // Given
        OpenAiLlmService service = new OpenAiLlmService(config);

        // When/Then
        assertDoesNotThrow(() -> service.close());
    }

    @Test
    @DisplayName("LlmException should have message")
    void testLlmException_Message() {
        // When
        OpenAiLlmService.LlmException exception = new OpenAiLlmService.LlmException("Test error");

        // Then
        assertEquals("Test error", exception.getMessage());
    }

    @Test
    @DisplayName("LlmException should have cause")
    void testLlmException_Cause() {
        // Given
        Exception cause = new RuntimeException("Root cause");

        // When
        OpenAiLlmService.LlmException exception = new OpenAiLlmService.LlmException("Test error", cause);

        // Then
        assertEquals("Test error", exception.getMessage());
        assertEquals(cause, exception.getCause());
    }
}
