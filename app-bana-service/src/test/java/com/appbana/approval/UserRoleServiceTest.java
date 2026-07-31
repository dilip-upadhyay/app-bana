package com.appbana.approval;

import com.appbana.JdbcManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class UserRoleServiceTest {

    @BeforeAll
    public static void setUpDb() throws Exception {
        // Ensure appbana_user_roles table exists for test environment
        try (Connection c = JdbcManager.getConnection("default");
             Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS appbana_user_roles (" +
                    "tenant_id VARCHAR(255) NOT NULL, " +
                    "app_id VARCHAR(255) NOT NULL, " +
                    "entity_name VARCHAR(255) NOT NULL, " +
                    "user_id VARCHAR(255) NOT NULL, " +
                    "role VARCHAR(20) NOT NULL CHECK (role IN ('maker', 'checker', 'both')), " +
                    "granted_by VARCHAR(255) NOT NULL, " +
                    "granted_at TIMESTAMP NOT NULL DEFAULT NOW(), " +
                    "PRIMARY KEY (tenant_id, app_id, entity_name, user_id))");
        }
    }

    @BeforeEach
    public void cleanTable() throws Exception {
        try (Connection c = JdbcManager.getConnection("default");
             Statement s = c.createStatement()) {
            // Scoped to this test's OWN fixture tenants -- a blanket "DELETE FROM
            // appbana_user_roles" (no WHERE) wipes every real app's role grants in the
            // shared dev Postgres instance on every `mvn test` run.
            s.execute("DELETE FROM appbana_user_roles WHERE tenant_id IN ('tenant1', 't1')");
        }
    }

    @Test
    public void testGrantAndCheckRoles() {
        String tenantId = "tenant1";
        String appId = "app1";
        String entityName = "Order";
        String makerUser = "user_maker";
        String checkerUser = "user_checker";

        UserRoleService.grantRole(tenantId, appId, entityName, makerUser, UserRoleService.Role.MAKER, "admin");
        UserRoleService.grantRole(tenantId, appId, entityName, checkerUser, UserRoleService.Role.CHECKER, "admin");

        assertTrue(UserRoleService.isMaker(tenantId, appId, entityName, makerUser));
        assertFalse(UserRoleService.isChecker(tenantId, appId, entityName, makerUser));

        assertFalse(UserRoleService.isMaker(tenantId, appId, entityName, checkerUser));
        assertTrue(UserRoleService.isChecker(tenantId, appId, entityName, checkerUser));
    }

    @Test
    public void testBothRoleSemantics() {
        String tenantId = "tenant1";
        String appId = "app1";
        String entityName = "Invoice";
        String bothUser = "user_both";

        UserRoleService.grantRole(tenantId, appId, entityName, bothUser, UserRoleService.Role.BOTH, "admin");

        assertTrue(UserRoleService.isMaker(tenantId, appId, entityName, bothUser));
        assertTrue(UserRoleService.isChecker(tenantId, appId, entityName, bothUser));

        Set<UserRoleService.Role> roles = UserRoleService.getUserRoles(tenantId, appId, entityName, bothUser);
        assertTrue(roles.contains(UserRoleService.Role.MAKER));
        assertTrue(roles.contains(UserRoleService.Role.CHECKER));
        assertTrue(roles.contains(UserRoleService.Role.BOTH));
    }

    @Test
    public void testGrantIdempotencyAndRevoke() {
        String tenantId = "t1";
        String appId = "a1";
        String entityName = "Customer";
        String user = "u1";

        UserRoleService.grantRole(tenantId, appId, entityName, user, UserRoleService.Role.MAKER, "admin");
        assertTrue(UserRoleService.isMaker(tenantId, appId, entityName, user));

        // Re-grant with updated role 'checker'
        UserRoleService.grantRole(tenantId, appId, entityName, user, UserRoleService.Role.CHECKER, "admin2");
        assertFalse(UserRoleService.isMaker(tenantId, appId, entityName, user));
        assertTrue(UserRoleService.isChecker(tenantId, appId, entityName, user));

        // Revoke
        UserRoleService.revokeRole(tenantId, appId, entityName, user);
        assertFalse(UserRoleService.isMaker(tenantId, appId, entityName, user));
        assertFalse(UserRoleService.isChecker(tenantId, appId, entityName, user));
    }

    @Test
    public void testGrantCreatorRoles() {
        String tenantId = "t1";
        String appId = "a1";
        String creator = "creator_user";
        Set<String> entities = Set.of("Order", "OrderItem");

        UserRoleService.grantCreatorRoles(tenantId, appId, creator, entities);

        assertTrue(UserRoleService.isMaker(tenantId, appId, "Order", creator));
        assertTrue(UserRoleService.isChecker(tenantId, appId, "Order", creator));
        assertTrue(UserRoleService.isMaker(tenantId, appId, "OrderItem", creator));
        assertTrue(UserRoleService.isChecker(tenantId, appId, "OrderItem", creator));
    }
}
