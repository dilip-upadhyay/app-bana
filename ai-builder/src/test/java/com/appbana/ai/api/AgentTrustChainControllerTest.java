package com.appbana.ai.api;

import com.appbana.ai.agent.AgentContext;
import com.appbana.ai.agent.AgentResponse;
import com.appbana.ai.agent.AiAgent;
import com.appbana.ai.agent.StreamEmitter;
import com.appbana.ai.dialogue.DialogueManager;
import com.appbana.ai.security.AgentAccessVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * S5.1 -- proves {@link com.appbana.ai.security.AgentAccessVerifier} is actually wired into both
 * production chat entry points and genuinely gates the agent loop, not merely present-but-inert.
 *
 * <p>{@code AgentAccessVerifierTest} already covers the verifier's own request/response logic in
 * isolation. This test instead drives real HTTP requests through a real {@link Router} mounted on
 * a real embedded {@link HttpServer}, into the real {@link AiChatController} /
 * {@link AgentStreamController}, with only {@link AiAgent} mocked -- so a regression that
 * constructs the verifier but never calls it (or calls it after the agent already ran) is caught
 * here even though it would pass a verifier-only unit test trivially.
 *
 * <p>Both controllers are exercised because {@code AgentStreamController} (the SSE
 * {@code /api/ai/chat/agent/stream} endpoint) is the endpoint the real Studio UI actually calls
 * (see {@code app-bana-shared/src/api-client.ts}'s {@code streamAgentChat}); {@code AiChatController}'s
 * sync endpoint shares the identical tenantId/appId trust-chain gap but is not reachable from the
 * browser today. Both needed the same fix -- this test proves both got it.
 */
class AgentTrustChainControllerTest {

    private static final String TOKEN = "test-session-token-s5-1-controller";
    private static final String TENANT = "t-test";
    private static final String APP_ID = "11111111-2222-3333-4444-555555555555";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpServer backendStub;
    private volatile int backendStubStatus;

    private HttpServer controllerServer;
    private String controllerBaseUrl;
    private AiAgent mockAgent;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() throws IOException {
        // Fake app-bana-service: only its status code matters to the verifier under test.
        backendStubStatus = 200;
        backendStub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        backendStub.createContext("/", exchange -> {
            byte[] bytes = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(backendStubStatus, bytes.length);
            try (var os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        backendStub.start();
        String backendBaseUrl = "http://127.0.0.1:" + backendStub.getAddress().getPort();

        mockAgent = mock(AiAgent.class);
        AgentAccessVerifier verifier = new AgentAccessVerifier(backendBaseUrl);
        DialogueManager dialogueManager = new DialogueManager();

        // Every dependency besides agent/dialogueManager/accessVerifier is null-checked before
        // use in both controllers (confirmed by reading processAgentRequest/handleAgentResponse
        // and stream()), so nulling them out here isolates the test to the trust-chain gate.
        AiChatController chatController = new AiChatController(
                null, null, null, mockAgent, null, null, null, dialogueManager, verifier);
        AgentStreamController streamController =
                new AgentStreamController(mockAgent, null, dialogueManager, verifier);

        Router router = new Router();
        router.post("/api/ai/chat/agent", chatController.chatAgent());
        router.post("/api/ai/chat/agent/stream", streamController.stream());

        controllerServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        controllerServer.createContext("/", router::handle);
        controllerServer.setExecutor(null);
        controllerServer.start();
        controllerBaseUrl = "http://127.0.0.1:" + controllerServer.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (backendStub != null) {
            backendStub.stop(0);
        }
        if (controllerServer != null) {
            controllerServer.stop(0);
        }
    }

    private static String chatRequestJson(String sessionId) throws Exception {
        return MAPPER.writeValueAsString(Map.of(
                "message", "hello",
                "sessionId", sessionId,
                "userId", "user-1",
                "tenantId", TENANT,
                "appId", APP_ID,
                "token", TOKEN));
    }

    @Test
    @DisplayName("sync /api/ai/chat/agent: backend denies (403) -> controller returns 403 and never invokes the agent")
    void syncEndpointDeniesCrossTenantWithoutInvokingAgent() throws Exception {
        backendStubStatus = 403;

        HttpResponse<String> response =
                postJson("/api/ai/chat/agent", chatRequestJson(UUID.randomUUID().toString()));

        assertEquals(403, response.statusCode());
        verifyNoInteractions(mockAgent);
    }

    @Test
    @DisplayName("break test: sync endpoint -- flipping the backend stub 403 -> 200 flips the controller from denying to invoking the agent")
    void syncEndpointBreakTestFlipBackendResponseFlipsBehavior() throws Exception {
        when(mockAgent.process(anyString(), any(AgentContext.class), nullable(String.class), nullable(List.class)))
                .thenReturn(AgentResponse.success("done", List.of(), 5));

        backendStubStatus = 403;
        HttpResponse<String> denied =
                postJson("/api/ai/chat/agent", chatRequestJson(UUID.randomUUID().toString()));
        assertEquals(403, denied.statusCode());
        verifyNoInteractions(mockAgent);

        backendStubStatus = 200;
        HttpResponse<String> allowed =
                postJson("/api/ai/chat/agent", chatRequestJson(UUID.randomUUID().toString()));
        assertEquals(200, allowed.statusCode());
        verify(mockAgent, times(1)).process(anyString(), any(AgentContext.class), nullable(String.class), nullable(List.class));
    }

    @Test
    @DisplayName("SSE /api/ai/chat/agent/stream: backend denies (403) -> controller returns 403 and never invokes the agent")
    void streamEndpointDeniesCrossTenantWithoutInvokingAgent() throws Exception {
        backendStubStatus = 403;

        HttpResponse<String> response =
                postJson("/api/ai/chat/agent/stream", chatRequestJson(UUID.randomUUID().toString()));

        assertEquals(403, response.statusCode());
        verifyNoInteractions(mockAgent);
    }

    @Test
    @DisplayName("break test: stream endpoint -- flipping the backend stub 403 -> 200 flips the controller from denying to invoking the agent")
    void streamEndpointBreakTestFlipBackendResponseFlipsBehavior() throws Exception {
        when(mockAgent.processWithStream(anyString(), any(AgentContext.class), nullable(String.class), nullable(List.class), any(StreamEmitter.class)))
                .thenReturn(AgentResponse.success("done", List.of(), 5));

        backendStubStatus = 403;
        HttpResponse<String> denied =
                postJson("/api/ai/chat/agent/stream", chatRequestJson(UUID.randomUUID().toString()));
        assertEquals(403, denied.statusCode());
        verifyNoInteractions(mockAgent);

        backendStubStatus = 200;
        HttpResponse<String> allowed =
                postJson("/api/ai/chat/agent/stream", chatRequestJson(UUID.randomUUID().toString()));
        assertEquals(200, allowed.statusCode());
        verify(mockAgent, times(1)).processWithStream(
                anyString(), any(AgentContext.class), nullable(String.class), nullable(List.class), any(StreamEmitter.class));
    }

    @Test
    @DisplayName("a brand-new app-creation conversation (appId=\"default\") is checked at the tenant level and still reaches the agent")
    void newAppConversationWithDefaultAppIdStillReachesAgent() throws Exception {
        when(mockAgent.process(anyString(), any(AgentContext.class), nullable(String.class), nullable(List.class)))
                .thenReturn(AgentResponse.success("done", List.of(), 5));
        backendStubStatus = 200;

        String json = MAPPER.writeValueAsString(Map.of(
                "message", "I want to build a spice-selling app",
                "sessionId", UUID.randomUUID().toString(),
                "userId", "user-1",
                "tenantId", TENANT,
                "appId", "default",
                "token", TOKEN));

        HttpResponse<String> response = postJson("/api/ai/chat/agent", json);

        assertEquals(200, response.statusCode());
        verify(mockAgent, times(1)).process(anyString(), any(AgentContext.class), nullable(String.class), nullable(List.class));
    }

    private HttpResponse<String> postJson(String path, String json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(controllerBaseUrl + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
