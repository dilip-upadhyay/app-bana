package com.appbana.api;

import com.appbana.JdbcManager;
import com.appbana.SchemaManager;
import com.appbana.config.AppConfig;
import com.appbana.model.EntitySchema;
import com.appbana.security.EntityAccessGuard;
import com.appbana.service.PasswordService;
import com.appbana.service.SessionService;
import com.appbana.service.SessionService.SessionData;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link GenericAppAuthController#login()} (Task S3.3 — Tenant Isolation Security Plan).
 *
 * <p>Covers all three review-round-1 findings this task closes:
 * <ul>
 *   <li>M5: the password is fetched-by-email and verified in Java — a BCrypt comparison for
 *       hashed rows, a constant-time plaintext fallback for legacy rows — never compared in a
 *       SQL {@code WHERE} clause.</li>
 *   <li>M6: a nonexistent app/entity and a wrong password now produce the identical generic 401.
 *       This endpoint can no longer be used as a cross-tenant/cross-app existence oracle by an
 *       unauthenticated caller.</li>
 *   <li>S3.1/S3.2 coordination note: a successful login mints a real, {@code EntityAccessGuard}
 *       -admissible session scoped to the app's own tenantId — not any other tenant value —
 *       proving the two features actually interoperate, not just that {@code createSession} was
 *       called with plausible-looking arguments.</li>
 * </ul>
 *
 * <p>Also fixes and locks in a pre-existing, tightly-coupled bug found while rewriting the login
 * SQL: {@code SchemaManager} always creates dynamic-entity columns quoted+uppercase (its shared
 * {@code quote()} helper), so an unquoted {@code WHERE email = ?} reference never matched the
 * real {@code "EMAIL"} column — every login attempt, correct or wrong password, 500'd before this
 * fix. Verified directly against Postgres (see commit message) before writing these tests.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GenericAppAuthControllerTest {

    private static final String TENANT_A = "t-s33-authctrl";
    private static final String APP_1 = "s33-auth-app";
    private static final String ENTITY_NAME = "AuthUser";

    private static final String ALICE_EMAIL = "alice@example.com";
    private static final String ALICE_PASSWORD = "Secret123!";
    private static final String BOB_EMAIL = "bob@example.com";
    private static final String BOB_PLAINTEXT_PASSWORD = "plainOldPass";

    private EntitySchema schema;
    private AppConfig cfg;

    @BeforeEach
    void setUp() {
        SessionService.clearAllSessions();
        cfg = new AppConfig();
        cleanUpFixtures();
        schema = saveFixtureSchema();
        insertRow(1L, ALICE_EMAIL, PasswordService.hashPassword(ALICE_PASSWORD), "Alice");
        // Bob simulates a pre-S4.2 legacy row: password stored as plain text, not a BCrypt hash.
        insertRow(2L, BOB_EMAIL, BOB_PLAINTEXT_PASSWORD, "Bob");
    }

    @AfterEach
    void tearDown() {
        SessionService.clearAllSessions();
        cleanUpFixtures();
    }

    // Scoped to this test class's own fixture tenant/app only — never a blanket statement
    // against the shared dev Postgres.
    private void cleanUpFixtures() {
        try (Connection c = JdbcManager.getConnection("default");
                Statement s = c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS \"" + physicalTableName().toUpperCase() + "\"");
            s.execute("DELETE FROM appbana_schemas WHERE tenant_id = '" + TENANT_A + "' AND app_id = '" + APP_1 + "'");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String physicalTableName() {
        EntitySchema s = new EntitySchema();
        s.setTenantId(TENANT_A);
        s.setAppId(APP_1);
        s.setName(ENTITY_NAME);
        return SchemaManager.getPhysicalTableName(s);
    }

    private EntitySchema saveFixtureSchema() {
        EntitySchema s = new EntitySchema();
        s.setName(ENTITY_NAME);
        s.setAppId(APP_1);
        s.setTenantId(TENANT_A);

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

        EntitySchema.Field name = new EntitySchema.Field();
        name.setName("name");
        name.setType("string");

        s.setFields(List.of(id, email, password, name));
        SchemaManager.saveSchema(s);
        return s;
    }

    private void insertRow(long id, String email, String storedPassword, String name) {
        String table = physicalTableName().toUpperCase();
        String sql = "INSERT INTO \"" + table + "\" (\"ID\",\"EMAIL\",\"PASSWORD\",\"NAME\") VALUES (?,?,?,?)";
        try (Connection c = JdbcManager.getConnection("default");
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.setString(2, email);
            ps.setString(3, storedPassword);
            ps.setString(4, name);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, String> loginBody(String appId, String tenantId, String entity, String email,
            String password) {
        Map<String, String> body = new HashMap<>();
        if (appId != null) body.put("appId", appId);
        if (tenantId != null) body.put("tenantId", tenantId);
        if (entity != null) body.put("entity", entity);
        if (email != null) body.put("email", email);
        if (password != null) body.put("password", password);
        return body;
    }

    private record LoginOutcome(int status, Object body) {
    }

    private LoginOutcome doLogin(Map<String, String> requestBody) {
        Router.HttpRequest req = mock(Router.HttpRequest.class);
        when(req.<Map<String, String>>readJson(any())).thenReturn(requestBody);
        Router.HttpResponse res = mock(Router.HttpResponse.class);

        GenericAppAuthController.login().accept(req, res);

        ArgumentCaptor<Integer> statusCap = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Object> bodyCap = ArgumentCaptor.forClass(Object.class);
        verify(res).json(statusCap.capture(), bodyCap.capture());
        return new LoginOutcome(statusCap.getValue(), bodyCap.getValue());
    }

    // ========================================
    // Happy path + S3.1/S3.2 coordination
    // ========================================

    @Test
    @DisplayName("Successful BCrypt login issues a session EntityAccessGuard rule (ii) actually admits")
    void testSuccessfulLoginIssuesGuardAdmissibleScopedSession() {
        LoginOutcome outcome = doLogin(loginBody(APP_1, TENANT_A, ENTITY_NAME, ALICE_EMAIL, ALICE_PASSWORD));

        assertEquals(200, outcome.status());
        Map<?, ?> respBody = (Map<?, ?>) outcome.body();
        assertEquals("Login successful", respBody.get("message"));

        String sessionId = (String) respBody.get("sessionId");
        assertNotNull(sessionId);
        assertEquals(sessionId, respBody.get("token"), "token and sessionId must be the same value");

        Map<?, ?> userPayload = (Map<?, ?>) respBody.get("user");
        assertEquals(ALICE_EMAIL, userPayload.get("email"));
        assertFalse(userPayload.containsKey("password"), "password must never appear in the response");

        SessionData session = SessionService.validateSession(sessionId);
        assertNotNull(session, "login must mint a real, retrievable session");
        assertEquals(TENANT_A, session.tenantId(), "session tenantId must be the APP's tenantId");
        assertEquals(APP_1, session.scopedAppId());
        assertEquals("1", session.userId(), "session userId should resolve from the row's own PK, not email");

        // Flagship coordination-note assertion (reviewer MEDIUM finding on S3.1/S3.2 handoff):
        // a session minted here must actually be admitted by EntityAccessGuard rule (ii), not
        // just look superficially correct. Proves the two features interoperate end-to-end.
        Router.HttpRequest guardReq = mock(Router.HttpRequest.class);
        when(guardReq.header("X-Session-Token")).thenReturn(sessionId);
        EntityAccessGuard.Result guardResult = EntityAccessGuard.check(guardReq, cfg, TENANT_A, APP_1, ENTITY_NAME);
        assertTrue(guardResult.allowed(),
                "a session minted by GenericAppAuthController.login() must be admitted by EntityAccessGuard rule (ii)");
    }

    @Test
    @DisplayName("M5: a legacy plaintext-stored password (pre-S4.2) still verifies via the fallback path")
    void testLegacyPlaintextPasswordStillVerifies() {
        LoginOutcome outcome = doLogin(loginBody(APP_1, TENANT_A, ENTITY_NAME, BOB_EMAIL, BOB_PLAINTEXT_PASSWORD));

        assertEquals(200, outcome.status());
        Map<?, ?> respBody = (Map<?, ?>) outcome.body();
        Map<?, ?> userPayload = (Map<?, ?>) respBody.get("user");
        assertEquals(BOB_EMAIL, userPayload.get("email"));
        assertFalse(userPayload.containsKey("password"));
    }

    // ========================================
    // S4.2: transparent rehash-on-login
    // ========================================

    @Test
    @DisplayName("S4.2: a successful legacy-plaintext login transparently rehashes the stored row to BCrypt")
    void testLegacyPlaintextPasswordIsRehashedToBcryptAfterLogin() {
        assertEquals(BOB_PLAINTEXT_PASSWORD, fetchStoredPassword(2L),
                "fixture precondition: Bob's row must start out as plain text, not a hash");

        LoginOutcome outcome = doLogin(loginBody(APP_1, TENANT_A, ENTITY_NAME, BOB_EMAIL, BOB_PLAINTEXT_PASSWORD));
        assertEquals(200, outcome.status(), "the rehash must be best-effort and never block a successful login");

        String storedAfterLogin = fetchStoredPassword(2L);
        assertNotEquals(BOB_PLAINTEXT_PASSWORD, storedAfterLogin,
                "row must no longer be stored as plain text after a successful login");
        assertTrue(PasswordService.looksLikeBcryptHash(storedAfterLogin),
                "row must be rewritten as a BCrypt hash");
        assertTrue(PasswordService.verifyPassword(BOB_PLAINTEXT_PASSWORD, storedAfterLogin),
                "the new hash must still verify against the original plaintext password");
    }

    @Test
    @DisplayName("S4.2: a row already rehashed to BCrypt is not rehashed again on a subsequent login (idempotent)")
    void testAlreadyRehashedPasswordIsNotRehashedAgainOnSubsequentLogin() {
        LoginOutcome first = doLogin(loginBody(APP_1, TENANT_A, ENTITY_NAME, BOB_EMAIL, BOB_PLAINTEXT_PASSWORD));
        assertEquals(200, first.status());
        String storedAfterFirstLogin = fetchStoredPassword(2L);
        assertTrue(PasswordService.looksLikeBcryptHash(storedAfterFirstLogin));

        LoginOutcome second = doLogin(loginBody(APP_1, TENANT_A, ENTITY_NAME, BOB_EMAIL, BOB_PLAINTEXT_PASSWORD));
        assertEquals(200, second.status(), "the now-BCrypt row must still verify via the normal BCrypt path");

        String storedAfterSecondLogin = fetchStoredPassword(2L);
        assertEquals(storedAfterFirstLogin, storedAfterSecondLogin,
                "a row that already looks like a BCrypt hash must be left untouched by a later login - "
                        + "rehashing an already-hashed value would corrupt the credential");
    }

    private String fetchStoredPassword(long id) {
        String table = physicalTableName().toUpperCase();
        String sql = "SELECT \"PASSWORD\" FROM \"" + table + "\" WHERE \"ID\" = ?";
        try (Connection c = JdbcManager.getConnection("default");
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (var rs = ps.executeQuery()) {
                assertTrue(rs.next(), "fixture row " + id + " must exist");
                return rs.getString(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Default entity name ('User') is used when the request omits 'entity'")
    void testDefaultsToUserEntityWhenEntityFieldOmitted() throws SQLException {
        // Save a second fixture schema literally named "User" to exercise the default branch.
        EntitySchema userSchema = new EntitySchema();
        userSchema.setName("User");
        userSchema.setAppId(APP_1);
        userSchema.setTenantId(TENANT_A);
        EntitySchema.Field id = new EntitySchema.Field("id", "long", true, true, null);
        EntitySchema.Field email = new EntitySchema.Field("email", "string", false, false, null);
        EntitySchema.Field password = new EntitySchema.Field("password", "string", false, false, null);
        userSchema.setFields(List.of(id, email, password));
        SchemaManager.saveSchema(userSchema);
        try {
            String table = SchemaManager.getPhysicalTableName(userSchema).toUpperCase();
            String sql = "INSERT INTO \"" + table + "\" (\"ID\",\"EMAIL\",\"PASSWORD\") VALUES (?,?,?)";
            try (Connection c = JdbcManager.getConnection("default");
                    PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setLong(1, 1L);
                ps.setString(2, "carol@example.com");
                ps.setString(3, PasswordService.hashPassword("Carol123!"));
                ps.executeUpdate();
            }

            Map<String, String> body = loginBody(APP_1, TENANT_A, null, "carol@example.com", "Carol123!");
            LoginOutcome outcome = doLogin(body);
            assertEquals(200, outcome.status());
        } finally {
            try (Connection c = JdbcManager.getConnection("default");
                    Statement s = c.createStatement()) {
                s.execute("DROP TABLE IF EXISTS \""
                        + SchemaManager.getPhysicalTableName(userSchema).toUpperCase() + "\"");
                s.execute("DELETE FROM appbana_schemas WHERE tenant_id = '" + TENANT_A + "' AND app_id = '" + APP_1
                        + "' AND name = 'User'");
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    // ========================================
    // M6: existence-oracle normalization
    // ========================================

    @Test
    @DisplayName("M6: wrong password produces a generic 401")
    void testWrongPasswordReturnsGeneric401() {
        LoginOutcome outcome = doLogin(loginBody(APP_1, TENANT_A, ENTITY_NAME, ALICE_EMAIL, "WrongPassword!"));

        assertEquals(401, outcome.status());
        assertEquals(Map.of("error", "Invalid credentials"), outcome.body());
    }

    @Test
    @DisplayName("M6: unknown email produces the identical generic 401 as a wrong password")
    void testUnknownEmailReturnsIdenticalGeneric401AsWrongPassword() {
        LoginOutcome wrongPassword = doLogin(loginBody(APP_1, TENANT_A, ENTITY_NAME, ALICE_EMAIL, "WrongPassword!"));
        LoginOutcome unknownEmail = doLogin(
                loginBody(APP_1, TENANT_A, ENTITY_NAME, "nobody-here@example.com", "whatever"));

        assertEquals(wrongPassword.status(), unknownEmail.status());
        assertEquals(wrongPassword.body(), unknownEmail.body());
    }

    @Test
    @DisplayName("M6: nonexistent entity produces the identical generic 401 as a wrong password (not 404)")
    void testNonexistentEntityReturnsIdenticalGeneric401AsWrongPassword() {
        LoginOutcome wrongPassword = doLogin(loginBody(APP_1, TENANT_A, ENTITY_NAME, ALICE_EMAIL, "WrongPassword!"));
        LoginOutcome nonexistentEntity = doLogin(
                loginBody(APP_1, TENANT_A, "NoSuchEntity", ALICE_EMAIL, ALICE_PASSWORD));

        assertEquals(wrongPassword.status(), nonexistentEntity.status());
        assertEquals(wrongPassword.body(), nonexistentEntity.body());
    }

    @Test
    @DisplayName("M6: nonexistent app produces the identical generic 401 as a wrong password (not 404)")
    void testNonexistentAppReturnsIdenticalGeneric401AsWrongPassword() {
        LoginOutcome wrongPassword = doLogin(loginBody(APP_1, TENANT_A, ENTITY_NAME, ALICE_EMAIL, "WrongPassword!"));
        LoginOutcome nonexistentApp = doLogin(
                loginBody("totally-bogus-app-999", TENANT_A, ENTITY_NAME, ALICE_EMAIL, ALICE_PASSWORD));

        assertEquals(wrongPassword.status(), nonexistentApp.status());
        assertEquals(wrongPassword.body(), nonexistentApp.body());
    }

    @Test
    @DisplayName("M6: wrong tenant for a real app/entity produces the identical generic 401 as a wrong password")
    void testWrongTenantReturnsIdenticalGeneric401AsWrongPassword() {
        LoginOutcome wrongPassword = doLogin(loginBody(APP_1, TENANT_A, ENTITY_NAME, ALICE_EMAIL, "WrongPassword!"));
        LoginOutcome wrongTenant = doLogin(
                loginBody(APP_1, "t-some-other-tenant", ENTITY_NAME, ALICE_EMAIL, ALICE_PASSWORD));

        assertEquals(wrongPassword.status(), wrongTenant.status());
        assertEquals(wrongPassword.body(), wrongTenant.body());
    }

    @Test
    @DisplayName("M6 (round-60 MEDIUM): a blank password against a BCrypt-backed account is a 400, never a 500")
    void testBlankPasswordAgainstBcryptRowReturns400NotServerError() {
        // Before the fix: password="" slipped past the null-only guard, reached
        // verifyCredential(""), and PasswordService.verifyPassword("", hash) threw
        // IllegalArgumentException for Alice's BCrypt-hashed row specifically, propagating to the
        // catch-all as a 500 - while an unknown email or a plaintext row stayed a normal 401. That
        // split let an unauthenticated caller fingerprint which emails are backed by a BCrypt hash.
        LoginOutcome outcome = doLogin(loginBody(APP_1, TENANT_A, ENTITY_NAME, ALICE_EMAIL, ""));

        assertEquals(400, outcome.status());
        assertEquals(Map.of("error", "Email and password are required"), outcome.body());
    }

    @Test
    @DisplayName("M6 (round-60 MEDIUM): blank password produces the identical 400 whether or not the account exists")
    void testBlankPasswordProducesIdentical400RegardlessOfAccountExistence() {
        LoginOutcome knownBcryptAccount = doLogin(loginBody(APP_1, TENANT_A, ENTITY_NAME, ALICE_EMAIL, ""));
        LoginOutcome knownPlaintextAccount = doLogin(loginBody(APP_1, TENANT_A, ENTITY_NAME, BOB_EMAIL, ""));
        LoginOutcome unknownAccount = doLogin(
                loginBody(APP_1, TENANT_A, ENTITY_NAME, "nobody-here@example.com", ""));

        assertEquals(knownBcryptAccount.status(), knownPlaintextAccount.status());
        assertEquals(knownBcryptAccount.body(), knownPlaintextAccount.body());
        assertEquals(knownBcryptAccount.status(), unknownAccount.status());
        assertEquals(knownBcryptAccount.body(), unknownAccount.body());
    }

    // ========================================
    // Pre-existing validation behavior (unchanged) — quick regression check
    // ========================================

    @Test
    @DisplayName("Missing appId => 400 (unchanged)")
    void testMissingAppIdReturns400() {
        LoginOutcome outcome = doLogin(loginBody(null, TENANT_A, ENTITY_NAME, ALICE_EMAIL, ALICE_PASSWORD));
        assertEquals(400, outcome.status());
    }

    @Test
    @DisplayName("Missing email/password => 400 (unchanged)")
    void testMissingEmailOrPasswordReturns400() {
        LoginOutcome outcome = doLogin(loginBody(APP_1, TENANT_A, ENTITY_NAME, null, null));
        assertEquals(400, outcome.status());
    }
}
