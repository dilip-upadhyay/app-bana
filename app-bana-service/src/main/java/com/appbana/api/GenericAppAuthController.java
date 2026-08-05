package com.appbana.api;

import com.appbana.JdbcManager;
import com.appbana.SchemaManager;
import com.appbana.model.EntitySchema;
import com.appbana.service.PasswordService;
import com.appbana.service.SessionService;
import com.appbana.service.SessionService.SessionData;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Controller for handling authentication for generated applications.
 * Allows end-users to login against the generated app's database tables.
 */
public class GenericAppAuthController {
    private static final Logger LOG = LoggerFactory.getLogger(GenericAppAuthController.class);

    // S3.3 (M6): one generic, indistinguishable failure for every "this should not succeed"
    // case - nonexistent app, nonexistent entity, unknown email, wrong password. Returning a
    // distinguishable status/body for any single one of these (e.g. 404 for "no such entity")
    // turns this endpoint into a cross-tenant/cross-app existence oracle for an unauthenticated
    // caller.
    private static final Map<String, Object> GENERIC_AUTH_FAILURE = Map.of("error", "Invalid credentials");

    public static BiConsumer<Router.HttpRequest, Router.HttpResponse> login() {
        return (req, res) -> {
            try {
                // Parse request
                Map<String, String> body = req.readJson(new TypeReference<>() {
                });

                String appId = body.get("appId");
                String tenantId = body.getOrDefault("tenantId", "default");
                String entityName = body.getOrDefault("entity", "User");
                String email = body.get("email");
                String password = body.get("password");

                if (appId == null || appId.isBlank()) {
                    res.json(400, Map.of("error", "App ID is required for authentication"));
                    return;
                }

                if (email == null || password == null || password.isBlank()) {
                    // Round-60 review MEDIUM: an empty (non-null, blank) password used to slip past
                    // this guard and reach verifyCredential(), which for a BCrypt-hashed row calls
                    // PasswordService.verifyPassword("", hash) -> IllegalArgumentException -> 500,
                    // while an unknown email or a plaintext row still correctly returned 401. That
                    // status-code split let an unauthenticated caller distinguish "this email is
                    // backed by a BCrypt-hashed account" from every other case - the same existence-
                    // oracle class M6 closes elsewhere in this method. Rejecting here with the same
                    // 400 as the missing-password case happens before any schema/DB lookup, so it
                    // reveals nothing about whether the app/entity/email exists.
                    res.json(400, Map.of("error", "Email and password are required"));
                    return;
                }

                // 1. Resolve Entity Schema (Scoped to App & Tenant)
                EntitySchema schema = SchemaManager.loadSchema(appId, entityName, tenantId);
                if (schema == null) {
                    // Try case-insensitive lookup
                    if ("User".equalsIgnoreCase(entityName)) {
                        schema = SchemaManager.loadSchema(appId, "User", tenantId);
                    }
                }

                if (schema == null) {
                    // S3.3 (M6): previously a distinguishable 404 here let an unauthenticated
                    // caller enumerate which apps/entities exist. Same generic 401 as any other
                    // login failure below.
                    LOG.debug("Login failed: entity '{}' not found in app '{}' (tenant '{}')", entityName, appId,
                            tenantId);
                    res.json(401, GENERIC_AUTH_FAILURE);
                    return;
                }

                // 2. Identify connection/table
                String dsName = schema.getDatasourceName();
                String tableName = SchemaManager.getPhysicalTableName(schema);

                // 3. Fetch by email only - password is verified in Java below (M5). A BCrypt
                // hash embeds a random salt, so the same password hashes differently every
                // time; a `WHERE password = ?` clause can never match a stored hash, so the
                // comparison cannot live in SQL.
                //
                // Column name MUST be quoted+uppercase ("EMAIL"), matching SchemaManager's own
                // quote() convention used to create every dynamic-entity column: Postgres does
                // not case-fold an unquoted `email` reference to match a column that was itself
                // created quoted (i.e. case-sensitive) as "EMAIL" - it throws "column email does
                // not exist". The original unquoted `WHERE email = ? AND password = ?` therefore
                // 500'd on every single login attempt, right password or wrong. Verified directly
                // against Postgres while fixing S3.3 (a table created via SchemaManager.saveSchema
                // + a bare `WHERE email = ?` reproduces the error; `WHERE "EMAIL" = ?` does not).
                String sql = "SELECT * FROM \"" + tableName.toUpperCase() + "\" WHERE \"EMAIL\" = ?";

                Map<String, Object> userData = null;
                String storedPassword = null;

                JdbcManager.ensureMetaTableFor(dsName);
                try (Connection conn = JdbcManager.getConnection(dsName);
                        PreparedStatement ps = conn.prepareStatement(sql)) {

                    ps.setString(1, email);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            userData = new HashMap<>();
                            ResultSetMetaData meta = rs.getMetaData();
                            int colCount = meta.getColumnCount();

                            for (int i = 1; i <= colCount; i++) {
                                String colName = meta.getColumnName(i).toLowerCase();
                                // Exclude password/secret columns from the response; capture the
                                // raw stored value for verification first.
                                if (colName.contains("password") || colName.contains("secret")) {
                                    if ("password".equals(colName)) {
                                        storedPassword = rs.getString(i);
                                    }
                                    continue;
                                }
                                userData.put(colName, rs.getObject(i));
                            }
                        }
                    }
                }

                if (userData == null || !verifyCredential(password, storedPassword)) {
                    res.json(401, GENERIC_AUTH_FAILURE);
                    return;
                }

                // 4. Issue a real, app-scoped session (S3.1/S3.3). tenantId here MUST be the
                // app's own tenantId (schema.getTenantId(), authoritative for the schema we just
                // matched on), never some other tenant the caller might separately claim -
                // EntityAccessGuard rule (ii) requires session.tenantId() to equal the entity's
                // own tenantId, so minting with the wrong value here would silently 403 every
                // request this session makes.
                String appTenantId = (schema.getTenantId() != null && !schema.getTenantId().isBlank())
                        ? schema.getTenantId()
                        : tenantId;
                String sessionUserId = resolveSessionUserId(schema, userData, email);
                SessionData session = SessionService.createSession(sessionUserId, appTenantId, appId);

                LOG.info("Generic app login succeeded for '{}' in app '{}' (tenant '{}')", email, appId,
                        appTenantId);

                // Return success
                res.json(200, Map.of(
                        "user", userData,
                        "token", session.sessionId(),
                        "sessionId", session.sessionId(),
                        "message", "Login successful"));

            } catch (Exception e) {
                LOG.error("Generic App Login failed", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        };
    }

    /**
     * S3.3 (M5): verify in Java, never in SQL. Prefers a BCrypt comparison; falls back to a
     * constant-time plain-text equality check for rows still holding a pre-hash value (Phase 1
     * prototype data - see the old TODO this replaced). S4.2 adds transparent hash-on-write and
     * rehash-on-login so this fallback branch stops being reachable for any row it has touched.
     *
     * Rejects a blank (non-null, empty) rawPassword here too - not just at the login() call site
     * - because PasswordService.verifyPassword throws IllegalArgumentException on an empty
     * argument (round-60 review MEDIUM); this method must be safe to call with any non-null
     * String regardless of caller-side guards.
     */
    private static boolean verifyCredential(String rawPassword, String storedValue) {
        if (rawPassword == null || rawPassword.isEmpty() || storedValue == null || storedValue.isEmpty()) {
            return false;
        }
        if (looksLikeBcryptHash(storedValue)) {
            return PasswordService.verifyPassword(rawPassword, storedValue);
        }
        return MessageDigest.isEqual(
                rawPassword.getBytes(StandardCharsets.UTF_8),
                storedValue.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean looksLikeBcryptHash(String value) {
        return value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$");
    }

    /** Prefer the row's own primary-key value as the session's userId; fall back to email. */
    private static String resolveSessionUserId(EntitySchema schema, Map<String, Object> userData, String email) {
        if (schema.getFields() != null) {
            for (EntitySchema.Field field : schema.getFields()) {
                if (field.isPrimaryKey() && field.getName() != null) {
                    Object pk = userData.get(field.getName().toLowerCase());
                    if (pk != null) {
                        return String.valueOf(pk);
                    }
                }
            }
        }
        return email;
    }
}
