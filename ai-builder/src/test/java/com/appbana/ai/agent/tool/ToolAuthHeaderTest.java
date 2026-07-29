package com.appbana.ai.agent.tool;

import com.appbana.ai.agent.AgentContext;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * C4.4d -- every backend request an agent tool makes must carry the caller's session token.
 *
 * <p>Five tools were sending none: {@code list_apps}, {@code list_pages}, {@code list_workflows},
 * and the app-context branches of {@code list_entities} and {@code get_entity_details}. All five
 * 401'd on every invocation. A failing tool burns an agent iteration and increments
 * {@code consecutiveFailures} toward the abort-at-3, so the user-visible symptom was the agent
 * giving up vaguely rather than an auth error. {@code get_entity_details} was worse: its 401 was
 * swallowed by a {@code statusCode() != 200} fall-through to the global {@code /schema} lookup,
 * which misses because it uses the unprefixed entity name -- so the tool answered "entity not
 * found" for an entity that exists.
 *
 * <p>Three tests, because no two of them cover each other:
 * <ul>
 *   <li>{@link #everyDrivableToolPutsTheTokenOnTheWire()} proves the header actually reaches the
 *       socket, by recording what a real {@link HttpServer} received. A source scan cannot show
 *       that.</li>
 *   <li>{@link #noToolBuildsARequestWithoutAttachingTheToken()} covers the components that need an
 *       LLM or a validator to drive and so cannot be exercised here. A behavioural test cannot reach
 *       them, which is precisely how tool fourteen would reintroduce this.</li>
 *   <li>{@link #aTokenlessContextCannotProduceARequest()} covers the direction C4.4d missed
 *       entirely. Every site attaches the header only {@code if (token != null && !isEmpty())}, and
 *       nothing upstream established that precondition -- both controllers null-coalesced every
 *       other field to a default and passed the token through raw, so a tokenless chat request was
 *       accepted, returned 200, and produced exactly the pre-fix behaviour. Test 1 always supplies a
 *       good token and test 2 only checks for the literal text, so the guard had been verified in
 *       one direction only.</li>
 * </ul>
 */
class ToolAuthHeaderTest {

    private static final String TOKEN = "test-session-token-c44d";
    private static final String TENANT = "t-test";
    private static final String APP_ID = "11111111-2222-3333-4444-555555555555";

    private HttpServer server;
    private String baseUrl;

    /** Every request the stub received, in order: path -> Authorization header (null if absent). */
    private final List<Map.Entry<String, String>> received = new ArrayList<>();

    @BeforeEach
    void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            synchronized (received) {
                received.add(Map.entry(
                        exchange.getRequestURI().getPath(),
                        String.valueOf(exchange.getRequestHeaders().getFirst("Authorization"))));
            }
            // Bodies are shaped only well enough that the tools do not blow up before we have
            // recorded the request. What the tool does with the response is not under test.
            String body = "/schema".equals(exchange.getRequestURI().getPath())
                    ? "[]"
                    : "{\"apps\":[],\"entities\":[],\"pagesData\":[],\"pages\":[]}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
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
    @DisplayName("every tool that can be driven without an LLM puts the session token on the wire")
    void everyDrivableToolPutsTheTokenOnTheWire() {
        AgentContext context = AgentContext.create(TENANT, APP_ID, "user-1", "session-1", TOKEN);

        // Each of these previously issued at least one unauthenticated request.
        Map<Tool, Map<String, Object>> toDrive = new LinkedHashMap<>();
        toDrive.put(new ListAppsTool(baseUrl), Map.of());
        toDrive.put(new ListPagesTool(baseUrl), Map.of());
        toDrive.put(new ListWorkflowsTool(baseUrl), Map.of());
        toDrive.put(new ListEntitiesTool(baseUrl), Map.of());
        toDrive.put(new GetEntityDetailsTool(baseUrl), Map.of("entityName", "CustomerApplication"));

        toDrive.forEach((tool, args) -> {
            received.clear();
            tool.execute(new LinkedHashMap<>(args), context);

            // Without this the test passes vacuously for any tool that fails before it calls out.
            assertFalse(received.isEmpty(),
                    tool.getName() + " made no backend request at all, so this test proved nothing "
                            + "about it. Fix the harness, do not delete the assertion.");

            received.forEach(request -> assertTrue(
                    ("Bearer " + TOKEN).equals(request.getValue()),
                    tool.getName() + " sent " + request.getKey() + " with Authorization="
                            + request.getValue() + ", expected Bearer " + TOKEN
                            + ". An unauthenticated tool request 401s, and a failing tool burns an "
                            + "agent iteration rather than reporting an auth error."));
        });
    }

    @Test
    @DisplayName("no component builds an HttpRequest to the backend without attaching the token")
    void noToolBuildsARequestWithoutAttachingTheToken() throws IOException {
        List<String> offenders = new ArrayList<>();

        try (Stream<Path> sources = Files.walk(locateAiSources())) {
            for (Path source : sources.filter(ToolAuthHeaderTest::isScannableSource).toList()) {
                String text = Files.readString(source);
                int from = 0;
                while (true) {
                    int start = text.indexOf("HttpRequest.newBuilder()", from);
                    if (start < 0) {
                        break;
                    }
                    // Bound the window at the next builder so one request's header can never be
                    // credited to the previous request that lacks it.
                    int next = text.indexOf("HttpRequest.newBuilder()", start + 1);
                    int end = Math.min(next < 0 ? text.length() : next, start + 900);
                    if (!text.substring(start, Math.max(end, start)).contains("header(\"Authorization\"")) {
                        offenders.add(source.getFileName() + " @ line "
                                + (text.substring(0, start).split("\n", -1).length));
                    }
                    from = start + 1;
                }
            }
        }

        assertTrue(offenders.isEmpty(),
                "These backend requests are built without a session token and will 401: " + offenders
                        + ". Attach it with: if (context.token() != null && !context.token().isEmpty()) "
                        + "builder.header(\"Authorization\", \"Bearer \" + context.token());");
    }

    @Test
    @DisplayName("a blank token cannot be carried by a context, so no unauthenticated request can leave")
    void aTokenlessContextCannotProduceARequest() {
        received.clear();

        // The three shapes the HTTP layer can deliver: absent, empty, and whitespace-only.
        List<String> blankTokens = new ArrayList<>();
        blankTokens.add(null);
        blankTokens.add("");
        blankTokens.add("   ");

        for (String blank : blankTokens) {
            try {
                AgentContext context = AgentContext.create(TENANT, APP_ID, "user-1", "session-1", blank);

                // Reaching here means the constructor guard is gone. Do not just report the missing
                // throw -- drive a tool and report the consequence, which is the thing that actually
                // matters and the thing tests 1 and 2 both miss: test 1 always supplies a good token,
                // and test 2 only requires the literal text header("Authorization") to be present.
                new ListAppsTool(baseUrl).execute(new LinkedHashMap<>(), context);
                fail("AgentContext accepted a blank token (" + describe(blank) + ") and list_apps then "
                        + "sent " + received.size() + " request(s), the first with Authorization="
                        + (received.isEmpty() ? "n/a" : received.get(0).getValue())
                        + ". Every tool call such a context authorises 401s, and a failing tool burns "
                        + "an agent iteration rather than telling the user their session ended.");
            } catch (IllegalArgumentException expected) {
                // Correct: the type refuses to represent the broken state.
            }
        }

        assertTrue(received.isEmpty(),
                "A tokenless context must not be able to put anything on the wire, but the stub "
                        + "received: " + received);
    }

    private static String describe(String token) {
        return token == null ? "null" : "\"" + token + "\"";
    }

    /**
     * The boundary this guard protects is "an ai-builder component calling the backend", which is
     * wider than the tool package: {@code AiAgent.loadEntitySummary} issues one too, and a tool added
     * in a subpackage would be invisible to a non-recursive scan of one folder.
     *
     * <p>{@code llm/} is excluded by name and on purpose: {@code OpenAiLlmService} and
     * {@code GeminiLlmService} call third-party APIs and authenticate with their own provider keys,
     * so a session token would be meaningless there. That is a decision, not an artefact of where
     * the scan happens to start.
     */
    private static boolean isScannableSource(Path source) {
        String path = source.toString().replace('\\', '/');
        return path.endsWith(".java") && !path.contains("/com/appbana/ai/llm/");
    }

    /** Surefire runs with the module dir as cwd; tolerate being launched from the repo root. */
    private Path locateAiSources() {
        Path relative = Path.of("src/main/java/com/appbana/ai");
        return Files.isDirectory(relative) ? relative : Path.of("ai-builder").resolve(relative);
    }
}
