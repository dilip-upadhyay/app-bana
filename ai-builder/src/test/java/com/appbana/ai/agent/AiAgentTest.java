package com.appbana.ai.agent;

import com.appbana.ai.agent.tool.*;
import com.appbana.ai.llm.LlmRegistry;
import com.appbana.ai.llm.LlmService;
import com.appbana.ai.llm.OpenAiLlmService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AiAgent
 * Story 8.1: Core Agent Infrastructure
 */
class AiAgentTest {

    @Mock
    private LlmService llmService;

    @Mock
    private LlmRegistry llmRegistry;

    private ToolRegistry toolRegistry;
    private AgentConfig config;
    private AiAgent agent;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        toolRegistry = new ToolRegistry();
        config = AgentConfig.defaults();
        config.setDebugMode(false);
        
        // Mock the registry to return our mocked LLM service
        when(llmRegistry.getService(nullable(String.class))).thenReturn(llmService);
        
        agent = new AiAgent(llmRegistry, toolRegistry, config);
        agent.setPatternMatchingEnabled(false);
    }


    @Test
    void testAgentLoop_FinalAnswerImmediately() throws Exception {
        // Arrange
        String userMessage = "Hello";
        AgentContext context = AgentContext.create("tenant1", "app1", "user1", "session1", "test-token");

        String llmResponse = """
                {
                  "thinking": "User just said hello, I'll respond",
                  "final_answer": "Hello! How can I help you build an application?"
                }
                """;

        when(llmService.chatWithJsonMode(anyString())).thenReturn(llmResponse);

        // Act
        AgentResponse response = agent.process(userMessage, context);

        // Assert
        assertTrue(response.isSuccess());
        assertEquals("Hello! How can I help you build an application?", response.getFinalAnswer());
        assertEquals(1, response.getIterationCount());
        verify(llmService, times(1)).chatWithJsonMode(anyString());
    }

    @Test
    void testAgentLoop_WithToolCalls() throws Exception {
        // Arrange
        String userMessage = "Create a customer entity";
        AgentContext context = AgentContext.create("tenant1", "app1", "user1", "session1", "test-token");

        // Register a mock tool
        Tool mockTool = new Tool() {
            @Override
            public String getName() {
                return "create_entity";
            }

            @Override
            public String getDescription() {
                return "Create an entity";
            }

            @Override
            public String getParameterSchema() {
                return "{}";
            }

            @Override
            public ToolResult execute(Map<String, Object> arguments, AgentContext context) {
                return ToolResult.success("create_entity", Map.of("id", "customer"), 100);
            }
        };
        toolRegistry.register(mockTool);

        // First iteration: call tool
        String llmResponse1 = """
                {
                  "thinking": "I need to create the customer entity",
                  "tool_calls": [
                    {"name": "create_entity", "arguments": {"name": "customer"}}
                  ]
                }
                """;

        // Second iteration: final answer
        String llmResponse2 = """
                {
                  "thinking": "Entity created successfully",
                  "final_answer": "I've created the customer entity for you!"
                }
                """;

        when(llmService.chatWithJsonMode(anyString()))
                .thenReturn(llmResponse1)
                .thenReturn(llmResponse2);

        // Act
        AgentResponse response = agent.process(userMessage, context);

        // Assert
        assertTrue(response.isSuccess());
        assertEquals("I've created the customer entity for you!", response.getFinalAnswer());
        assertEquals(2, response.getIterationCount());
        assertEquals(2, response.getSteps().size());

        // Check first step has tool result
        AgentResponse.AgentStep firstStep = response.getSteps().get(0);
        assertEquals(1, firstStep.getToolResults().size());
        assertTrue(firstStep.getToolResults().get(0).isSuccess());

        verify(llmService, times(2)).chatWithJsonMode(anyString());
    }

    @Test
    void testAgentLoop_MaxIterationsReached() throws Exception {
        // Arrange
        String userMessage = "Do something complex";
        AgentContext context = AgentContext.create("tenant1", "app1", "user1", "session1", "test-token");
        config.setMaxIterations(3);

        // Create a new agent with updated config
        agent = new AiAgent(llmRegistry, toolRegistry, config);

        // Always return tool calls, never final answer
        String llmResponse = """
                {
                  "thinking": "Still working on it",
                  "tool_calls": []
                }
                """;

        when(llmService.chatWithJsonMode(anyString())).thenReturn(llmResponse);

        // Act
        AgentResponse response = agent.process(userMessage, context);

        // Assert
        // Agent will return final answer "I'm not sure what to do next" when no
        // tool_calls
        assertTrue(response.isSuccess());
        assertEquals(1, response.getIterationCount());
        verify(llmService, times(1)).chatWithJsonMode(anyString());
    }

    @Test
    void testAgentLoop_ToolNotFound() throws Exception {
        // Arrange
        String userMessage = "Use unknown tool";
        AgentContext context = AgentContext.create("tenant1", "app1", "user1", "session1", "test-token");

        // First call: try unknown tool
        String llmResponse1 = """
                {
                  "thinking": "I'll use this tool",
                  "tool_calls": [
                    {"name": "unknown_tool", "arguments": {}}
                  ]
                }
                """;

        // Second call: give up
        String llmResponse2 = """
                {
                  "thinking": "Tool not found, I'll stop",
                  "final_answer": "Sorry, I don't have that capability."
                }
                """;

        when(llmService.chatWithJsonMode(anyString()))
                .thenReturn(llmResponse1)
                .thenReturn(llmResponse2);

        // Act
        AgentResponse response = agent.process(userMessage, context);

        // Assert
        assertTrue(response.isSuccess());
        assertEquals(2, response.getSteps().size());

        AgentResponse.AgentStep step = response.getSteps().get(0);
        assertEquals(1, step.getToolResults().size());
        assertFalse(step.getToolResults().get(0).isSuccess());
        assertTrue(step.getToolResults().get(0).getError().contains("Tool not found"));
    }

    @Test
    void testAgentLoop_LlmError() throws Exception {
        // Arrange
        String userMessage = "Test error handling";
        AgentContext context = AgentContext.create("tenant1", "app1", "user1", "session1", "test-token");
        config.setRetryOnError(false);

        when(llmService.chatWithJsonMode(anyString())).thenThrow(new RuntimeException("LLM API error"));

        // Act
        AgentResponse response = agent.process(userMessage, context);

        // Assert
        assertFalse(response.isSuccess());
        assertNotNull(response.getError());
    }

    @Test
    void testAgentLoop_HandlesHallucinatedSchema() throws Exception {
        // Arrange
        String userMessage = "Tell me something";
        AgentContext context = AgentContext.create("tenant1", "app1", "user1", "session1", "test-token");

        // Hallucinated schema with 'action' and 'response'
        String llmResponse = """
                {
                  "action": "THINKING_INTERNALLY",
                  "response": "Here is your hallucinated answer"
                }
                """;

        when(llmService.chatWithJsonMode(anyString())).thenReturn(llmResponse);

        // Act
        AgentResponse response = agent.process(userMessage, context);

        // Assert
        assertTrue(response.isSuccess());
        assertEquals("Here is your hallucinated answer", response.getFinalAnswer());
        // Since action maps to thinking, check if thinking is captured
        assertEquals("THINKING_INTERNALLY", response.getSteps().get(0).getThinking());
    }

    @Test
    void testAgentLoop_InvalidJson() throws Exception {
        // Arrange
        String userMessage = "Test invalid JSON";
        AgentContext context = AgentContext.create("tenant1", "app1", "user1", "session1", "test-token");
        config.setRetryOnError(false);

        when(llmService.chatWithJsonMode(anyString())).thenReturn("This is not JSON");

        // Act
        AgentResponse response = agent.process(userMessage, context);

        // Assert
        assertTrue(response.isSuccess());
        assertEquals("This is not JSON", response.getFinalAnswer());
    }

    /**
     * C4.4e Review #12 (DoD item 3) — a tool returning a 401-shaped ToolResult
     * (ToolResult.authError, isAuthFailure()==true) must abort the non-streaming loop
     * on iteration 1, not iteration 3 (the generic consecutiveFailures>=3 path), and
     * the message must say the session ended -- not "rephrase or try again".
     */
    @Test
    void testAgentLoop_AuthFailureAbortsOnFirstIterationNotThird() throws Exception {
        // Arrange
        String userMessage = "List my apps";
        AgentContext context = AgentContext.create("tenant1", "app1", "user1", "session1", "test-token");

        Tool authFailingTool = new Tool() {
            @Override
            public String getName() {
                return "list_apps";
            }

            @Override
            public String getDescription() {
                return "List apps";
            }

            @Override
            public String getParameterSchema() {
                return "{}";
            }

            @Override
            public ToolResult execute(Map<String, Object> arguments, AgentContext ctx) {
                return ToolResult.authError("list_apps", "list_apps: list_apps returned 401");
            }
        };
        toolRegistry.register(authFailingTool);

        String llmResponse = """
                {
                  "thinking": "I'll list the apps",
                  "tool_calls": [
                    {"name": "list_apps", "arguments": {}}
                  ]
                }
                """;
        when(llmService.chatWithJsonMode(anyString())).thenReturn(llmResponse);

        // Act
        AgentResponse response = agent.process(userMessage, context);

        // Assert -- aborted on iteration 1, not 3: only one LLM call happened.
        assertFalse(response.isSuccess());
        assertEquals(1, response.getIterationCount());
        verify(llmService, times(1)).chatWithJsonMode(anyString());

        // The message must say the session ended, not the generic "please rephrase".
        assertNotNull(response.getError());
        assertTrue(response.getError().toLowerCase().contains("session"),
                "Expected a session-ended message, got: " + response.getError());
        assertFalse(response.getError().toLowerCase().contains("rephrase"),
                "Auth failure must not be reported as a generic 'please rephrase' error: " + response.getError());
    }

    /**
     * Same scenario, streaming loop -- also verifies a distinct SSE `auth_expired` event is
     * emitted (which the Studio's ChatPane maps to the `appbana:auth:expired` recovery), not
     * folded silently into a generic `done` with no signal to distinguish it.
     */
    @Test
    void testAgentLoopStream_AuthFailureAbortsAndEmitsAuthExpiredEvent() throws Exception {
        // Arrange
        String userMessage = "List my apps";
        AgentContext context = AgentContext.create("tenant1", "app1", "user1", "session1", "test-token");

        Tool authFailingTool = new Tool() {
            @Override
            public String getName() {
                return "list_apps";
            }

            @Override
            public String getDescription() {
                return "List apps";
            }

            @Override
            public String getParameterSchema() {
                return "{}";
            }

            @Override
            public ToolResult execute(Map<String, Object> arguments, AgentContext ctx) {
                return ToolResult.authError("list_apps", "list_apps: list_apps returned 401");
            }
        };
        toolRegistry.register(authFailingTool);

        String llmResponse = """
                {
                  "thinking": "I'll list the apps",
                  "tool_calls": [
                    {"name": "list_apps", "arguments": {}}
                  ]
                }
                """;
        when(llmService.chatWithJsonMode(anyString())).thenReturn(llmResponse);

        List<String> emittedEventNames = new java.util.ArrayList<>();
        List<Object> emittedPayloads = new java.util.ArrayList<>();
        StreamEmitter fakeEmitter = new StreamEmitter() {
            @Override
            public void emit(String eventName, Object payload) {
                emittedEventNames.add(eventName);
                emittedPayloads.add(payload);
            }

            @Override
            public void complete() {
                // no-op for this test
            }
        };

        // Act
        AgentResponse response = agent.processWithStream(userMessage, context, null, null, fakeEmitter);

        // Assert -- aborted on iteration 1, not 3.
        assertFalse(response.isSuccess());
        assertEquals(1, response.getIterationCount());
        verify(llmService, times(1)).chatWithJsonMode(anyString());

        assertTrue(emittedEventNames.contains("auth_expired"),
                "Expected an auth_expired SSE event, got: " + emittedEventNames);
        assertTrue(emittedEventNames.contains("done"));

        @SuppressWarnings("unchecked")
        Map<String, Object> authExpiredPayload = (Map<String, Object>) emittedPayloads.get(
                emittedEventNames.indexOf("auth_expired"));
        String message = (String) authExpiredPayload.get("message");
        assertNotNull(message);
        assertTrue(message.toLowerCase().contains("session"), "Expected a session-ended message: " + message);
        assertFalse(message.toLowerCase().contains("rephrase"), "Must not say 'rephrase': " + message);
    }

    @Test
    void testAgentContext_Immutability() {
        // Arrange
        AgentContext context = AgentContext.create("tenant1", "app1", "user1", "session1", "test-token");

        // Act
        AgentContext newContext = context.withVariable("key", "value");

        // Assert
        assertFalse(context.hasVariable("key"));
        assertTrue(newContext.hasVariable("key"));
        assertEquals("value", newContext.getVariable("key"));
    }

    @Test
    void testToolRegistry_DuplicateRegistration() {
        // Arrange
        Tool tool = createMockTool("test_tool");
        toolRegistry.register(tool);

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> {
            toolRegistry.register(tool);
        });
    }

    private Tool createMockTool(String name) {
        return new Tool() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getDescription() {
                return "Test tool";
            }

            @Override
            public String getParameterSchema() {
                return "{}";
            }

            @Override
            public ToolResult execute(Map<String, Object> arguments, AgentContext context) {
                return ToolResult.success(name, "success", 0);
            }
        };
    }
}

