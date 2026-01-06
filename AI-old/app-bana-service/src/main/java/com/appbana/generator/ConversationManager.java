package com.appbana.generator;

import com.appbana.AiAppGeneratorService.GenerationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Manages conversation context across user sessions for AI chat continuity.
 * Tracks app discussions, entity mentions, and pending generation plans.
 */
public class ConversationManager {
    private static final Logger LOG = LoggerFactory.getLogger(ConversationManager.class);
    private static final long CONTEXT_TIMEOUT_MS = 10 * 60 * 1000; // 10 minutes

    private static final Map<String, ConversationContext> sessionContexts = new HashMap<>();

    /**
     * Conversation context to maintain state across multiple requests
     */
    public static class ConversationContext {
        public String lastDiscussedAppType;
        public String lastDiscussedAppDescription;
        public List<String> discussedEntities;
        public String lastCreatedAppId;
        public String lastOpenedAppId;
        public GenerationResult pendingResult; // Staged app generation waiting for approval
        public long timestamp;

        public ConversationContext() {
            this.discussedEntities = new ArrayList<>();
            this.timestamp = System.currentTimeMillis();
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CONTEXT_TIMEOUT_MS;
        }

        public void refresh() {
            this.timestamp = System.currentTimeMillis();
        }
    }

    /**
     * Get or create conversation context for a user
     */
    public static ConversationContext getContext(String userId) {
        String key = userId != null ? userId : "default";
        ConversationContext ctx = sessionContexts.get(key);

        if (ctx == null || ctx.isExpired()) {
            ctx = new ConversationContext();
            sessionContexts.put(key, ctx);
            LOG.debug("[Context] Created new context for user: {}", key);
        } else {
            ctx.refresh();
        }

        return ctx;
    }

    /**
     * Update discussed app details (type and full description)
     */
    public static void updateDiscussedApp(String userId, String appType, String fullDescription) {
        ConversationContext ctx = getContext(userId);
        ctx.lastDiscussedAppType = appType;
        ctx.lastDiscussedAppDescription = fullDescription;
        ctx.refresh();
        LOG.info("[Context] Updated discussed app for {}: type={}", userId, appType);
    }

    /**
     * Track when user opens/loads an app
     */
    public static void updateOpenedApp(String userId, String appId) {
        ConversationContext ctx = getContext(userId);
        ctx.lastOpenedAppId = appId;
        ctx.refresh();
        LOG.info("[Context] User {} opened app: {}", userId, appId);
    }

    /**
     * Track when user creates a new app
     */
    public static void updateCreatedApp(String userId, String appId) {
        ConversationContext ctx = getContext(userId);
        ctx.lastCreatedAppId = appId;
        ctx.refresh();
        LOG.info("[Context] User {} created app: {}", userId, appId);
    }

    /**
     * Clear/reset context for a user (e.g., on logout or explicit reset)
     */
    public static void clearContext(String userId) {
        String key = userId != null ? userId : "default";
        sessionContexts.remove(key);
        LOG.info("[Context] Cleared context for user: {}", key);
    }

    /**
     * Cleanup expired sessions (can be called periodically)
     */
    public static int cleanupExpiredSessions() {
        int beforeSize = sessionContexts.size();
        sessionContexts.entrySet().removeIf(entry -> entry.getValue().isExpired());
        int removed = beforeSize - sessionContexts.size();
        if (removed > 0) {
            LOG.info("[Context] Cleaned up {} expired sessions", removed);
        }
        return removed;
    }
}
