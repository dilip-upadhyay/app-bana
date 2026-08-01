package com.appbana.server.routes;

import com.appbana.ApiServer;
import com.appbana.config.AppConfig;
import com.appbana.config.ConfigManager;
import com.appbana.service.SessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * S1.9 — {@code SchemaRoutes.java}'s 6 {@code authEnabled}-gated call sites ({@code GET
 * /api/endpoints}, {@code GET /openapi.json}, {@code GET /schema}, {@code GET /schema/{name}},
 * {@code POST /schema}, {@code DELETE /schema/{name}}) used to extract a caller's token via
 * {@link com.appbana.service.AuthService#extractToken} (which reads {@code X-Session-Token}
 * first — the H8-class bug) and check it with {@code hasRead()}/{@code hasWrite()}, a separate,
 * weaker credential tier than the admin token. Converted to {@code extractServiceToken()} +
 * {@code hasAdmin()} uniformly (readToken retired — plan Non-goals, R6-1).
 *
 * <p>Behavior only changes when an admin token is actually configured
 * ({@code authEnabled(cfg)==true}) — the shipped {@code config.json} ships both
 * {@code adminToken}/{@code readToken} null, so under the default configuration these 6 gates are
 * skipped entirely regardless of this fix (same shipped-config discipline that caught B2, see
 * {@link AppRoutesTenantIsolationTest}). These tests deliberately configure both tokens on the
 * live {@link ConfigManager} singleton for the duration of each test (restoring the prior, null
 * values in {@link #restoreTokens()}) specifically to exercise the branch the shipped config
 * never reaches.
 *
 * <p><b>{@code /schema} is independently gated by {@link com.appbana.middleware.SessionMiddleware}
 * itself</b> ({@code isExcludedPath} hard-excludes it from the public/entity carve-outs — it
 * always requires a real session), a layer entirely separate from the internal
 * {@code hasAdmin()} check this task touches. Every {@code /schema} request below therefore also
 * carries a real session via {@code X-Session-Token} so the assertions isolate the *route's own*
 * gate rather than accidentally passing because {@code SessionMiddleware} rejected a bogus
 * session value first (a real trap here: a request with only a made-up {@code X-Session-Token}
 * value 401s at the middleware layer regardless of this fix, which would silently pass a test
 * that never actually exercises the route's own {@code hasAdmin()} check). {@code /api/endpoints}
 * has no such middleware gate (it matches {@code ENTITY_API_PATTERN}), so its tests don't need one.
 */
public class SchemaRoutesAdminTokenTest {

    private static final int PORT = 18093;
    private static final String BASE_URL = "http://localhost:" + PORT;
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String ADMIN_TOKEN = "s19-admin-xyz";
    private static final String READ_TOKEN = "s19-read-abc";

    private String originalAdminToken;
    private String originalReadToken;

    @BeforeAll
    public static void startServer() throws Exception {
        ApiServer.startJdk(PORT);
    }

    @BeforeEach
    public void configureTokens() {
        AppConfig cfg = ConfigManager.getConfig();
        originalAdminToken = cfg.getAdminToken();
        originalReadToken = cfg.getReadToken();
        cfg.setAdminToken(ADMIN_TOKEN);
        cfg.setReadToken(READ_TOKEN);
    }

    @AfterEach
    public void restoreTokens() {
        AppConfig cfg = ConfigManager.getConfig();
        cfg.setAdminToken(originalAdminToken);
        cfg.setReadToken(originalReadToken);
    }

    private String createOrdinarySession() {
        return SessionService.createSession("s19_ordinary_user", "t_s19_ordinary").sessionId();
    }

    @Test
    public void testShippedConfigHadBothTokensNullBeforeThisTestOverrodeThem() {
        assertNull(originalAdminToken, "This fixture assumes shipped config.json has adminToken=null");
        assertNull(originalReadToken, "This fixture assumes shipped config.json has readToken=null");
    }

    @Test
    public void testReadTokenAloneNoLongerAdmitsGetEndpoints() throws Exception {
        HttpResponse<String> res = get("/api/endpoints", READ_TOKEN);
        assertEquals(401, res.statusCode(),
                "The old read-only token must no longer satisfy the now-hasAdmin-only gate on /api/endpoints");
    }

    @Test
    public void testAdminTokenStillAdmitsGetEndpoints() throws Exception {
        HttpResponse<String> res = get("/api/endpoints", ADMIN_TOKEN);
        assertEquals(200, res.statusCode(), "The real admin token must still be admitted on /api/endpoints");
    }

    @Test
    public void testReadTokenAloneNoLongerAdmitsGetSchema() throws Exception {
        HttpResponse<String> res = getWithSession("/schema", createOrdinarySession(), READ_TOKEN);
        assertEquals(401, res.statusCode(),
                "The old read-only token must no longer satisfy the now-hasAdmin-only gate on GET /schema, "
                        + "even from a caller with an otherwise-valid session");
    }

    @Test
    public void testAdminTokenStillAdmitsGetSchema() throws Exception {
        HttpResponse<String> res = getWithSession("/schema", createOrdinarySession(), ADMIN_TOKEN);
        assertEquals(200, res.statusCode(), "The real admin token must still be admitted on GET /schema");
    }

    @Test
    public void testSessionTokenHeaderNeverSatisfiesTheAdminGateOnReads() throws Exception {
        // H8-class check: a value sent via X-Session-Token (even equal to the real admin token)
        // must never satisfy this gate — extractServiceToken() only reads X-AppBana-Token /
        // Authorization: Bearer, never X-Session-Token (unlike the retired extractToken()).
        // Only meaningful on a route with no independent SessionMiddleware gate of its own —
        // otherwise a bogus session value 401s at the middleware layer regardless of this fix,
        // which is exactly why /schema's own equivalent isn't tested this way (see class Javadoc).
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/endpoints"))
                .header("X-Session-Token", ADMIN_TOKEN)
                .GET()
                .build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, res.statusCode(),
                "A value sent via X-Session-Token must never satisfy the admin gate, even if it equals the admin token");
    }

    @Test
    public void testOrdinarySessionAloneDoesNotAdmitPostSchema() throws Exception {
        // A real, valid session with no admin token must not be able to write a schema once
        // authEnabled(cfg) is true — proves the hasWrite -> hasAdmin conversion didn't loosen
        // anything on the write side (hasWrite() already equaled hasAdmin() before this task, so
        // the only real change there is the extraction method, which this route's own
        // SessionMiddleware gate makes untestable in isolation — see class Javadoc).
        String body = MAPPER.writeValueAsString(Map.of(
                "name", "s19_schema_probe",
                "tenantId", "t_s19_probe",
                "appId", "app_s19_probe",
                "fields", List.of()
        ));
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/schema"))
                .header("Content-Type", "application/json")
                .header("X-Session-Token", createOrdinarySession())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, res.statusCode(),
                "POST /schema must reject a valid-but-non-admin session once an admin token is configured");
    }

    private HttpResponse<String> get(String path, String serviceToken) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .header("X-AppBana-Token", serviceToken)
                .GET()
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getWithSession(String path, String sessionId, String serviceToken) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .header("X-Session-Token", sessionId)
                .header("X-AppBana-Token", serviceToken)
                .GET()
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }
}
