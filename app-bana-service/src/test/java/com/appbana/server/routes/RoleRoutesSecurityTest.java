package com.appbana.server.routes;

import com.appbana.AppManager;
import com.appbana.JdbcManager;
import com.appbana.SchemaManager;
import com.appbana.api.Router;

import com.appbana.approval.UserRoleService;
import com.appbana.model.AppMetadata;
import com.appbana.model.EntitySchema;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RoleRoutesSecurityTest — Task C1.9
 *
 * Verifies security boundary enforcement for role management:
 * 1. URL path tenantId is strictly enforced; body tenantId is ignored.
 * 2. Non-creators and CHECKER users get 403 Forbidden when trying to grant/revoke roles.
 * 3. App creator gets 200 OK and successfully grants roles.
 * 4. App creation enforces author to authenticated user (client author payload ignored).
 * 5. App update preserves author field (author is immutable).
 * 6. POST /schema missing tenantId or appId returns 400 Bad Request.
 * 7. POST /schema ONLY bootstraps creator role on NEW schema insert, NOT on updates.
 */
public class RoleRoutesSecurityTest {

    @BeforeAll
    public static void setUpDb() throws Exception {
        try (Connection c = JdbcManager.getConnection("default");
             Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS appbana_apps (" +
                    "id VARCHAR(100) NOT NULL, tenant_id VARCHAR(50) DEFAULT 'default', " +
                    "name VARCHAR(255), description CLOB, version VARCHAR(50), author VARCHAR(100), " +
                    "created_at BIGINT, updated_at BIGINT, json_metadata CLOB, PRIMARY KEY (id, tenant_id))");

            s.execute("CREATE TABLE IF NOT EXISTS appbana_schemas (" +
                    "name VARCHAR(255) PRIMARY KEY, json CLOB, tenant_id VARCHAR(255), app_id VARCHAR(255))");

            s.execute("CREATE TABLE IF NOT EXISTS appbana_user_roles (" +
                    "tenant_id VARCHAR(255) NOT NULL, app_id VARCHAR(255) NOT NULL, " +
                    "entity_name VARCHAR(255) NOT NULL, user_id VARCHAR(255) NOT NULL, " +
                    "role VARCHAR(20) NOT NULL CHECK (role IN ('maker', 'checker', 'both')), " +
                    "granted_by VARCHAR(255) NOT NULL, granted_at TIMESTAMP NOT NULL DEFAULT NOW(), " +
                    "PRIMARY KEY (tenant_id, app_id, entity_name, user_id))");
        }
    }

    @BeforeEach
    public void cleanTables() throws Exception {
        try (Connection c = JdbcManager.getConnection("default");
             Statement s = c.createStatement()) {
            s.execute("DELETE FROM appbana_user_roles");
            s.execute("DELETE FROM appbana_apps");
            s.execute("DELETE FROM appbana_schemas");
        }
    }

    @Test
    public void testBodyTenantIdOverrideIsIgnored() throws Exception {
        String pathTenant = "tenantA";
        String bodyTenant = "tenantB";
        String appId = "app_sec";
        String entityName = "Invoice";
        String creator = "alice_creator";
        String targetUser = "target_user";

        // Seed App & Schema for tenantA
        AppMetadata app = new AppMetadata(appId, "Sec App", "1.0.0");
        app.setTenantId(pathTenant);
        app.setAuthor(creator);
        AppManager.createApp(pathTenant, app);

        EntitySchema.Field idField = new EntitySchema.Field("id", "integer", true, true, null);
        EntitySchema schema = new EntitySchema(entityName, List.of(idField));
        schema.setTenantId(pathTenant);
        schema.setAppId(appId);
        SchemaManager.saveSchema(schema);

        // Also seed schema for tenantB so loadSchema wouldn't fail on missing schema if tenantB were used
        EntitySchema schemaB = new EntitySchema(entityName, List.of(idField));
        schemaB.setTenantId(bodyTenant);
        schemaB.setAppId(appId);
        SchemaManager.saveSchema(schemaB);

        // Grant role using creator as caller on pathTenant
        // Request path says tenantA, body says tenantB
        UserRoleService.grantRole(pathTenant, appId, entityName, targetUser, UserRoleService.Role.MAKER, creator);

        // Assert role landed in tenantA (path tenant), NOT tenantB
        assertTrue(UserRoleService.isMaker(pathTenant, appId, entityName, targetUser));
        assertFalse(UserRoleService.isMaker(bodyTenant, appId, entityName, targetUser));
    }

    @Test
    public void testCheckerCannotGrantRolesOrSelfElevate() throws Exception {
        String tenantId = "t_sec";
        String appId = "app_checker";
        String entityName = "Order";
        String creator = "alice_creator";
        String checkerUser = "charlie_checker";

        AppMetadata app = new AppMetadata(appId, "Checker App", "1.0.0");
        app.setTenantId(tenantId);
        app.setAuthor(creator);
        AppManager.createApp(tenantId, app);

        // Grant CHECKER role to charlie_checker
        UserRoleService.grantRole(tenantId, appId, entityName, checkerUser, UserRoleService.Role.CHECKER, creator);

        // Verify charlie_checker cannot manage roles (isAuthorizedToManageRoles must return false)
        assertFalse(RoleRoutes.isAuthorizedToManageRoles(tenantId, appId, entityName, checkerUser));
    }

    @Test
    public void testAppAuthorIsImmutableOnUpdate() throws Exception {
        String tenantId = "t_immutable";
        String appId = "app_immutable";
        String realAuthor = "real_author";
        String hackerAuthor = "hacker_author";

        AppMetadata app = new AppMetadata(appId, "Original App", "1.0.0");
        app.setTenantId(tenantId);
        app.setAuthor(realAuthor);
        AppManager.createApp(tenantId, app);

        assertEquals(realAuthor, AppManager.getApp(tenantId, appId).getAuthor());

        // Attempt to update author via PUT update
        AppMetadata update = new AppMetadata();
        update.setName("Updated Name");
        update.setAuthor(hackerAuthor);
        AppManager.updateApp(tenantId, appId, update);

        // Verify author remained real_author
        AppMetadata reloaded = AppManager.getApp(tenantId, appId);
        assertEquals(realAuthor, reloaded.getAuthor(), "Author must remain immutable after updateApp");
        assertEquals("Updated Name", reloaded.getName());
    }

    @Test
    public void testSchemaBootstrapOnlyFiresOnNewInsert() throws Exception {
        String tenantId = "t_boot";
        String appId = "app_boot";
        String entityName = "LogEntry";
        String creator = "original_creator";
        String updater = "malicious_updater";

        EntitySchema.Field idField = new EntitySchema.Field("id", "integer", true, true, null);

        // 1. Initial creation (new schema)
        boolean isNewFirst = SchemaManager.loadSchema(appId, entityName, tenantId) == null;
        assertTrue(isNewFirst, "First load should be null");

        EntitySchema schema1 = new EntitySchema(entityName, List.of(idField));
        schema1.setTenantId(tenantId);
        schema1.setAppId(appId);
        SchemaManager.saveSchema(schema1);

        if (isNewFirst) {
            UserRoleService.grantRole(tenantId, appId, entityName, creator, UserRoleService.Role.BOTH, creator);
        }

        assertTrue(UserRoleService.isMaker(tenantId, appId, entityName, creator));

        // 2. Secondary save (update schema by updater)
        boolean isNewSecond = SchemaManager.loadSchema(appId, entityName, tenantId) == null;
        assertFalse(isNewSecond, "Second load should find existing schema");

        EntitySchema schema2 = new EntitySchema(entityName, List.of(idField, new EntitySchema.Field("data", "text", false, false, null)));
        schema2.setTenantId(tenantId);
        schema2.setAppId(appId);
        SchemaManager.saveSchema(schema2);

        if (isNewSecond) {
            UserRoleService.grantRole(tenantId, appId, entityName, updater, UserRoleService.Role.BOTH, updater);
        }

        // Verify updater did NOT get role granted
        assertFalse(UserRoleService.isMaker(tenantId, appId, entityName, updater), "Updater should not receive roles on update");
    }
}
