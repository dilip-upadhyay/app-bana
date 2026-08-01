package com.appbana.api;

import com.appbana.UserManager;
import com.appbana.model.User;
import com.appbana.model.dto.UserDTO;
import com.appbana.service.SessionService;
import com.appbana.service.SessionService.SessionData;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

public class AuthenticationController {
    private static final Logger LOG = LoggerFactory.getLogger(AuthenticationController.class);

    public AuthenticationController() {
        UserManager.initialize();
    }

    public BiConsumer<Router.HttpRequest, Router.HttpResponse> register() {
        return (req, res) -> {
            try {
                // Parse request body
                Map<String, String> body = req.readJson(new TypeReference<>() {
                });
                String email = body.get("email");
                String password = body.get("password");
                String name = body.get("name");

                if (email == null || password == null) {
                    res.json(400, Map.of("error", "Email and password are required"));
                    return;
                }

                // Check if user exists
                if (UserManager.getUserByEmail(email) != null) {
                    res.json(409, Map.of("error", "User already exists"));
                    return;
                }

                // Create user
                User user = UserManager.register(name, email, password, null);

                // Return DTO (safe user) and session
                UserDTO safeUser = UserDTO.fromUser(user);

                // Create session using SessionService (Story 2.1); tenantId captured at login (S1.1)
                SessionData session = SessionService.createSession(String.valueOf(user.getId()), user.getTenantId());
                
                LOG.info("User registered successfully: {}", email);

                res.json(201, Map.of(
                        "user", safeUser,
                        "token", session.sessionId(),
                        "sessionId", session.sessionId(),
                        "message", "Registration successful"));

            } catch (Exception e) {
                LOG.error("Registration failed", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        };
    }

    public BiConsumer<Router.HttpRequest, Router.HttpResponse> login() {
        return (req, res) -> {
            try {
                Map<String, String> body = req.readJson(new TypeReference<>() {
                });
                String email = body.get("email");
                String password = body.get("password");

                if (email == null || password == null) {
                    res.json(400, Map.of("error", "Email and password are required"));
                    return;
                }

                User user = UserManager.authenticate(email, password);
                if (user == null) {
                    res.json(401, Map.of("error", "Invalid credentials"));
                    return;
                }

                UserDTO safeUser = UserDTO.fromUser(user);

                // Create session using SessionService (Story 2.1); tenantId captured at login (S1.1)
                SessionData session = SessionService.createSession(String.valueOf(user.getId()), user.getTenantId());
                
                LOG.info("User logged in successfully: {}", email);

                res.json(200, Map.of(
                        "user", safeUser,
                        "token", session.sessionId(),
                        "sessionId", session.sessionId(),
                        "message", "Login successful"));

            } catch (Exception e) {
                LOG.error("Login failed", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        };
    }
    
    public BiConsumer<Router.HttpRequest, Router.HttpResponse> profile() {
        return (req, res) -> {
            try {
                // Get session from request attribute (set by SessionMiddleware)
                String sessionId = (String) req.getAttribute("session");
                
                if (sessionId == null) {
                    res.json(401, Map.of("error", "Not authenticated"));
                    return;
                }
                
                // Validate session
                SessionData session = SessionService.validateSession(sessionId);
                if (session == null) {
                    res.json(401, Map.of("error", "Invalid or expired session"));
                    return;
                }
                
                // Get user from session
                String userId = session.userId();
                User user = UserManager.getUser(Long.parseLong(userId));
                
                if (user == null) {
                    res.json(404, Map.of("error", "User not found"));
                    return;
                }
                
                UserDTO safeUser = UserDTO.fromUser(user);
                res.json(200, Map.of("user", safeUser));
                
            } catch (Exception e) {
                LOG.error("Profile fetch failed", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        };
    }
}
