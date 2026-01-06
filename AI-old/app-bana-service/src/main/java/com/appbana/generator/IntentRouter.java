package com.appbana.generator;

import com.appbana.AiAppGeneratorService.GenerationRequest;
import com.appbana.ai.IntentCache;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Intent classification and action routing.
 * Combines rule-based matching with cached AI classifications.
 */
public class IntentRouter {
    private static final Logger LOG = LoggerFactory.getLogger(IntentRouter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    // Action constants
    private static final String ACTION_LIST_APPS = "listApps";
    private static final String ACTION_LOAD_APP = "loadApp";
    private static final String ACTION_DELETE_APP = "deleteApp";
    private static final String ACTION_LIST_PAGES = "listPages";
    private static final String ACTION_OPEN_PAGE = "openPage";
    private static final String ACTION_GENERATE_APP = "generateApp";
    private static final String ACTION_DESCRIBE_APP = "describeApp";

    /**
     * Resolve action from request (checks explicit action, then description)
     */
    public static String resolveAction(GenerationRequest request) {
        if (request == null) {
            return null;
        }

        if (request.action != null && !request.action.isBlank()) {
            request.action = normalizeActionLabel(request.action);
            return request.action;
        }

        if (request.description == null || request.description.isBlank()) {
            return null;
        }

        // Try rule-based classification first (no GPT needed)
        Map<String, Object> ruleBasedResult = classifyWithRules(request.description);
        if (ruleBasedResult != null) {
            LOG.info("[IntentRouter] Rule-based classification succeeded for: {}", request.description);
            Object classifiedAction = ruleBasedResult.get("action");
            if (classifiedAction != null) {
                request.action = normalizeActionLabel(String.valueOf(classifiedAction));
            }
            if ((request.options == null || request.options.isEmpty()) && ruleBasedResult.get("options") != null) {
                request.options = MAPPER.convertValue(ruleBasedResult.get("options"), MAP_TYPE);
            }
            return request.action;
        }

        // Try intent cache
        IntentCache.ActionDescriptor cached = IntentCache.get(request.description);
        if (cached != null) {
            LOG.info("[IntentRouter] Intent cache hit for: {}", request.description);
            request.action = normalizeActionLabel(cached.action);
            if (request.options == null || request.options.isEmpty()) {
                request.options = cached.options;
            }
            return request.action;
        }

        // Fall back to GPT classification (delegated to caller or heuristics)
        LOG.info("[IntentRouter] Using heuristic fallback classification for: {}", request.description);
        Map<String, Object> classification = heuristicClassification(request.description);
        if (classification == null) {
            return null;
        }

        Object classifiedAction = classification.get("action");
        if (classifiedAction != null) {
            request.action = normalizeActionLabel(String.valueOf(classifiedAction));
        }

        if ((request.options == null || request.options.isEmpty()) && classification.get("options") != null) {
            request.options = MAPPER.convertValue(classification.get("options"), MAP_TYPE);
        }

        // Store in cache for future use
        if (request.action != null) {
            IntentCache.ActionDescriptor descriptor = new IntentCache.ActionDescriptor(request.action);
            descriptor.options = request.options != null ? request.options : new HashMap<>();
            IntentCache.put(request.description, descriptor);
            LOG.info("[IntentRouter] Stored classification in cache: {} -> {}", request.description, request.action);
        }

        return request.action;
    }

    /**
     * Rule-based classification - handles common commands without AI
     * Returns Map with "action" and optional "options" keys, or null if no match
     */
    public static Map<String, Object> classifyWithRules(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }

        String lower = description.toLowerCase(Locale.ROOT).trim();
        Map<String, Object> result = new HashMap<>();

        // List apps commands
        if (lower.matches("^(show|list|display|get|view|see) (my |all )?apps?$")
                || lower.equals("apps")
                || lower.equals("my apps")
                || lower.equals("what apps do i have")
                || lower.equals("show me my apps")
                || lower.contains("list all apps")
                || lower.contains("show all apps")) {
            result.put("action", ACTION_LIST_APPS);
            return result;
        }

        // Show fields commands
        if (lower.matches("^(show|list|describe|what are the) fields? (of|for|in) \\w+$")
                || lower.contains("show fields")
                || lower.contains("list fields")
                || lower.contains("what fields does")
                || lower.contains("fields in")) {
            result.put("action", "listFields");
            return result;
        }

        // Help commands
        if (lower.matches("^(help|what can you do|capabilities|commands)$")
                || lower.equals("?")
                || lower.contains("what can i")
                || lower.contains("how do i")
                || lower.contains("show me help")) {
            result.put("action", "help");
            return result;
        }

        return null; // No rule match
    }

    /**
     * Heuristic classification fallback (when AI is not available)
     */
    private static Map<String, Object> heuristicClassification(String userText) {
        String lower = userText == null ? "" : userText.toLowerCase(Locale.ROOT);
        Map<String, Object> out = new HashMap<>();

        if (lower.matches(".*(list|show).*(apps|app list).*") || lower.contains("my apps")) {
            out.put("action", ACTION_LIST_APPS);
            out.put("options", new HashMap<>());
            return out;
        }

        if (lower.matches(".*(describe|explain|summary|overview).*(app|application|this).*") ||
                lower.equals("explain app") || lower.equals("describe app") ||
                lower.matches(".*(what|tell|talk).*(about).*(app|application|this|selected|current).*") ||
                lower.contains("app selected") ||
                lower.contains("current app") ||
                lower.contains("this app")) {
            out.put("action", ACTION_DESCRIBE_APP);
            out.put("options", new HashMap<>());
            return out;
        }

        if (lower.matches(".*(open|load).*(app).*")) {
            out.put("action", ACTION_LOAD_APP);
            out.put("options", new HashMap<>());
            return out;
        }

        if (lower.matches(".*delete.*app.*")) {
            out.put("action", ACTION_DELETE_APP);
            out.put("options", new HashMap<>());
            return out;
        }

        if (lower.matches(".*(list|show|how many|count|number of).*pages.*")) {
            Map<String, Object> opts = new HashMap<>();
            Matcher m = Pattern.compile("(in|for|of) ([A-Za-z0-9 _-]+)").matcher(lower);
            if (m.find())
                opts.put("appName", m.group(2).trim());
            out.put("action", ACTION_LIST_PAGES);
            out.put("options", opts);
            return out;
        }

        // Handle "open/show/go to [page name] page"
        if (lower.matches(".*(open|show|load|view|goto|display).*(page).*")) {
            Map<String, Object> opts = new HashMap<>();
            Matcher m1 = Pattern.compile("(open|show|load|view|goto|display) (?:the )?([A-Za-z0-9 _-]+?) page")
                    .matcher(lower);
            if (m1.find()) {
                opts.put("pageName", m1.group(2).trim());
            } else {
                Matcher m2 = Pattern.compile("(?:open|show|load|view|goto|display) (?:the )?(.+?) page").matcher(lower);
                if (m2.find()) {
                    opts.put("pageName", m2.group(1).trim());
                }
            }
            out.put("action", ACTION_OPEN_PAGE);
            out.put("options", opts);
            return out;
        }

        out.put("action", ACTION_GENERATE_APP);
        out.put("options", new HashMap<>());
        return out;
    }

    /**
     * Normalize action labels to canonical form
     */
    public static String normalizeActionLabel(String action) {
        if (action == null || action.isBlank()) {
            return null;
        }
        String lower = action.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "list" -> ACTION_LIST_APPS;
            case "open", "load" -> ACTION_LOAD_APP;
            case "delete", "remove" -> ACTION_DELETE_APP;
            case "pages" -> ACTION_LIST_PAGES;
            case "generate", "create", "build" -> ACTION_GENERATE_APP;
            default -> action;
        };
    }
}
