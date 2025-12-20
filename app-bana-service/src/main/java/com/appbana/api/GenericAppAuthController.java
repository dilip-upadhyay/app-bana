package com.appbana.api;

import com.appbana.JdbcManager;
import com.appbana.SchemaManager;
import com.appbana.model.EntitySchema;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Controller for handling authentication for generated applications.
 * Allows end-users to login against the generated app's database tables.
 */
public class GenericAppAuthController {
    private static final Logger LOG = LoggerFactory.getLogger(GenericAppAuthController.class);

    public static BiConsumer<Router.HttpRequest, Router.HttpResponse> login() {
        return (req, res) -> {
            try {
                // Parse request
                Map<String, String> body = req.readJson(new TypeReference<>() {
                });

                String entityName = body.getOrDefault("entity", "User");
                String email = body.get("email");
                String password = body.get("password");

                if (email == null || password == null) {
                    res.json(400, Map.of("error", "Email and password are required"));
                    return;
                }

                // 1. Resolve Entity Schema to find table and valid fields
                EntitySchema schema = SchemaManager.loadSchema(entityName);
                if (schema == null) {
                    // Try case-insensitive lookup if standard lookup fails
                    // (Simple heuristic: most apps just have "User" or "Users")
                    if ("User".equalsIgnoreCase(entityName)) {
                        schema = SchemaManager.loadSchema("User");
                        if (schema == null)
                            schema = SchemaManager.loadSchema("Users");
                    }
                }

                if (schema == null) {
                    res.json(404, Map.of("error", "Entity definition not found for: " + entityName));
                    return;
                }

                // 2. Identify connection/table
                String dsName = schema.getDatasourceName();
                String tableName = schema.getName(); // In SchemaManager, schema name IS table name usually

                // 3. Query DB
                // TODO: Phase 2 - Use password hashing. Phase 1 assumes plain text or simple
                // matching for Prototype apps.
                String sql = "SELECT * FROM \"" + tableName.toUpperCase() + "\" WHERE email = ? AND password = ?";

                JdbcManager.ensureMetaTableFor(dsName);
                try (Connection conn = JdbcManager.getConnection(dsName);
                        PreparedStatement ps = conn.prepareStatement(sql)) {

                    ps.setString(1, email);
                    ps.setString(2, password);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            // User found! Return valid record data
                            Map<String, Object> userData = new HashMap<>();
                            ResultSetMetaData meta = rs.getMetaData();
                            int colCount = meta.getColumnCount();

                            for (int i = 1; i <= colCount; i++) {
                                String colName = meta.getColumnName(i).toLowerCase();
                                // Exclude password from response
                                if (!colName.contains("password") && !colName.contains("secret")) {
                                    userData.put(colName, rs.getObject(i));
                                }
                            }

                            // Return success
                            res.json(200, Map.of(
                                    "user", userData,
                                    "token", "mock-jwt-token-for-" + email, // Mock token for now
                                    "message", "Login successful"));
                            return;
                        }
                    }
                }

                res.json(401, Map.of("error", "Invalid credentials"));

            } catch (Exception e) {
                LOG.error("Generic App Login failed", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        };
    }
}
