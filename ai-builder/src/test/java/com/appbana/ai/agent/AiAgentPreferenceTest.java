package com.appbana.ai.agent;

import com.appbana.ai.agent.tool.ToolRegistry;
import com.appbana.ai.llm.OpenAiLlmService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit test to verify that user preferences are correctly injected into the AI
 * Agent's prompt.
 */
class AiAgentPreferenceTest {

    @Mock
    private OpenAiLlmService llmService;

    private ToolRegistry toolRegistry;
    private AgentConfig config;
    private AiAgent agent;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        toolRegistry = new ToolRegistry();
        config = AgentConfig.defaults();
        config.setDebugMode(false);
        agent = new AiAgent(llmService, toolRegistry, config);
    }

    @Test
    void testAgentPrompt_IncludesUserPreferences() throws Exception {
        // Arrange
        String userMessage = "Create an entity";

        // Mock preferences in context
        Map<String, String> prefs = Map.of(
                "naming_convention", "snake_case",
                "ui_style", "material");

        AgentContext context = AgentContext.create("tenant1", "app1", "user1", "session1", "test-token")
                .withVariable("user_preferences", prefs);

        String llmResponse = "{\"thinking\": \"monologue\", \"final_answer\": \"done\"}";
        when(llmService.chat(anyString())).thenReturn(llmResponse);

        // Act
        agent.process(userMessage, context);

        // Assert
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmService).chat(promptCaptor.capture());

        String capturedPrompt = promptCaptor.getValue();

        // Check for the header and the specific preferences
        assertTrue(capturedPrompt.contains("## USER PREFERENCES & STYLE"), "Prompt should contain preferences header");
        assertTrue(capturedPrompt.contains("**naming_convention**: snake_case"),
                "Prompt should contain naming preference");
        assertTrue(capturedPrompt.contains("**ui_style**: material"), "Prompt should contain UI style preference");
        assertTrue(capturedPrompt.contains("You MUST respect the following user preferences:"),
                "Prompt should contain the instruction to respect preferences");
    }

    @Test
    void testAgentPrompt_NoPreferences_DoesNotIncludeSection() throws Exception {
        // Arrange
        String userMessage = "Create an entity";

        // No preferences in context
        AgentContext context = AgentContext.create("tenant1", "app1", "user1", "session1", "test-token");

        String llmResponse = "{\"thinking\": \"monologue\", \"final_answer\": \"done\"}";
        when(llmService.chat(anyString())).thenReturn(llmResponse);

        // Act
        agent.process(userMessage, context);

        // Assert
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmService).chat(promptCaptor.capture());

        String capturedPrompt = promptCaptor.getValue();

        // Check that the section is NOT present
        assertTrue(!capturedPrompt.contains("## USER PREFERENCES & STYLE"),
                "Prompt should NOT contain preferences header when no preferences exist");
    }
}
