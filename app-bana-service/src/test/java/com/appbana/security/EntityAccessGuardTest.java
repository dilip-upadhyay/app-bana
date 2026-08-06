package com.appbana.security;

import com.appbana.JdbcManager;
import com.appbana.SchemaManager;
import com.appbana.api.Router;
import com.appbana.config.AppConfig;
import com.appbana.model.EntitySchema;
import com.appbana.service.SessionService;
import com.appbana.service.SessionService.SessionData;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for EntityAccessGuard (Task S3.2 — Tenant Isolation Security Plan).
 *
 * Covers both entry points: the path-segmented core check (tenantId/appId already separate
 * params) and the packed-key entry point that resolves them via {@code SchemaManager.loadSchema}.
 * Exercises the full allow-rule order — membership (any role), scopedAppId+tenantId match
 * (hardened beyond the plan's literal one-liner — see class Javadoc), publicRead+GET, and the
 * break-glass admin token evaluated last — plus the 401-vs-403 default-deny split.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EntityAccessGuardTest {

    private static final String TENANT_A = "t-s32-guard-a";
    private static final String TENANT_B = "t-s32-guard-b";
    private static final String APP_1 = "s32-guard-app-1";
    private static final String ENTITY_NAME = "S32Entity";
    private static final String PACKED_KEY = TENANT_A + "_" + APP_1 + "_" + ENTITY_NAME;

    private Router.HttpRequest req;
    private AppConfig cfg;

    @BeforeEach
    void setUp() {
        SessionService.clearAllSessions();
        req = mock(Router.HttpRequest.class);
        cfg = new AppConfig();
        cfg.setAdminToken("admin-token-xyz");
        cfg.setReadToken("read-token-abc");
        cleanUpFixtures();
    }

    @AfterEach
    void tearDown() {
        SessionService.clearAllSessions();
        cleanUpFixtures();
    }

    // Scoped to this test class's own fixture tenant/app only — never a blanket DELETE against
    // the shared dev Postgres (see RoleRoutesSecurityTest for why that matters).
    private void cleanUpFixtures() {
        try (Connection c = JdbcManager.getConnection("default");
             Statement s = c.createStatement()) {
            s.execute("DELETE FROM appbana_app_members WHERE tenant_id IN ('" + TENANT_A + "', '" + TENANT_B
                    + "') AND app_id = '" + APP_1 + "'");
            s.execute("DELETE FROM appbana_schemas WHERE tenant_id = '" + TENANT_A + "' AND app_id = '" + APP_1 + "'");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void saveFixtureSchema() {
        EntitySchema schema = new EntitySchema();
        schema.setName(ENTITY_NAME);
        schema.setAppId(APP_1);
        schema.setTenantId(TENANT_A);

        EntitySchema.Field idField = new EntitySchema.Field();
        idField.setName("id");
        idField.setType("long");
        idField.setPrimaryKey(true);
        schema.setFields(List.of(idField));

        SchemaManager.saveSchema(schema);
    }

    // ========================================
    // Core check (entry point b): no credentials / no rule matched
    // ========================================

    @Test
    @DisplayName("No credentials at all, no publicRead => 401, not 403")
    void testNoCredentialsReturns401() {
        EntityAccessGuard.Result result = EntityAccessGuard.check(req, cfg, TENANT_A, APP_1, ENTITY_NAME, false);

        assertFalse(result.allowed());
        assertEquals(401, result.statusCode());
        assertNotNull(result.message());
    }

    @Test
    @DisplayName("Invalid/unknown session credential => 401")
    void testInvalidSessionCredentialReturns401() {
        when(req.header("X-Session-Token")).thenReturn("not-a-real-session-id");

        EntityAccessGuard.Result result = EntityAccessGuard.check(req, cfg, TENANT_A, APP_1, ENTITY_NAME, false);

        assertFalse(result.allowed());
        assertEquals(401, result.statusCode());
    }

    @Test
    @DisplayName("Real session but no membership row, no scopedAppId, no publicRead => 403")
    void testSessionWithNoMatchingRuleReturns403() {
        SessionData session = SessionService.createSession("user-outsider", TENANT_A);
        when(req.header("X-Session-Token")).thenReturn(session.sessionId());

        EntityAccessGuard.Result result = EntityAccessGuard.check(req, cfg, TENANT_A, APP_1, ENTITY_NAME, false);

        assertFalse(result.allowed());
        assertEquals(403, result.statusCode());
        // Round-65 review MEDIUM fix: the 403 message is a caller-invariant constant and must NOT
        // name the entity, or an authenticated caller could distinguish real-vs-fake entities by
        // inspecting the response body even when status codes match (see EntityAccessGuard's
        // denyOrAdmit Javadoc). Assert the exact constant rather than merely its absence of
        // ENTITY_NAME, so any future regression back to an interpolated message fails loudly.
        assertEquals("Forbidden: caller is not authorized for this entity", result.message());
        assertFalse(result.message().contains(ENTITY_NAME), "403 message must not leak the entity name");
    }

    // ========================================
    // Rule (i): appbana_app_members membership, any role
    // ========================================

    @Test
    @DisplayName("Rule (i): OWNER membership on (tenantId, appId) admits, even cross-tenant session")
    void testOwnerMembershipAdmits() {
        AppMembershipService.grant(TENANT_A, APP_1, "user-owner", AppMembershipService.Role.OWNER, "test-setup");
        SessionData session = SessionService.createSession("user-owner", TENANT_B); // session's own tenant differs
        when(req.header("X-Session-Token")).thenReturn(session.sessionId());

        EntityAccessGuard.Result result = EntityAccessGuard.check(req, cfg, TENANT_A, APP_1, ENTITY_NAME, false);

        assertTrue(result.allowed(), "a real membership row must admit regardless of the session's own tenant");
    }

    @Test
    @DisplayName("Rule (i): MEMBER role also admits")
    void testMemberRoleAdmits() {
        AppMembershipService.grant(TENANT_A, APP_1, "user-member", AppMembershipService.Role.MEMBER, "test-setup");
        SessionData session = SessionService.createSession("user-member", TENANT_A);
        when(req.header("X-Session-Token")).thenReturn(session.sessionId());

        EntityAccessGuard.Result result = EntityAccessGuard.check(req, cfg, TENANT_A, APP_1, ENTITY_NAME, false);

        assertTrue(result.allowed());
    }

    @Test
    @DisplayName("Rule (i): END_USER role also admits — this guard is data-access-only, never a management check")
    void testEndUserRoleAdmits() {
        AppMembershipService.grant(TENANT_A, APP_1, "user-enduser", AppMembershipService.Role.END_USER, "test-setup");
        SessionData session = SessionService.createSession("user-enduser", TENANT_A);
        when(req.header("X-Session-Token")).thenReturn(session.sessionId());

        EntityAccessGuard.Result result = EntityAccessGuard.check(req, cfg, TENANT_A, APP_1, ENTITY_NAME, false);

        assertTrue(result.allowed());
    }

    @Test
    @DisplayName("Rule (i): membership on a DIFFERENT app does not admit")
    void testMembershipOnDifferentAppDoesNotAdmit() {
        AppMembershipService.grant(TENANT_A, "some-other-app", "user-x", AppMembershipService.Role.OWNER, "test-setup");
        SessionData session = SessionService.createSession("user-x", TENANT_A);
        when(req.header("X-Session-Token")).thenReturn(session.sessionId());

        EntityAccessGuard.Result result = EntityAccessGuard.check(req, cfg, TENANT_A, APP_1, ENTITY_NAME, false);

        assertFalse(result.allowed());
        assertEquals(403, result.statusCode());
    }

    // ========================================
    // Rule (ii): scopedAppId match, hardened with tenantId (deviation from literal spec)
    // ========================================

    @Test
    @DisplayName("Rule (ii): a session scoped to this exact (tenantId, appId) admits")
    void testScopedSessionMatchingTenantAndAppAdmits() {
        SessionData session = SessionService.createSession("scoped-user", TENANT_A, APP_1);
        when(req.header("X-Session-Token")).thenReturn(session.sessionId());

        EntityAccessGuard.Result result = EntityAccessGuard.check(req, cfg, TENANT_A, APP_1, ENTITY_NAME, false);

        assertTrue(result.allowed());
    }

    @Test
    @DisplayName("Hardening beyond literal spec: scopedAppId matches but session's tenantId does NOT " +
            "=> still denied. appId is not guaranteed globally unique across tenants (same lesson as the " +
            "S2.10/S2.12 DataDrawer bug) — a bare appId-only comparison would be a cross-tenant hole here.")
    void testScopedSessionMatchingAppIdButDifferentTenantIsDenied() {
        // Same appId literal value, but the session was scoped while under TENANT_B, not TENANT_A.
        SessionData session = SessionService.createSession("scoped-user-b", TENANT_B, APP_1);
        when(req.header("X-Session-Token")).thenReturn(session.sessionId());

        EntityAccessGuard.Result result = EntityAccessGuard.check(req, cfg, TENANT_A, APP_1, ENTITY_NAME, false);

        assertFalse(result.allowed(), "a scopedAppId match must not cross a tenant boundary");
        assertEquals(403, result.statusCode());
    }

    @Test
    @DisplayName("Rule (ii): scopedAppId for a DIFFERENT app does not admit")
    void testScopedSessionForDifferentAppDoesNotAdmit() {
        SessionData session = SessionService.createSession("scoped-user-2", TENANT_A, "some-other-app");
        when(req.header("X-Session-Token")).thenReturn(session.sessionId());

        EntityAccessGuard.Result result = EntityAccessGuard.check(req, cfg, TENANT_A, APP_1, ENTITY_NAME, false);

        assertFalse(result.allowed());
        assertEquals(403, result.statusCode());
    }

    @Test
    @DisplayName("M1-style: a session with null tenantId and a null path tenantId must not leak through via " +
            "Objects.equals(null, null) == true, even with a matching scopedAppId")
    void testNullSessionTenantIdDoesNotMatchNullPathTenantId() {
        SessionData session = SessionService.createSession("scoped-user-3", null, APP_1);
        when(req.header("X-Session-Token")).thenReturn(session.sessionId());

        EntityAccessGuard.Result result = EntityAccessGuard.check(req, cfg, null, APP_1, ENTITY_NAME, false);

        assertFalse(result.allowed());
    }

    // ========================================
    // Rule (iii): publicRead rescues only GETs
    // ========================================

    @Test
    @DisplayName("Rule (iii): publicRead + GET admits even with zero session at all")
    void testPublicReadGetAdmitsAnonymously() {
        when(req.method()).thenReturn("GET");

        EntityAccessGuard.Result result = EntityAccessGuard.check(req, cfg, TENANT_A, APP_1, ENTITY_NAME, true);

        assertTrue(result.allowed());
    }

    @Test
    @DisplayName("Rule (iii): publicRead + POST does NOT admit — the rescue is GET-only")
    void testPublicReadPostDoesNotAdmit() {
        when(req.method()).thenReturn("POST");

        EntityAccessGuard.Result result = EntityAccessGuard.check(req, cfg, TENANT_A, APP_1, ENTITY_NAME, true);

        assertFalse(result.allowed());
        assertEquals(401, result.statusCode());
    }

    @Test
    @DisplayName("publicRead=false + GET + no session/membership => still denied")
    void testNonPublicGetWithNoOtherRuleIsDenied() {
        when(req.method()).thenReturn("GET");

        EntityAccessGuard.Result result = EntityAccessGuard.check(req, cfg, TENANT_A, APP_1, ENTITY_NAME, false);

        assertFalse(result.allowed());
        assertEquals(401, result.statusCode());
    }

    // ========================================
    // Rule (iv): break-glass admin token, evaluated last
    // ========================================

    @Test
    @DisplayName("Rule (iv): valid admin token admits with zero session and no membership/scopedAppId/publicRead")
    void testAdminTokenAdmitsWithNoSessionAtAll() {
        when(req.header("X-AppBana-Token")).thenReturn("admin-token-xyz");

        EntityAccessGuard.Result result = EntityAccessGuard.check(req, cfg, TENANT_A, APP_1, ENTITY_NAME, false);

        assertTrue(result.allowed());
    }

    @Test
    @DisplayName("Rule (iv): valid admin token admits even when a real session with a tenant/app mismatch is also present")
    void testAdminTokenAdmitsDespiteMismatchedSession() {
        SessionData session = SessionService.createSession("user-mismatch", "t-completely-different");
        when(req.header("X-AppBana-Token")).thenReturn("admin-token-xyz");
        when(req.header("X-Session-Token")).thenReturn(session.sessionId());

        EntityAccessGuard.Result result = EntityAccessGuard.check(req, cfg, TENANT_A, APP_1, ENTITY_NAME, false);

        assertTrue(result.allowed());
    }

    @Test
    @DisplayName("A read-only token (not admin) does NOT satisfy the break-glass branch — " +
            "this guard uses hasAdmin only, never hasRead/readToken (plan doc retires readToken in favor of publicRead)")
    void testReadTokenAloneDoesNotBypass() {
        when(req.header("X-AppBana-Token")).thenReturn("read-token-abc");

        EntityAccessGuard.Result result = EntityAccessGuard.check(req, cfg, TENANT_A, APP_1, ENTITY_NAME, false);

        assertFalse(result.allowed());
        assertEquals(401, result.statusCode());
    }

    // ========================================
    // Entry point (a): packed-key resolution via SchemaManager.loadSchema
    // ========================================

    @Test
    @DisplayName("Entry point (a): unknown entity key, no session => 401, not 404 (S3.4 review LOW fix)")
    void testUnknownEntityKeyNoSessionReturns401() {
        EntityAccessGuard.Result result = EntityAccessGuard.check(req, cfg, "no-such-tenant_no-such-app_NoSuchEntity", false);

        assertFalse(result.allowed());
        assertEquals(401, result.statusCode());
    }

    @Test
    @DisplayName("Entry point (a): unknown entity key, real session but no membership possible => 403, same as a real unauthorized entity")
    void testUnknownEntityKeyWithSessionReturns403() {
        SessionData session = SessionService.createSession("user-no-such-app", TENANT_A);
        when(req.header("X-Session-Token")).thenReturn(session.sessionId());

        EntityAccessGuard.Result result = EntityAccessGuard.check(req, cfg, "no-such-tenant_no-such-app_NoSuchEntity", false);

        assertFalse(result.allowed());
        assertEquals(403, result.statusCode());
    }

    @Test
    @DisplayName("Entry point (a): unknown vs. real-but-unauthorized entity key are byte-identical (status code AND message) for the same caller shape (round-65 review MEDIUM fix)")
    void testUnknownEntityKeyIndistinguishableFromRealUnauthorizedEntity() {
        saveFixtureSchema();
        EntityAccessGuard.Result unknownAnon = EntityAccessGuard.check(req, cfg, "no-such-tenant_no-such-app_NoSuchEntity", false);
        EntityAccessGuard.Result realAnon = EntityAccessGuard.check(req, cfg, PACKED_KEY, false);
        assertEquals(realAnon.statusCode(), unknownAnon.statusCode(), "unauthenticated: unknown key vs real-unauthorized key must match");
        assertEquals(realAnon.message(), unknownAnon.message(), "unauthenticated: 401 message must not differ (both null/no session)");

        SessionData session = SessionService.createSession("user-packed-outsider-2", TENANT_A);
        when(req.header("X-Session-Token")).thenReturn(session.sessionId());
        EntityAccessGuard.Result unknownWithSession = EntityAccessGuard.check(req, cfg, "no-such-tenant_no-such-app_NoSuchEntity", false);
        EntityAccessGuard.Result realWithSession = EntityAccessGuard.check(req, cfg, PACKED_KEY, false);
        assertEquals(realWithSession.statusCode(), unknownWithSession.statusCode(), "authenticated non-member: unknown key vs real-unauthorized key must match");
        // This is the exact assertion the round-65 review independently added via a temporary probe
        // to prove the 403 body still leaked (before the fix): a real entity's label (schema.getName(),
        // short) vs. an unresolvable packed key's label (the full raw key, always longer) meant the two
        // 403 bodies always differed even once status codes matched. Now permanently enforced here so
        // this closure cannot silently regress.
        assertEquals(realWithSession.message(), unknownWithSession.message(), "authenticated non-member: 403 message must not differ (must not leak which key is real)");
    }

    @Test
    @DisplayName("Entry point (a): resolves tenantId/appId from a real schema and delegates — membership admits")
    void testPackedKeyDelegatesToMembershipCheck() {
        saveFixtureSchema();
        AppMembershipService.grant(TENANT_A, APP_1, "user-packed-member", AppMembershipService.Role.MEMBER, "test-setup");
        SessionData session = SessionService.createSession("user-packed-member", TENANT_A);
        when(req.header("X-Session-Token")).thenReturn(session.sessionId());

        EntityAccessGuard.Result result = EntityAccessGuard.check(req, cfg, PACKED_KEY, false);

        assertTrue(result.allowed());
    }

    @Test
    @DisplayName("Entry point (a): resolves tenantId/appId from a real schema and delegates — no matching rule denies")
    void testPackedKeyDelegatesToDenyPath() {
        saveFixtureSchema();
        SessionData session = SessionService.createSession("user-packed-outsider", TENANT_A);
        when(req.header("X-Session-Token")).thenReturn(session.sessionId());

        EntityAccessGuard.Result result = EntityAccessGuard.check(req, cfg, PACKED_KEY, false);

        assertFalse(result.allowed());
        assertEquals(403, result.statusCode());
    }
}
