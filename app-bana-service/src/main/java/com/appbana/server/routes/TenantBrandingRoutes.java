package com.appbana.server.routes;

import com.appbana.JdbcManager;
import com.appbana.api.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public branding endpoint for the AI-native UI rebuild (Stage 0).
 *
 * Route: GET /api/tenants/{tenantId}/branding
 *
 * Returns tenant display metadata that the runtime can load BEFORE login
 * so it can render the branded login screen. The endpoint is intentionally
 * public (no auth required) — it exposes only display data, not secrets.
 *
 * Response shape:
 * {
 *   "tenantId":     "default",
 *   "displayName":  "AppBana",
 *   "logoUrl":      null,
 *   "primaryColor": "#6366f1"
 * }
 */
public class TenantBrandingRoutes {

    private static final Logger LOG = LoggerFactory.getLogger(TenantBrandingRoutes.class);

    private static final String SELECT_SQL =
            "SELECT tenant_id, display_name, logo_url, primary_color " +
            "FROM appbana_tenants WHERE tenant_id = ?";

    public static void register(Router router) {
        router.get("/api/tenants/{tenantId}/branding", (req, res) -> {
            String tenantId = req.pathParam("tenantId");
            if (tenantId == null || tenantId.isBlank()) {
                res.json(400, Map.of("error", "tenantId is required"));
                return;
            }

            try (Connection conn = JdbcManager.getConnection();
                 PreparedStatement ps = conn.prepareStatement(SELECT_SQL)) {

                ps.setString(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Map<String, Object> out = new LinkedHashMap<>();
                        out.put("tenantId",     rs.getString("tenant_id"));
                        out.put("displayName",  rs.getString("display_name"));
                        out.put("logoUrl",      rs.getString("logo_url"));
                        out.put("primaryColor", rs.getString("primary_color"));
                        res.json(200, out);
                    } else {
                        // Return sensible defaults for any unknown tenant
                        Map<String, Object> defaults = new LinkedHashMap<>();
                        defaults.put("tenantId",     tenantId);
                        defaults.put("displayName",  "AppBana");
                        defaults.put("logoUrl",      null);
                        defaults.put("primaryColor", "#6366f1");
                        res.json(200, defaults);
                    }
                }

            } catch (Exception e) {
                LOG.error("Failed to fetch branding for tenant '{}'", tenantId, e);
                res.json(500, Map.of("error", "Failed to fetch branding: " + e.getMessage()));
            }
        });

        LOG.info("Registered route: GET /api/tenants/{tenantId}/branding (public)");
    }
}
