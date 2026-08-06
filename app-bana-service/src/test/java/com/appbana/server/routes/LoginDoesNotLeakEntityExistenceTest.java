package com.appbana.server.routes;

import com.appbana.ApiServer;
import com.appbana.JdbcManager;
import com.appbana.SchemaManager;
import com.appbana.model.EntitySchema;
import com.appbana.service.PasswordService;
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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * LoginDoesNotLeakEntityExistenceTest — S3.6 (docs/planning/TENANT_ISOLATION_IMPLEMENTATION_TASKS.md).
 *
 * <p>{@code GenericAppAuthControllerTest} (S3.3, M6) already proves — exhaustively, at the mock
 * level — that {@link com.appbana.api.GenericAppAuthController#login()} returns one generic,
 * byte-identical 401 for every "this should not succeed" case, calling the controller method
 * directly and bypassing {@code SessionMiddleware} + {@code Router} entirely.
 *
 * <p>This class is deliberately thinner (4 failure cases, not the full M5/M6 matrix) and proves
 * the same normalization survives <b>real HTTP dispatch</b> through the actual
 * {@code Router}/{@code SessionMiddleware} stack — the same gap this task's
 * {@code SessionMiddleware} fix closed (before that fix, every one of these calls would have
 * 401'd with "Missing session token" before ever reaching the controller, which would have made
 * this test pass for entirely the wrong reason). A positive control (correct credentials succeed)
 * proves the fixture is sound, not just that the endpoint always fails closed.
 */
public class LoginDoesNotLeakEntityExistenceTest {

    private static final int PORT = 18107;
    private static final String BASE_URL = "http://localhost:" + PORT;
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String TENANT = "t-s36-ll-tenant";
    private static final String WRONG_TENANT = "t-s36-ll-wrong-tenant";
    private static final String APP_1 = "s36-ll-app-1";
    private static final String NONEXISTENT_APP = "s36-ll-app-does-not-exist";
    private static final String ENTITY_NAME = "S36LoginLeakUser";
    private static final String NONEXISTENT_ENTITY = "S36LoginLeakEntityDoesNotExist";

    private static final String ALICE_EMAIL = "alice-s36-ll@example.com";
    private static final String ALICE_PASSWORD = "Secret123!";
    private static final String UNKNOWN_EMAIL = "nobody-s36-ll@example.com";

    // Must match GenericAppAuthController.GENERIC_AUTH_FAILURE exactly — a single-entry Map.of
    // serializes deterministically, so a byte-exact body comparison is safe here.
    private static final String EXPECTED_GENERIC_FAILURE_BODY = "{\"error\":\"Invalid credentials\"}";

    @BeforeAll
    public static void startServer() throws Exception {
        ApiServer.startJdk(PORT);
    }

    @BeforeEach
    public void setUp() throws Exception {
        SessionService.clearAllSessions();
        cleanUpFixtures();
        saveLoginSchema();
        insertLoginUser(1L, ALICE_EMAIL, PasswordService.hashPassword(ALICE_PASSWORD));
    }

    @AfterEach
    public void tearDown() {
        SessionService.clearAllSessions();
        cleanUpFixtures();
    }

    // Scoped to this test class's own fixture tenant/app only — never a blanket statement
    // against the shared dev Postgres (see RoleRoutesSecurityTest for why that matters).
    private void cleanUpFixtures() {
        try (Connection c = JdbcManager.getConnection("default");
                Statement s = c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS \"" + physicalTableName().toUpperCase() + "\"");
            s.execute("DELETE FROM appbana_schemas WHERE tenant_id = '" + TENANT + "' AND app_id = '" + APP_1 + "'");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String physicalTableName() {
        EntitySchema s = new EntitySchema();
        s.setTenantId(TENANT);
        s.setAppId(APP_1);
        s.setName(ENTITY_NAME);
        return SchemaManager.getPhysicalTableName(s);
    }

    private void saveLoginSchema() {
        EntitySchema s = new EntitySchema();
        s.setName(ENTITY_NAME);
        s.setAppId(APP_1);
        s.setTenantId(TENANT);

        EntitySchema.Field id = new EntitySchema.Field();
        id.setName("id");
        id.setType("long");
        id.setPrimaryKey(true);
        id.setAutoIncrement(true);

        EntitySchema.Field email = new EntitySchema.Field();
        email.setName("email");
        email.setType("string");

        EntitySchema.Field password = new EntitySchema.Field();
        password.setName("password");
        password.setType("string");

        s.setFields(List.of(id, email, password));
        SchemaManager.saveSchema(s);
    }

    private void insertLoginUser(long id, String email, String hashedPassword) {
        String table = physicalTableName().toUpperCase();
        String sql = "INSERT INTO \"" + table + "\" (\"ID\",\"EMAIL\",\"PASSWORD\") VALUES (?,?,?)";
        try (Connection c = JdbcManager.getConnection("default");
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.setString(2, email);
            ps.setString(3, hashedPassword);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private HttpResponse<String> login(String appId, String tenantId, String entity, String email, String password)
            throws Exception {
        Map<String, String> req = new HashMap<>();
        req.put("appId", appId);
        req.put("tenantId", tenantId);
        req.put("entity", entity);
        req.put("email", email);
        req.put("password", password);
        String body = MAPPER.writeValueAsString(req);
        HttpRequest httpReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/runtime/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(httpReq, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    public void testCorrectCredentialsSucceedAsPositiveControl() throws Exception {
        HttpResponse<String> res = login(APP_1, TENANT, ENTITY_NAME, ALICE_EMAIL, ALICE_PASSWORD);
        assertEquals(200, res.statusCode(), () -> "correct credentials must succeed: " + res.body());
        assertNotNull(MAPPER.readTree(res.body()).get("token"), "successful login must carry a session token");
    }

    @Test
    public void testWrongPasswordReturnsGenericFailure() throws Exception {
        HttpResponse<String> res = login(APP_1, TENANT, ENTITY_NAME, ALICE_EMAIL, "definitely-wrong-password");
        assertEquals(401, res.statusCode());
        assertEquals(EXPECTED_GENERIC_FAILURE_BODY, res.body());
    }

    @Test
    public void testUnknownEmailReturnsIdenticalGenericFailure() throws Exception {
        HttpResponse<String> res = login(APP_1, TENANT, ENTITY_NAME, UNKNOWN_EMAIL, ALICE_PASSWORD);
        assertEquals(401, res.statusCode());
        assertEquals(EXPECTED_GENERIC_FAILURE_BODY, res.body());
    }

    @Test
    public void testNonexistentEntityReturnsIdenticalGenericFailure() throws Exception {
        HttpResponse<String> res = login(APP_1, TENANT, NONEXISTENT_ENTITY, ALICE_EMAIL, ALICE_PASSWORD);
        assertEquals(401, res.statusCode());
        assertEquals(EXPECTED_GENERIC_FAILURE_BODY, res.body());
    }

    @Test
    public void testNonexistentAppReturnsIdenticalGenericFailure() throws Exception {
        HttpResponse<String> res = login(NONEXISTENT_APP, TENANT, ENTITY_NAME, ALICE_EMAIL, ALICE_PASSWORD);
        assertEquals(401, res.statusCode());
        assertEquals(EXPECTED_GENERIC_FAILURE_BODY, res.body());
    }

    @Test
    public void testWrongTenantReturnsIdenticalGenericFailure() throws Exception {
        // Same appId/entity/email/password as the positive control, but claiming a different
        // tenantId than the one the schema was actually saved under - SchemaManager.loadSchema
        // scopes by (appId, entityName, tenantId), so this must miss the schema and 401 exactly
        // like every other case here, never a distinguishable 404/500.
        HttpResponse<String> res = login(APP_1, WRONG_TENANT, ENTITY_NAME, ALICE_EMAIL, ALICE_PASSWORD);
        assertEquals(401, res.statusCode());
        assertEquals(EXPECTED_GENERIC_FAILURE_BODY, res.body());
    }
}
