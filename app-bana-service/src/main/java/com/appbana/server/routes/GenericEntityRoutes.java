package com.appbana.server.routes;

import com.appbana.api.Router;

/**
 * Generic entity CRUD routes (dynamic entities based on schemas)
 * 
 * Provides RESTful CRUD operations for all entity types defined in schemas.
 * Routes are dynamically handled based on entity name in the path.
 * 
 * Supported operations:
 * - POST /api/{entity} - Create entity
 * - GET /api/{entity} - List entities with pagination/filtering
 * - GET /api/{entity}/{id} - Get entity by ID
 * - PUT /api/{entity}/{id} - Update entity
 * - DELETE /api/{entity}/{id} - Delete entity
 * - POST /api/{entity}/batch - Batch create
 * - POST /api/{entity}/bulk-delete - Bulk delete by IDs
 * - POST /api/{entity}/bulk-export - Export entities
 * 
 * Additional features:
 * - Field-level security (permissions)
 * - Query filtering and sorting
 * - Pagination
 * - Related entity loading
 * - Datasource configuration
 * 
 * Note: The actual route handlers remain in ApiServer.buildRouter()
 * for this release due to complexity. This class documents the
 * routes and provides the structure for future extraction.
 */
public class GenericEntityRoutes {

    public static void register(Router router) {
        // NOTE: Generic entity routes are currently registered in
        // ApiServer.buildRouter()
        // due to their complexity (600+ lines with nested authentication, permissions,
        // pagination, filtering, batch operations, etc.)
        //
        // Future refactoring can extract these routes here using a pattern like:
        //
        // router.post("/api/{entity}", GenericEntityHandler.create());
        // router.get("/api/{entity}", GenericEntityHandler.list());
        // router.get("/api/{entity}/{id}", GenericEntityHandler.get());
        // router.put("/api/{entity}/{id}", GenericEntityHandler.update());
        // router.delete("/api/{entity}/{id}", GenericEntityHandler.delete());
        // router.post("/api/{entity}/batch", GenericEntityHandler.batchCreate());
        // router.post("/api/{entity}/bulk-delete", GenericEntityHandler.bulkDelete());
        // router.post("/api/{entity}/bulk-export", GenericEntityHandler.bulkExport());
        //
        // Permissions routes:
        // router.post("/api/field-permissions", PermissionHandler.create());
        // router.get("/api/field-permissions", PermissionHandler.list());
        //
        // Datasource routes:
        // router.get("/ui/datasource/config", DatasourceHandler.getConfig());
        // router.get("/ui/datasource/list", DatasourceHandler.list());
        // router.post("/ui/datasource/save", DatasourceHandler.save());
        // router.post("/ui/datasource/test", DatasourceHandler.test());
        // router.post("/ui/datasource/activate", DatasourceHandler.activate());
        // router.post("/ui/datasource/delete", DatasourceHandler.delete());
    }
}
