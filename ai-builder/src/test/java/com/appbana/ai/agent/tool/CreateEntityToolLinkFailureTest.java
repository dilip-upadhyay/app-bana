package com.appbana.ai.agent.tool;

import com.appbana.ai.agent.AgentContext;
import com.appbana.ai.knowledge.MetadataValidator;
import com.appbana.ai.knowledge.ValidationResult;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Review #13 (C4.4f follow-up) -- {@code CreateEntityTool.linkEntityToApp} used to make its GET
 * (fetch the app) and PUT (save the app with the entity linked) round-trip and handle neither
 * failure: any non-200, including 401, was {@code log.error}'d and the {@code void} method just
 * returned. {@code execute()} then still reached the {@code ToolResult.success(...)} at the bottom
 * of its 2xx-on-/schema branch regardless -- the exact orphan-shaped defect (schema written, app
 * linkage silently lost) that C4.4f had just fixed one tool over in {@code ScaffoldAppTool}.
 *
 * <p>These tests drive the real HTTP path with a stub {@link HttpServer} rather than asserting
 * against the source, because the previous bug was not "the code doesn't check the status code" --
 * it checked it and then didn't act on it. Only a black-box assertion on {@link ToolResult} proves
 * that: the tool must not report success when the link step failed, whatever the cause.
 */
class CreateEntityToolLinkFailureTest {

    private static final String TENANT = "t-test";
    private static final String APP_ID = "22222222-3333-4444-5555-666666666666";

    private HttpServer server;
    private String baseUrl;

    private volatile int getStatus = 200;
    private volatile int putStatus = 200;
    private volatile String getBody = "{\"schemas\":[]}";

    @Mock
    private MetadataValidator validator;

    @BeforeEach
    void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);
        when(validator.validateEntity(any())).thenReturn(new ValidationResult());

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/schema", exchange -> respond(exchange, 200, "{\"status\":\"created\"}"));
        server.createContext("/appbana-studio/", exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                respond(exchange, getStatus, getStatus == 200 ? getBody : "{\"error\":\"boom\"}");
            } else {
                respond(exchange, putStatus, putStatus == 200 ? "{\"status\":\"ok\"}" : "{\"error\":\"boom\"}");
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static Map<String, Object> args() {
        Map<String, Object> a = new HashMap<>();
        a.put("name", "Customer");
        a.put("fields", List.of(Map.of("name", "email", "type", "email", "required", true)));
        a.put("appId", APP_ID);
        return a;
    }

    @Test
    @DisplayName("a 401 on the link-entity-to-app GET fails the tool as an auth failure, not a success")
    void linkGet401FailsAsAuthFailure() {
        getStatus = 401;

        CreateEntityTool tool = new CreateEntityTool(validator, baseUrl);
        ToolResult result = tool.execute(args(), AgentContext.create(TENANT, APP_ID, "user-1", "session-1", "tok"));

        assertFalse(result.isSuccess(), "create_entity reported success despite the app-link GET returning 401: " + result);
        assertTrue(result.isAuthFailure(), "a 401 on the link step must be surfaced as an auth failure: " + result);
    }

    @Test
    @DisplayName("a 401 on the link-entity-to-app PUT fails the tool as an auth failure, not a success")
    void linkPut401FailsAsAuthFailure() {
        putStatus = 401;

        CreateEntityTool tool = new CreateEntityTool(validator, baseUrl);
        ToolResult result = tool.execute(args(), AgentContext.create(TENANT, APP_ID, "user-1", "session-1", "tok"));

        assertFalse(result.isSuccess(), "create_entity reported success despite the app-link PUT returning 401: " + result);
        assertTrue(result.isAuthFailure(), "a 401 on the link step must be surfaced as an auth failure: " + result);
    }

    @Test
    @DisplayName("a non-auth failure on the link-entity-to-app GET still fails the tool, not just logs it")
    void linkGetServerErrorFailsTheTool() {
        getStatus = 500;

        CreateEntityTool tool = new CreateEntityTool(validator, baseUrl);
        ToolResult result = tool.execute(args(), AgentContext.create(TENANT, APP_ID, "user-1", "session-1", "tok"));

        assertFalse(result.isSuccess(), "create_entity reported success despite the app-link GET returning 500: " + result);
        assertFalse(result.isAuthFailure(), "a 500 is not an auth failure, it's a generic failure: " + result);
        assertEquals("create_entity", result.getToolName());
    }
}
