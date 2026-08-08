package com.appbana.ai.security;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S5.1 -- {@link AgentAccessVerifier} is the gate that stops ai-builder from trusting a
 * client-supplied tenantId/appId in the chat request body. These tests exercise it against a real
 * {@link HttpServer} stub (not mocked at the HTTP layer) so a change to the URL shape, the header
 * name, or the status-code mapping shows up as a real wire-level failure, matching the pattern
 * {@code ToolAuthHeaderTest} already established for this same reason.
 */
class AgentAccessVerifierTest {

    private static final String TOKEN = "test-session-token-s5-1";
    private static final String TENANT = "t-test";
    private static final String APP_ID = "11111111-2222-3333-4444-555555555555";

    private HttpServer server;
    private String baseUrl;
    private volatile int stubStatus;
    private volatile String stubBody;

    /** Every request the stub received, in order: path -> Authorization header (null if absent). */
    private final List<Map.Entry<String, String>> received = new ArrayList<>();

    @BeforeEach
    void startStub() throws IOException {
        stubStatus = 200;
        stubBody = "{}";
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            synchronized (received) {
                received.add(Map.entry(
                        exchange.getRequestURI().getPath(),
                        String.valueOf(exchange.getRequestHeaders().getFirst("Authorization"))));
            }
            byte[] bytes = stubBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(stubStatus, bytes.length);
            try (var os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopStub() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("a real appId is checked against the per-app route and allowed on 200")
    void realAppIdChecksPerAppRouteAndAllowsOn200() {
        stubStatus = 200;
        AgentAccessVerifier verifier = new AgentAccessVerifier(baseUrl);

        AgentAccessVerifier.VerifyResult result = verifier.verify(TENANT, APP_ID, TOKEN);

        assertTrue(result.allowed(), "expected 200 from the per-app route to allow");
        assertEquals(1, received.size());
        assertEquals("/appbana-studio/" + TENANT + "/apps/" + APP_ID, received.get(0).getKey());
    }

    @Test
    @DisplayName("a null/blank/\"default\" appId is checked at the tenant level only, not per-app")
    void noAppContextChecksTenantOnlyRoute() {
        stubStatus = 200;
        AgentAccessVerifier verifier = new AgentAccessVerifier(baseUrl);

        for (String noApp : new String[] { null, "", "   ", "default" }) {
            received.clear();
            AgentAccessVerifier.VerifyResult result = verifier.verify(TENANT, noApp, TOKEN);

            assertTrue(result.allowed(), "expected tenant-only route 200 to allow for appId=" + noApp);
            assertEquals(1, received.size());
            assertEquals("/appbana-studio/" + TENANT + "/apps", received.get(0).getKey(),
                    "appId=" + noApp + " must hit the bare tenant-list route, never a per-app path "
                            + "(a brand-new app-creation conversation has nothing to own yet)");
        }
    }

    @Test
    @DisplayName("the caller's own token is forwarded verbatim as Bearer, never a service/admin token")
    void forwardsCallersOwnTokenAsBearerHeader() {
        stubStatus = 200;
        AgentAccessVerifier verifier = new AgentAccessVerifier(baseUrl);

        verifier.verify(TENANT, APP_ID, TOKEN);

        assertEquals(1, received.size());
        assertEquals("Bearer " + TOKEN, received.get(0).getValue());
    }

    @Test
    @DisplayName("a 401 from the backend denies with 401, passed through verbatim")
    void backendUnauthorizedDeniesWith401() {
        stubStatus = 401;
        AgentAccessVerifier verifier = new AgentAccessVerifier(baseUrl);

        AgentAccessVerifier.VerifyResult result = verifier.verify(TENANT, APP_ID, TOKEN);

        assertFalse(result.allowed());
        assertEquals(401, result.statusCode());
    }

    @Test
    @DisplayName("a 403 from the backend (cross-tenant/cross-app denial) denies with 403")
    void backendForbiddenDeniesWith403() {
        stubStatus = 403;
        AgentAccessVerifier verifier = new AgentAccessVerifier(baseUrl);

        AgentAccessVerifier.VerifyResult result = verifier.verify(TENANT, APP_ID, TOKEN);

        assertFalse(result.allowed());
        assertEquals(403, result.statusCode());
    }

    @Test
    @DisplayName("a 404 (app does not exist) denies rather than silently admitting")
    void backendNotFoundDenies() {
        stubStatus = 404;
        AgentAccessVerifier verifier = new AgentAccessVerifier(baseUrl);

        AgentAccessVerifier.VerifyResult result = verifier.verify(TENANT, APP_ID, TOKEN);

        assertFalse(result.allowed());
        assertEquals(404, result.statusCode());
    }

    @Test
    @DisplayName("an unexpected status fails closed with 503, not an implicit allow")
    void unexpectedStatusFailsClosed() {
        stubStatus = 500;
        AgentAccessVerifier verifier = new AgentAccessVerifier(baseUrl);

        AgentAccessVerifier.VerifyResult result = verifier.verify(TENANT, APP_ID, TOKEN);

        assertFalse(result.allowed());
        assertEquals(503, result.statusCode());
    }

    /**
     * Review round-96 watch (a): an unreachable app-bana-service must not be treated as an
     * implicit allow. This is a genuine break test, not a mock -- the stub server is stopped
     * before the call, so the client genuinely gets a {@link java.net.ConnectException}.
     */
    @Test
    @DisplayName("an unreachable app-bana-service fails closed (deny), never open (allow)")
    void unreachableBackendFailsClosed() {
        server.stop(0);
        AgentAccessVerifier verifier = new AgentAccessVerifier(baseUrl);

        AgentAccessVerifier.VerifyResult result = verifier.verify(TENANT, APP_ID, TOKEN);

        assertFalse(result.allowed(),
                "an unreachable backend must deny -- ownership cannot be confirmed, which must "
                        + "not be treated the same as confirming it");
        assertEquals(503, result.statusCode());
    }

    /**
     * Break-test proof (review round-96 watch (c)): flip the stub's response from denying to
     * allowing and observe the verifier's verdict flip in lockstep. A verifier that always denied
     * (or always allowed) regardless of input would pass every test above individually if they
     * were written carelessly enough; this proves the verdict is actually driven by the response.
     */
    @Test
    @DisplayName("break test: flipping the stub 403 -> 200 flips the verdict deny -> allow")
    void breakTestVerdictTracksStubResponse() {
        AgentAccessVerifier verifier = new AgentAccessVerifier(baseUrl);

        stubStatus = 403;
        AgentAccessVerifier.VerifyResult denied = verifier.verify(TENANT, APP_ID, TOKEN);
        assertFalse(denied.allowed(), "stub returned 403, verifier must deny");

        stubStatus = 200;
        AgentAccessVerifier.VerifyResult allowed = verifier.verify(TENANT, APP_ID, TOKEN);
        assertTrue(allowed.allowed(), "stub returned 200, verifier must allow");
    }

    @Test
    @DisplayName("a blank tenantId defaults to \"default\", matching both controllers' own null-coalescing")
    void blankTenantIdDefaultsToDefault() {
        stubStatus = 200;
        AgentAccessVerifier verifier = new AgentAccessVerifier(baseUrl);

        for (String blankTenant : new String[] { null, "", "   " }) {
            received.clear();
            verifier.verify(blankTenant, APP_ID, TOKEN);
            assertEquals(1, received.size());
            assertEquals("/appbana-studio/default/apps/" + APP_ID, received.get(0).getKey());
        }
    }

}
