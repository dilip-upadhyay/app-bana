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
 * Subdomain-ready app-context endpoint for the AI-native UI rebuild (Stage 0).
 *
 * Route: GET /api/app-context?tenantId=...&appId=...
 *        GET /api/app-context?host=...&path=...   (subdomain mode — Stage 5)
 *
 * The runtime calls this on boot to discover which app it should render.
 * Designed so the resolution strategy can be swapped from path-based (today)
 * to hostname-based (Stage 5) with no frontend code changes.
 *
 * Response shape:
 * {
 *   "tenantId":  "default",
 *   "appId":     "7495460a-...",
 *   "branding":  { "displayName": "AppBana", "logoUrl": null, "primaryColor": "#6366f1" }
 * }
 */
public class AppContextRoutes {

    private static final Logger LOG = LoggerFactory.getLogger(AppContextRoutes.class);

    private static final String BRANDING_SQL =
            "SELECT display_name, logo_url, primary_color " +
            "FROM appbana_tenants WHERE tenant_id = ?";

    private AppContextRoutes() {}

    public static void register(Router router) {
        router.get("/api/app-context", (req, res) -> {
            // Phase 1: explicit query params (path-based resolution, used by runtime in dev)
            String tenantId = req.query("tenantId");
            String appId    = req.query("appId");

            // Phase 2 (Stage 5): hostname-based resolution
            // e.g. spice-shop.tenant42.apps.appbana.com → extract tenantId + appId
            String host = req.query("host");
            if ((tenantId == null || tenantId.isBlank()) && host != null && !host.isBlank()) {
                String[] resolved = resolveFromHost(host);
                tenantId = resolved[0];
                appId    = resolved[1];
            }

            if (tenantId == null || tenantId.isBlank()) tenantId = "default";

            Map<String, Object> branding = fetchBranding(tenantId);

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("tenantId", tenantId);
            out.put("appId",    appId != null ? appId : "");
            out.put("branding", branding);
            res.json(200, out);
        });

        LOG.info("Registered route: GET /api/app-context (public)");
    }

    /**
     * Resolve tenantId + appId from a subdomain hostname.
     * Convention: {appSlug}.{tenantId}.apps.appbana.com
     * Returns ["default", ""] if the host cannot be parsed.
     */
    private static String[] resolveFromHost(String host) {
        try {
            // Strip port if present
            String h = host.split(":")[0];
            String[] parts = h.split("\\.");
            // Expect at least: appSlug.tenantId.apps.appbana.com → 5 parts
            if (parts.length >= 5 && "apps".equals(parts[parts.length - 3])) {
                String tenantId = parts[parts.length - 4];
                // appId is not in the hostname for now; the runtime passes it separately
                return new String[]{tenantId, ""};
            }
        } catch (Exception e) {
            LOG.debug("Could not parse host '{}': {}", host, e.getMessage());
        }
        return new String[]{"default", ""};
    }

    private static Map<String, Object> fetchBranding(String tenantId) {
        Map<String, Object> branding = new LinkedHashMap<>();
        branding.put("displayName",  "AppBana");
        branding.put("logoUrl",      null);
        branding.put("primaryColor", "#6366f1");

        try (Connection conn = JdbcManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(BRANDING_SQL)) {
            ps.setString(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    branding.put("displayName",  rs.getString("display_name"));
                    branding.put("logoUrl",      rs.getString("logo_url"));
                    branding.put("primaryColor", rs.getString("primary_color"));
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to fetch branding for tenant '{}', using defaults: {}", tenantId, e.getMessage());
        }
        return branding;
    }
}
