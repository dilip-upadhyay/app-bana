package com.appbana.server.routes;

import com.appbana.ApiServer;
import com.appbana.JdbcManager;
import com.appbana.SchemaManager;
import com.appbana.approval.UserRoleService;
import com.appbana.model.EntitySchema;
import com.appbana.service.SessionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UserRoutesTest — Task C3.3.
 *
 * Real HTTP coverage for {@code GET /api/users/me}, the endpoint the runtime
 * uses to discover which entities the caller may check.
 *
 * <p>The most important case here is {@link #testRouteIsNotSwallowedByEntityWildcard()}:
 * {@code /api/users/me} matches {@code SessionMiddleware.ENTITY_API_PATTERN}
 * ({@code /api/{entity}/{id}}), so without an explicit carve-out the middleware
 * skips session validation, the {@code userId} attribute is never set, and the
 * handler 401s every caller no matter how valid their token is.</p>
 */
public class UserRoutesTest {

    private static final String BASE_URL = "http://localhost:18091";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String TENANT_ID = "t_users_me";
    private static final String APP_ID = "app_users_me";

    private static String checkerToken;
    private static String makerToken;
    private static String strangerToken;

    @BeforeAll
    public static void startServerAndPrepareDb() throws Exception {
        ApiServer.startJdk(18091);

        try (Connection c = JdbcManager.getConnection("default");
             Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS appbana_user_roles (" +
                    "tenant_id VARCHAR(255) NOT NULL, app_id VARCHAR(255) NOT NULL, " +
                    "entity_name VARCHAR(255) NOT NULL, user_id VARCHAR(255) NOT NULL, " +
                    "role VARCHAR(20) NOT NULL CHECK (role IN ('maker', 'checker', 'both')), " +
                    "granted_by VARCHAR(255) NOT NULL, granted_at TIMESTAMP NOT NULL DEFAULT NOW(), " +
                    "PRIMARY KEY (tenant_id, app_id, entity_name, user_id))");
        }

        checkerToken = SessionService.createSession("carol_checker").sessionId();
        makerToken = SessionService.createSession("mike_maker").sessionId();
        strangerToken = SessionService.createSession("sam_stranger").sessionId();

        // UserRoutes only reports a grant for entities that actually have an approval
        // workflow enabled (see UserRoutes#collectEntityRoles) — a raw appbana_user_roles
        // row with no matching approvalRequired=true schema is intentionally filtered out.
        // Both entities under test need a real schema for that filter to pass.
        EntitySchema.Field idField = new EntitySchema.Field("id", "integer", true, true, null);
        for (String entityName : List.of("Invoice", "Vendor")) {
            EntitySchema schema = new EntitySchema(entityName, List.of(idField));
            schema.setTenantId(TENANT_ID);
            schema.setAppId(APP_ID);
            schema.setApprovalRequired(true);
            SchemaManager.saveSchema(schema);
        }
    }

    @BeforeEach
    public void seedRoles() throws Exception {
        try (Connection c = JdbcManager.getConnection("default");
             Statement s = c.createStatement()) {
            s.execute("DELETE FROM appbana_user_roles WHERE tenant_id = '" + TENANT_ID + "'");
        }
        UserRoleService.grantRole(TENANT_ID, APP_ID, "Invoice", "carol_checker", UserRoleService.Role.CHECKER, "system");
        UserRoleService.grantRole(TENANT_ID, APP_ID, "Vendor", "carol_checker", UserRoleService.Role.BOTH, "system");
        UserRoleService.grantRole(TENANT_ID, APP_ID, "Invoice", "mike_maker", UserRoleService.Role.MAKER, "system");
    }

    private HttpResponse<String> get(String path, String sessionToken) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder().uri(URI.create(BASE_URL + path)).GET();
        if (sessionToken != null) b.header("X-Session-Token", sessionToken);
        return HTTP_CLIENT.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> body(HttpResponse<String> res) throws Exception {
        return MAPPER.readValue(res.body(), new TypeReference<Map<String, Object>>() {});
    }

    @Test
    public void testUnauthenticatedReturns401() throws Exception {
        HttpResponse<String> res = get("/api/users/me", null);
        assertEquals(401, res.statusCode(), "No session must be rejected: " + res.body());
    }

    /**
     * Regression guard. `/api/users/me` looks exactly like `/api/{entity}/{id}`
     * to SessionMiddleware. If the carve-out is ever removed, the middleware
     * silently stops populating `userId` and this endpoint 401s every caller —
     * a failure that looks like a broken token rather than a routing bug.
     */
    @Test
    public void testRouteIsNotSwallowedByEntityWildcard() throws Exception {
        HttpResponse<String> res = get("/api/users/me", checkerToken);
        assertEquals(200, res.statusCode(),
                "A valid session must reach the handler — a 401 here means SessionMiddleware "
                        + "treated /api/users/me as an entity API and skipped session validation. "
                        + "Body: " + res.body());
        assertEquals("carol_checker", body(res).get("userId"));
    }

    @Test
    public void testIdentityOnlyWithoutAppId() throws Exception {
        HttpResponse<String> res = get("/api/users/me", checkerToken);
        assertEquals(200, res.statusCode());
        Map<String, Object> b = body(res);
        assertEquals("carol_checker", b.get("userId"));
        assertEquals(Map.of(), b.get("entityRoles"), "No appId means no role scope to report");
        assertNull(b.get("isAppOwner"), "Ownership is meaningless without an app");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testReportsCheckerRolePerEntity() throws Exception {
        HttpResponse<String> res = get("/api/users/me?tenantId=" + TENANT_ID + "&appId=" + APP_ID, checkerToken);
        assertEquals(200, res.statusCode(), res.body());

        Map<String, Object> roles = (Map<String, Object>) body(res).get("entityRoles");
        assertNotNull(roles);
        assertTrue(roles.containsKey("Invoice"), "Expected an Invoice grant: " + roles);

        Map<String, Object> invoice = (Map<String, Object>) roles.get("Invoice");
        assertEquals(Boolean.TRUE, invoice.get("isChecker"));
        assertEquals(Boolean.FALSE, invoice.get("isMaker"));
        assertEquals(List.of("checker"), invoice.get("roles"));
    }

    /**
     * BOTH must expand to maker + checker exactly as {@code UserRoleService.getUserRoles}
     * expands it, otherwise the queue UI and the backend guards disagree about
     * who may act.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testBothRoleExpandsToMakerAndChecker() throws Exception {
        HttpResponse<String> res = get("/api/users/me?tenantId=" + TENANT_ID + "&appId=" + APP_ID, checkerToken);
        Map<String, Object> roles = (Map<String, Object>) body(res).get("entityRoles");
        Map<String, Object> vendor = (Map<String, Object>) roles.get("Vendor");

        assertNotNull(vendor, "Expected a Vendor grant: " + roles);
        assertEquals(Boolean.TRUE, vendor.get("isChecker"));
        assertEquals(Boolean.TRUE, vendor.get("isMaker"));
        assertEquals(List.of("both", "checker", "maker"), vendor.get("roles"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testMakerIsNotReportedAsChecker() throws Exception {
        HttpResponse<String> res = get("/api/users/me?tenantId=" + TENANT_ID + "&appId=" + APP_ID, makerToken);
        Map<String, Object> roles = (Map<String, Object>) body(res).get("entityRoles");
        Map<String, Object> invoice = (Map<String, Object>) roles.get("Invoice");

        assertEquals(Boolean.TRUE, invoice.get("isMaker"));
        assertEquals(Boolean.FALSE, invoice.get("isChecker"),
                "A maker must never be offered a checker queue");
    }

    /**
     * The endpoint reports only the caller's own roles. A user with no grants
     * gets an empty map, not another user's roles and not a 403 — an empty
     * result is the correct answer to "what may I check here?".
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testUserWithNoGrantsGetsEmptyRoles() throws Exception {
        HttpResponse<String> res = get("/api/users/me?tenantId=" + TENANT_ID + "&appId=" + APP_ID, strangerToken);
        assertEquals(200, res.statusCode(), res.body());

        Map<String, Object> b = body(res);
        assertEquals("sam_stranger", b.get("userId"));
        assertTrue(((Map<String, Object>) b.get("entityRoles")).isEmpty(),
                "A user with no grants must see no entities: " + b.get("entityRoles"));
        assertEquals(Boolean.FALSE, b.get("isAppOwner"));
    }

    /**
     * Roles are scoped per app. Asking about a different app must not leak the
     * grants the caller holds elsewhere.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testRolesAreScopedToTheRequestedApp() throws Exception {
        HttpResponse<String> res = get("/api/users/me?tenantId=" + TENANT_ID + "&appId=some_other_app", checkerToken);
        assertEquals(200, res.statusCode(), res.body());
        assertTrue(((Map<String, Object>) body(res).get("entityRoles")).isEmpty(),
                "Grants from one app must not appear under another");
    }
}
