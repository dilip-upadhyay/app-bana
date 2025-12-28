package com.appbana.server;

import com.appbana.api.Router;
import com.appbana.server.routes.*;

/**
 * Central route registry that coordinates feature-specific route classes.
 * Organizes routes by functional area for better maintainability.
 */
public class RouteRegistry {

    /**
     * Build and configure all application routes
     */
    public static Router buildRouter() {
        Router router = new Router();

        // Register routes by feature area
        AuthRoutes.register(router);
        WorkflowRoutes.register(router);
        AppRoutes.register(router);
        AiRoutes.register(router);
        SchemaRoutes.register(router);
        GenericEntityRoutes.register(router);

        // Health and monitoring
        HealthRoutes.register(router);

        return router;
    }
}
