package com.appbana.server;

import com.appbana.api.Router;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * S0.3 — census-drift guard.
 *
 * Fails whenever {@link RouteRegistry#buildRouter()} registers a route (method + path
 * pattern) that isn't accounted for in the S0.2 route census
 * ({@code docs/planning/TENANT_ISOLATION_SECURITY_PLAN.md}, "S0.2 Route census" section),
 * or whenever a censused route is renamed/removed without updating this list. This is a
 * SET comparison (symmetric difference), not a count: {@code Router} has one confirmed
 * duplicate registration today (see below), so a raw registration count would be
 * 97 while the number of distinct (method, path) signatures is 96 — a count-based
 * assertion would silently accept a second, different duplicate appearing while missing
 * an actually-new route, which is exactly the failure mode this test exists to catch.
 *
 * Route inventory reflected here on 2026-08-01 (96 distinct signatures, 97 registration
 * call-sites — {@code GET /api/{tenantId}/apps/{id}/env/{env}/full} is registered twice,
 * byte-identical, in AppRoutes.java; the second registration is dead code because
 * {@link Router} is first-match-wins). Whenever a route is added, renamed, or removed in
 * any {@code *Routes.java} file, update BOTH this set and the plan doc's census table in
 * the same commit — that pairing is the whole point of this test.
 */
class RouteCensusTest {

    /** Expected (method, path-pattern) signatures, exactly as passed to Router.get/post/put/delete. */
    private static final Set<String> EXPECTED_ROUTES = new TreeSet<>(List.of(
        // AiRoutes.java
        "POST /api/ai/chat",
        "POST /api/ai/chat/agent",
        // AppContextRoutes.java
        "GET /api/app-context",
        // AppRoutes.java
        "POST /api/{tenantId}/apps/{id}/publish",
        "PUT /api/{tenantId}/apps/{id}/deploy/local",
        "POST /api/{tenantId}/apps/{id}/commits",
        "POST /api/{tenantId}/apps/{id}/commits/rollback",
        "POST /api/{tenantId}/apps/{id}/versions",
        "GET /api/{tenantId}/apps/{id}/versions",
        "POST /api/{tenantId}/apps/{id}/deploy/{versionId}",
        "GET /api/{tenantId}/apps/{id}/pipeline",
        "GET /api/{tenantId}/apps/{id}/env/{env}/full", // registered twice; set collapses to one signature
        "POST /api/{tenantId}/apps/{id}/restore-schemas",
        "GET /appbana-studio/{tenantId}/apps",
        "GET /appbana-studio/{tenantId}/apps/{id}",
        "GET /api/{tenantId}/apps/{id}/full",
        "POST /appbana-studio/{tenantId}/apps",
        "PUT /appbana-studio/{tenantId}/apps/{id}",
        "DELETE /appbana-studio/{tenantId}/apps/{id}",
        "GET /appbana-studio/{tenantId}/apps/{id}/workflow",
        "PUT /appbana-studio/{tenantId}/apps/{id}/workflow",
        "GET /appbana-studio/{tenantId}/apps/{appId}/pages/{pageId}",
        "PUT /appbana-studio/{tenantId}/apps/{appId}/pages/{pageId}",
        "DELETE /appbana-studio/{tenantId}/apps/{appId}/pages/{pageId}",
        "GET /api/templates",
        "GET /api/templates/{id}",
        "POST /api/templates",
        "PUT /api/templates/{id}",
        "DELETE /api/templates/{id}",
        // ApprovalRoutes.java
        "POST /api/tenants/{tenantId}/apps/{appId}/entities/{entityName}/records/{id}/submit",
        "POST /api/tenants/{tenantId}/apps/{appId}/entities/{entityName}/records/{id}/approve",
        "POST /api/tenants/{tenantId}/apps/{appId}/entities/{entityName}/records/{id}/reject",
        "GET /api/tenants/{tenantId}/apps/{appId}/entities/{entityName}/approvals/pending",
        "GET /api/tenants/{tenantId}/apps/{appId}/entities/{entityName}/records/{id}/approvals/audit",
        // AuthRoutes.java
        "POST /api/auth/register",
        "POST /api/auth/login",
        "GET /api/auth/profile",
        "POST /api/runtime/auth/login",
        "GET /api/csrf-token",
        "POST /api/csrf-validate",
        // FileRoutes.java
        "POST /api/files/upload",
        "GET /api/files/{tenantId}/{appId}/{fileId}",
        // GenericEntityRoutes.java
        "GET /audit",
        "GET /api/field-permissions",
        "GET /api/field-permissions/readable",
        "GET /api/field-permissions/editable",
        "GET /api/field-permissions/{id}",
        "POST /api/field-permissions",
        "PUT /api/field-permissions/{id}",
        "DELETE /api/field-permissions/{id}",
        "POST /api/{entity}",
        "POST /api/{entity}/batch",
        "GET /api/{entity}",
        "GET /api/{entity}/{id}",
        "PUT /api/{entity}/{id}",
        "DELETE /api/{entity}/{id}",
        "POST /api/{entity}/bulk-delete",
        "POST /api/{entity}/bulk-export",
        "POST /appbana-studio/{tenantId}/apps/{appId}/{entity}",
        "GET /appbana-studio/{tenantId}/apps/{appId}/{entity}",
        "GET /appbana-studio/{tenantId}/apps/{appId}/{entity}/{id}",
        "PUT /appbana-studio/{tenantId}/apps/{appId}/{entity}/{id}",
        "DELETE /appbana-studio/{tenantId}/apps/{appId}/{entity}/{id}",
        "POST /api/{tenantId}/apps/{appId}/{entity}",
        "POST /api/{tenantId}/apps/{appId}/env/{env}/{entity}",
        "GET /api/{tenantId}/apps/{appId}/env/{env}/{entity}",
        "GET /api/{tenantId}/apps/{appId}/env/{env}/{entity}/{id}",
        "PUT /api/{tenantId}/apps/{appId}/env/{env}/{entity}/{id}",
        "DELETE /api/{tenantId}/apps/{appId}/env/{env}/{entity}/{id}",
        // HealthRoutes.java
        "GET /health",
        "GET /ready",
        // RoleRoutes.java
        "GET /api/tenants/{tenantId}/apps/{appId}/roles",
        "POST /api/tenants/{tenantId}/apps/{appId}/roles",
        "DELETE /api/tenants/{tenantId}/apps/{appId}/roles",
        // SavedViewRoutes.java
        "GET /api/saved-views",
        "POST /api/saved-views",
        "DELETE /api/saved-views/{viewId}",
        // SchemaRoutes.java
        "GET /api/endpoints",
        "GET /openapi.json",
        "GET /schema",
        "GET /schema/{name}",
        "POST /schema",
        "DELETE /schema/{name}",
        "GET /api/debug/schemas",
        "GET /api/debug/schemas/names",
        // TenantBrandingRoutes.java
        "GET /api/tenants/{tenantId}/branding",
        // UserRoutes.java
        "GET /api/users/me",
        // WorkflowRoutes.java
        "POST /api/workflows",
        "GET /api/workflows",
        "GET /api/workflows/{id}",
        "POST /api/workflows/{id}/publish",
        "POST /api/workflows/{id}/start",
        "GET /api/my-tasks",
        "POST /api/my-tasks/{tokenId}/complete",
        "GET /api/my-requests",
        "GET /api/workflow-instances"
    ));

    @Test
    void registeredRoutesMatchCensusExactly() throws Exception {
        Router router = RouteRegistry.buildRouter();
        Set<String> actual = reflectRegisteredRoutes(router);

        Set<String> registeredButNotCensused = new TreeSet<>(actual);
        registeredButNotCensused.removeAll(EXPECTED_ROUTES);

        Set<String> censusedButNotRegistered = new TreeSet<>(EXPECTED_ROUTES);
        censusedButNotRegistered.removeAll(actual);

        if (!registeredButNotCensused.isEmpty() || !censusedButNotRegistered.isEmpty()) {
            StringBuilder sb = new StringBuilder("Route census drift detected.\n");
            if (!registeredButNotCensused.isEmpty()) {
                sb.append("Registered in Router but MISSING from the S0.2 census (add to both this\n")
                  .append("test's EXPECTED_ROUTES and the plan doc's census table):\n");
                registeredButNotCensused.forEach(r -> sb.append("  + ").append(r).append('\n'));
            }
            if (!censusedButNotRegistered.isEmpty()) {
                sb.append("Listed in the census but NO LONGER registered in Router (route was\n")
                  .append("renamed/removed — update both this test and the plan doc's census table):\n");
                censusedButNotRegistered.forEach(r -> sb.append("  - ").append(r).append('\n'));
            }
            fail(sb.toString());
        }
    }

    /**
     * Reads {@code Router}'s private {@code routes} list via reflection and reconstructs each
     * route's "METHOD /path/pattern" signature from its private {@code method}/{@code parts}
     * fields. There is no public accessor by design — {@code Router} has no reason to expose
     * its route table to production callers, so this test reaches in deliberately rather than
     * widening Router's public API just to make itself easier to write.
     */
    @SuppressWarnings("unchecked")
    private static Set<String> reflectRegisteredRoutes(Router router) throws Exception {
        Field routesField = Router.class.getDeclaredField("routes");
        routesField.setAccessible(true);
        List<Object> routes = (List<Object>) routesField.get(router);

        Class<?> routeClass = Class.forName("com.appbana.api.Router$Route");
        Field methodField = routeClass.getDeclaredField("method");
        Field partsField = routeClass.getDeclaredField("parts");
        methodField.setAccessible(true);
        partsField.setAccessible(true);

        Set<String> result = new LinkedHashSet<>();
        for (Object route : routes) {
            String method = (String) methodField.get(route);
            List<String> parts = (List<String>) partsField.get(route);
            String path = "/" + String.join("/", parts);
            result.add(method + " " + path);
        }
        return result;
    }
}
