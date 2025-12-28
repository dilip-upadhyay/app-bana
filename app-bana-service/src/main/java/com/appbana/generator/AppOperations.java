package com.appbana.generator;

import com.appbana.AppManager;
import com.appbana.AiAppGeneratorService.GenerationRequest;
import com.appbana.AiAppGeneratorService.GenerationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * App CRUD operations: list, load, delete apps.
 * Handles ID resolution from user descriptions (ordinal, name matching).
 */
public class AppOperations {
    private static final Logger LOG = LoggerFactory.getLogger(AppOperations.class);

    // Action constants
    private static final String ACTION_LIST_APPS = "listApps";
    private static final String ACTION_LOAD_APP = "loadApp";
    private static final String ACTION_DELETE_APP = "deleteApp";
    private static final String PAYLOAD_APPS = "apps";
    private static final String PAYLOAD_ACTION = "action";
    private static final String PAYLOAD_REPLY = "reply";

    /**
     * Build a result containing the list of all apps
     */
    public static GenerationResult buildAppsListResult() {
        GenerationResult listResult = new GenerationResult();
        listResult.success = true;
        List<Map<String, Object>> apps = safeListApps();
        listResult.payload = new HashMap<>();
        listResult.payload.put(PAYLOAD_APPS, apps);
        listResult.payload.put(PAYLOAD_ACTION, ACTION_LIST_APPS);
        if (apps.isEmpty()) {
            listResult.payload.put(PAYLOAD_REPLY,
                    "You don't have any apps yet. Describe an app you want to build, for example 'Create a project management app with projects and tasks'.");
        } else {
            listResult.payload.put(PAYLOAD_REPLY,
                    "Here are your apps. You can say 'open the second app' or 'delete the project management app'.");
        }
        LOG.info("[AppOperations] Returning apps list: {}", apps);
        return listResult;
    }

    /**
     * Handle loading/opening an app
     */
    public static GenerationResult handleLoadApp(GenerationRequest request) {
        GenerationResult loadResult = new GenerationResult();
        String appId = resolveLoadAppId(request);
        if (appId == null || appId.isBlank()) {
            loadResult.success = false;
            loadResult.error = "Could not determine which app to load.";
            Map<String, Object> payload = new HashMap<>();
            payload.put(PAYLOAD_REPLY,
                    "I couldn't tell which app you meant. Try 'open the second app' or 'open Restaurant Management App'.");
            payload.put(PAYLOAD_ACTION, ACTION_LOAD_APP);
            loadResult.payload = payload;
            LOG.warn("[AppOperations] loadApp missing resolvable appId");
            return loadResult;
        }
        try {
            Map<String, Object> appWithPages = AppManager.getAppWithPages("default", appId);
            if (appWithPages == null) {
                loadResult.success = false;
                loadResult.error = "App not found: " + appId;
                loadResult.payload = new HashMap<>();
                loadResult.payload.put(PAYLOAD_REPLY, "App not found: " + appId);
                loadResult.payload.put(PAYLOAD_ACTION, ACTION_LOAD_APP);
                LOG.warn("[AppOperations] App not found: {}", appId);
            } else {
                loadResult.success = true;
                loadResult.payload = new HashMap<>();
                loadResult.payload.put("app", appWithPages.get("app"));
                loadResult.payload.put("pages", appWithPages.get("pages"));
                loadResult.payload.put(PAYLOAD_REPLY, "Opened app '" + appWithPages.getOrDefault("name", appId) + "'.");
                loadResult.payload.put(PAYLOAD_ACTION, ACTION_LOAD_APP);

                // Track opened app in context
                ConversationManager.updateOpenedApp(request.userId, appId);

                LOG.info("[AppOperations] Loaded app: {}", appId);
            }
        } catch (Exception e) {
            loadResult.success = false;
            loadResult.error = "Failed to load app: " + e.getMessage();
            loadResult.payload = new HashMap<>();
            loadResult.payload.put(PAYLOAD_REPLY, "Failed to load app: " + e.getMessage());
            loadResult.payload.put(PAYLOAD_ACTION, ACTION_LOAD_APP);
            LOG.error("[AppOperations] Failed to load app", e);
        }
        return loadResult;
    }

    /**
     * Handle deleting an app
     */
    public static GenerationResult handleDeleteApp(GenerationRequest request) {
        GenerationResult result = new GenerationResult();
        String resolvedId = resolveDeleteAppId(request);
        if (resolvedId == null || resolvedId.isBlank()) {
            result.success = false;
            result.error = "Could not determine which app to delete.";
            result.payload = new HashMap<>();
            result.payload.put("deleted", false);
            result.payload.put(PAYLOAD_REPLY,
                    "I couldn't tell which app you want to delete. Try 'delete the second app' after listing or 'delete Restaurant Management App'.");
            result.payload.put(PAYLOAD_ACTION, ACTION_DELETE_APP);
            return result;
        }
        try {
            boolean deleted = AppManager.deleteApp("default", resolvedId);
            result.success = deleted;
            result.payload = new HashMap<>();
            result.payload.put("deleted", deleted);
            result.payload.put(PAYLOAD_ACTION, ACTION_DELETE_APP);
            if (deleted) {
                result.payload.put(PAYLOAD_REPLY,
                        "Deleted app '" + resolvedId + "'. You can say 'show my apps' to confirm.");
            } else {
                result.payload.put(PAYLOAD_REPLY, "App not found: " + resolvedId);
            }
            LOG.info("[AppOperations] Delete attempt for app '{}': {}", resolvedId, deleted);
        } catch (Exception e) {
            result.success = false;
            result.error = "Failed to delete app: " + e.getMessage();
            result.payload = new HashMap<>();
            result.payload.put("deleted", false);
            result.payload.put(PAYLOAD_REPLY, "Failed to delete app: " + e.getMessage());
            result.payload.put(PAYLOAD_ACTION, ACTION_DELETE_APP);
            LOG.error("[AppOperations] Failed to delete app", e);
        }
        return result;
    }

    /**
     * Resolve app ID for load operation from user description and context
     */
    public static String resolveLoadAppId(GenerationRequest request) {
        if (request == null)
            return null;
        String desc = request.description != null ? request.description.toLowerCase(Locale.ROOT) : "";

        // Try ordinal resolution (first, second, third...)
        Integer indexFromText = extractOrdinalIndex(desc);
        if (indexFromText != null && request.conversationContext != null) {
            Object lastAppsObj = request.conversationContext.get("lastAppList");
            if (lastAppsObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> lastApps = (List<Map<String, Object>>) lastAppsObj;
                int idx = indexFromText - 1;
                if (idx >= 0 && idx < lastApps.size()) {
                    Object id = lastApps.get(idx).get("id");
                    if (id != null) {
                        LOG.info("[AppOperations] Resolved ordinal '{}' to app id '{}'", indexFromText, id);
                        return String.valueOf(id);
                    }
                }
            }
        }

        // Try name matching against last app list
        if (request.conversationContext != null) {
            Object lastAppsObj = request.conversationContext.get("lastAppList");
            if (lastAppsObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> lastApps = (List<Map<String, Object>>) lastAppsObj;
                for (Map<String, Object> app : lastApps) {
                    Object nameObj = app.get("name");
                    if (nameObj != null && desc.contains(nameObj.toString().toLowerCase(Locale.ROOT))) {
                        Object id = app.get("id");
                        if (id != null) {
                            LOG.info("[AppOperations] Resolved app by name '{}' to id '{}' from lastAppList", nameObj,
                                    id);
                            return String.valueOf(id);
                        }
                    }
                }
            }
        }

        // Check explicit appId option from classifier
        if (request.options != null && request.options.get("appId") != null) {
            Object rawAppId = request.options.get("appId");
            String appIdStr = String.valueOf(rawAppId);
            String lowered = appIdStr.toLowerCase(Locale.ROOT);
            if (!lowered.contains(" ") && !lowered.contains("first") && !lowered.contains("second")
                    && !lowered.contains("third") && !lowered.contains("fourth") && !lowered.contains("fifth")
                    && !lowered.contains("app")) {
                LOG.info("[AppOperations] Using appId from classifier: {}", appIdStr);
                return appIdStr;
            }
            LOG.info("[AppOperations] Ignoring non-id appId from classifier: {}", appIdStr);
        }

        // "first app" fallback
        if (request.description != null && request.description.toLowerCase(Locale.ROOT).contains("first app")) {
            List<Map<String, Object>> apps = safeListApps();
            if (apps != null && !apps.isEmpty() && apps.get(0).get("id") != null) {
                String pickedId = String.valueOf(apps.get(0).get("id"));
                LOG.info("[AppOperations] Auto-selected first app for load request: {}", pickedId);
                return pickedId;
            }
        }
        return null;
    }

    /**
     * Resolve app ID for delete operation
     */
    @SuppressWarnings("unchecked")
    public static String resolveDeleteAppId(GenerationRequest request) {
        if (request == null)
            return null;
        String desc = request.description != null ? request.description.toLowerCase(Locale.ROOT) : "";

        // 'this app' from current context
        if (desc.contains("this app") && request.conversationContext != null
                && request.conversationContext.get("currentAppId") != null) {
            return String.valueOf(request.conversationContext.get("currentAppId"));
        }

        // Ordinal resolution
        Integer ordinal = extractOrdinalIndex(desc);
        if (ordinal != null && request.conversationContext != null) {
            Object lastAppsObj = request.conversationContext.get("lastAppList");
            if (lastAppsObj instanceof List) {
                List<Map<String, Object>> lastApps = (List<Map<String, Object>>) lastAppsObj;
                int idx = ordinal - 1;
                if (idx >= 0 && idx < lastApps.size()) {
                    Object id = lastApps.get(idx).get("id");
                    if (id != null)
                        return String.valueOf(id);
                }
            }
        }

        // Name resolution against lastAppList
        if (request.conversationContext != null) {
            Object lastAppsObj = request.conversationContext.get("lastAppList");
            if (lastAppsObj instanceof List) {
                List<Map<String, Object>> lastApps = (List<Map<String, Object>>) lastAppsObj;
                for (Map<String, Object> app : lastApps) {
                    Object nameObj = app.get("name");
                    Object idObj = app.get("id");
                    if (nameObj != null && idObj != null
                            && desc.contains(nameObj.toString().toLowerCase(Locale.ROOT))) {
                        return String.valueOf(idObj);
                    }
                }
            }
        }

        // Explicit option appId
        if (request.options != null && request.options.get("appId") != null) {
            return String.valueOf(request.options.get("appId"));
        }
        return null;
    }

    /**
     * Extract ordinal index from text (first=1, second=2, etc.)
     */
    public static Integer extractOrdinalIndex(String text) {
        if (text == null)
            return null;
        if (text.contains("first"))
            return 1;
        if (text.contains("second"))
            return 2;
        if (text.contains("third"))
            return 3;
        if (text.contains("fourth"))
            return 4;
        if (text.contains("fifth"))
            return 5;
        Matcher m = Pattern.compile("\\b(\\d+)(st|nd|rd|th)?\\b").matcher(text);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    /**
     * Safely list apps, returning empty list on error
     */
    private static List<Map<String, Object>> safeListApps() {
        try {
            return AppManager.listApps("default");
        } catch (Exception e) {
            LOG.error("[AppOperations] Failed to list apps", e);
            return Collections.emptyList();
        }
    }
}
