package com.appbana.server;

import com.appbana.api.Router;
import com.appbana.middleware.RateLimitMiddleware;
import com.appbana.middleware.SessionMiddleware;
import com.appbana.server.routes.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central route registry that coordinates feature-specific route classes.
 * Organizes routes by functional area for better maintainability.
 */
public class RouteRegistry {
    private static final Logger LOG = LoggerFactory.getLogger(RouteRegistry.class);

    /**
     * Build and configure all application routes
     */
    public static Router buildRouter() {
        Router router = new Router();

        // Register global middlewares (run before all routes)
        LOG.info("Registering global middlewares");

        // Rate limiting middleware - protects all endpoints
        router.use(RateLimitMiddleware.create());
        LOG.info("Rate limiting middleware registered");

        // Session validation middleware - authenticates requests
        router.use(SessionMiddleware.create());
        LOG.info("Session middleware registered");

        // Register routes by feature area
        AuthRoutes.register(router);
        WorkflowRoutes.register(router);
        AppRoutes.register(router);
        AiRoutes.register(router); // AI Builder routes
        SchemaRoutes.register(router);
        GenericEntityRoutes.register(router);

        // Health and monitoring
        HealthRoutes.register(router);

        return router;
    }
}
