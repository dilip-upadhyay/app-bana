package com.appbana.server.routes;

import com.appbana.api.Router;

import java.util.LinkedHashMap;
import java.util.Map;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

import com.appbana.config.AppConfig;
import com.appbana.config.ConfigManager;
import com.appbana.JdbcManager;

/**
 * Health check and monitoring routes
 */
public class HealthRoutes {

    public static void register(Router router) {
        // Simple health check
        router.get("/health", (req, res) -> res.json(200, Map.of("status", "UP")));

        // Detailed readiness check with DB connection
        router.get("/ready", (req, res) -> {
            long start = System.currentTimeMillis();
            try (Connection c = JdbcManager.getConnection()) {
                DatabaseMetaData md = c.getMetaData();
                long elapsed = System.currentTimeMillis() - start;
                AppConfig cfg = ConfigManager.getConfig();
                String active = cfg.getActiveDatasource();

                Map<String, Object> out = new LinkedHashMap<>();
                out.put("ok", true);
                out.put("activeDatasource", active);
                out.put("dbProduct", md.getDatabaseProductName());
                out.put("dbVersion", md.getDatabaseProductVersion());
                out.put("elapsedMs", elapsed);
                res.json(200, out);
            } catch (Exception ce) {
                long elapsed = System.currentTimeMillis() - start;
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("ok", false);
                out.put("error", ce.getMessage());
                out.put("elapsedMs", elapsed);
                res.json(503, out);
            }
        });
    }
}
