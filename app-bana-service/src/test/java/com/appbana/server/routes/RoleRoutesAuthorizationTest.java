package com.appbana.server.routes;

import com.appbana.AppManager;
import com.appbana.JdbcManager;
import com.appbana.SchemaManager;
import com.appbana.approval.UserRoleService;
import com.appbana.model.AppMetadata;
import com.appbana.model.EntitySchema;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RoleRoutesAuthorizationTest {

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

    // Scoped to this test class's OWN fixture tenants only. A blanket
    // "DELETE FROM appbana_apps"/"appbana_schemas" (no WHERE) here wipes every
    // real app in the shared dev Postgres instance on every `mvn test` run --
    // confirmed to have destroyed the live "Employee Onboarding" AI Builder app's
    // appbana_apps/appbana_schemas rows this way (its appbana_pages rows and
    // physical data tables survived untouched, only the app/schema catalog rows
    // were lost). Keep this IN (...) list in sync with every tenantId literal
    // used by a @Test in this file.
    @BeforeEach
    public void cleanTables() throws Exception {
        try (Connection c = JdbcManager.getConnection("default");
             Statement s = c.createStatement()) {
            s.execute("DELETE FROM appbana_user_roles WHERE tenant_id IN ('t_auth', 't_schema')");
            s.execute("DELETE FROM appbana_apps WHERE tenant_id IN ('t_auth', 't_schema')");
            s.execute("DELETE FROM appbana_schemas WHERE tenant_id IN ('t_auth', 't_schema')");
        }
    }

    @Test
    public void testAuthorizationGuardUnitLogic() throws Exception {
        String tenantId = "t_auth";
        String appId = "app_auth";
        String entityName = "Payment";
        String creator = "alice_creator";
        String nonCreator = "bob_user";
        String checker = "charlie_checker";

        // Create app with author 'alice_creator'
        AppMetadata app = new AppMetadata(appId, "Auth App", "1.0.0");
        app.setTenantId(tenantId);
        app.setAuthor(creator);
        AppManager.createApp(tenantId, app);

        // System user is always authorized
        assertTrue(RoleRoutes.isAuthorizedToManageRoles(tenantId, appId, entityName, "system"));

        // App creator is authorized
        assertTrue(RoleRoutes.isAuthorizedToManageRoles(tenantId, appId, entityName, creator));

        // Random non-creator is NOT authorized
        assertFalse(RoleRoutes.isAuthorizedToManageRoles(tenantId, appId, entityName, nonCreator));

        // Grant 'checker' role to charlie_checker
        UserRoleService.grantRole(tenantId, appId, entityName, checker, UserRoleService.Role.CHECKER, creator);

        // User with ONLY 'checker' role is NOT authorized to manage roles (C1.9 fix)
        assertFalse(RoleRoutes.isAuthorizedToManageRoles(tenantId, appId, entityName, checker));
    }

    @Test
    public void testSchemaSaveBootstrapsCreatorRole() throws Exception {
        String tenantId = "t_schema";
        String appId = "app_schema";
        String entityName = "AuditLog";
        String creator = "schema_creator";

        EntitySchema.Field idField = new EntitySchema.Field("id", "integer", true, true, null);
        EntitySchema schema = new EntitySchema(entityName, List.of(idField));
        schema.setTenantId(tenantId);
        schema.setAppId(appId);
        SchemaManager.saveSchema(schema);

        // Grant role explicitly as done in SchemaRoutes or AppRoutes
        UserRoleService.grantRole(tenantId, appId, entityName, creator, UserRoleService.Role.BOTH, creator);

        assertTrue(UserRoleService.isMaker(tenantId, appId, entityName, creator));
        assertTrue(UserRoleService.isChecker(tenantId, appId, entityName, creator));
    }
}
