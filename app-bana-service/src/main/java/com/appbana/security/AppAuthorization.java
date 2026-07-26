package com.appbana.security;

import com.appbana.AppManager;
import com.appbana.model.AppMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AppAuthorization — Task C1.10 & C1.12
 *
 * Centralized authorization helper for app-level ownership checks.
 */
public final class AppAuthorization {
    private static final Logger LOG = LoggerFactory.getLogger(AppAuthorization.class);

    private AppAuthorization() {
        // Utility class
    }

    /**
     * Checks if callerUserId is authorized to modify or manage an app (or its schemas/roles).
     * Authorized if:
     * 1. caller is "system"
     * 2. caller is the exact author/creator of the app
     *
     * @param tenantId     tenant ID of the app
     * @param appId        app ID
     * @param callerUserId authenticated user ID
     * @return true if caller is authorized, false otherwise
     */
    public static boolean isAppOwnerOrSystem(String tenantId, String appId, String callerUserId) {
        if ("system".equalsIgnoreCase(callerUserId)) {
            return true;
        }
        if (callerUserId == null || callerUserId.isBlank()) {
            return false;
        }
        try {
            AppMetadata app = AppManager.getApp(tenantId, appId);
            if (app != null && app.getAuthor() != null) {
                return callerUserId.equals(app.getAuthor());
            }
        } catch (Exception e) {
            LOG.warn("[AppAuthorization] Failed to retrieve app metadata for ({}, {}): {}", tenantId, appId, e.getMessage());
        }
        return false;
    }
}
