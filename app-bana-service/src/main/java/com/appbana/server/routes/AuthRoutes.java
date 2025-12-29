package com.appbana.server.routes;

import com.appbana.api.AuthenticationController;
import com.appbana.api.CsrfController;
import com.appbana.api.GenericAppAuthController;
import com.appbana.api.Router;

/**
 * Authentication and authorization routes
 */
public class AuthRoutes {

    public static void register(Router router) {
        // Builder authentication
        AuthenticationController authController = new AuthenticationController();
        router.post("/api/auth/register", authController.register());
        router.post("/api/auth/login", authController.login());

        // Runtime authentication (for generated apps)
        router.post("/api/runtime/auth/login", GenericAppAuthController.login());
        
        // CSRF protection endpoints (Story 1.2)
        router.get("/api/csrf-token", CsrfController.generateToken());
        router.post("/api/csrf-validate", CsrfController.validateToken());
    }
}
