package com.appbana.api;

import com.appbana.UserManager;
import com.appbana.model.User;
import com.appbana.model.dto.UserDTO;
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

                // Return DTO (safe user) and token
                UserDTO safeUser = UserDTO.fromUser(user);

                // For now, use a simple token (e.g., user ID or a random string mapped to user)
                // In Phase 2, this will be a real JWT
                String token = UUID.randomUUID().toString();
                // TODO: Store token in a SessionManager or TokenService

                res.json(201, Map.of(
                        "user", safeUser,
                        "token", token,
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

                // Generate token (placeholder)
                String token = UUID.randomUUID().toString();
                // TODO: Store token

                res.json(200, Map.of(
                        "user", safeUser,
                        "token", token,
                        "message", "Login successful"));

            } catch (Exception e) {
                LOG.error("Login failed", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        };
    }
}
