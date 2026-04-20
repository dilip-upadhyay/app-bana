package com.appbana.ai.dialogue;

import com.appbana.ai.rag.ConversationMemory;
import lombok.Getter;

import java.util.List;
import java.util.Locale;

/**
 * ConversationSpec — Phase 3 of AI Schema Quality Plan
 *
 * Analyses the full chat history for a session and determines which
 * requirements topics have been discussed. Produces a lightweight checklist
 * that {@code AiAgent.buildAgentPrompt()} injects into the system prompt so
 * the LLM naturally asks about missing items before rushing to build.
 *
 * Detection is intentionally keyword-based (no extra LLM call). Fast, cheap,
 * and deterministic — a small set of signals is enough to nudge the model.
 */
public class ConversationSpec {

    // ── Checklist item IDs (used as keys in the prompt snippet) ──────────────
    public static final String ITEM_ENTITIES        = "core_entities";
    public static final String ITEM_RELATIONSHIPS   = "data_relationships";
    public static final String ITEM_USER_ROLES      = "user_roles_access";
    public static final String ITEM_REPORTING       = "reporting_needs";
    public static final String ITEM_CONFIRMATION    = "user_confirmed";

    // ── Observed flags ────────────────────────────────────────────────────────
    @Getter private final boolean entitiesDiscussed;
    @Getter private final boolean relationshipsDiscussed;
    @Getter private final boolean userRolesDiscussed;
    @Getter private final boolean reportingDiscussed;
    @Getter private final boolean userConfirmed;
    @Getter private final boolean modificationRequested;

    // Discovery of existing context
    @Getter private final String currentAppName;
    @Getter private final String currentAppId;

    private ConversationSpec(boolean entities, boolean relationships,
                              boolean userRoles, boolean reporting, boolean confirmed, boolean modification,
                              String appName, String appId) {
        this.entitiesDiscussed      = entities;
        this.relationshipsDiscussed = relationships;
        this.userRolesDiscussed     = userRoles;
        this.reportingDiscussed     = reporting;
        this.userConfirmed          = confirmed;
        this.modificationRequested  = modification;
        this.currentAppName        = appName;
        this.currentAppId          = appId;
    }

    /**
     * Analyse a chat history and the current user message, then return a
     * populated {@code ConversationSpec}.
     *
     * @param history      past turns for this session (may be empty)
     * @param currentMsg   the message the user just sent
     */
    public static ConversationSpec analyse(List<ConversationMemory.Conversation> history,
                                           String currentMsg) {
        // Concatenate all text (user messages + assistant responses) for scanning
        StringBuilder corpus = new StringBuilder();
        if (history != null) {
            for (ConversationMemory.Conversation turn : history) {
                if (turn.getMessage() != null) corpus.append(turn.getMessage()).append(' ');
                if (turn.getResponse() != null) corpus.append(turn.getResponse()).append(' ');
            }
        }
        if (currentMsg != null) corpus.append(currentMsg);

        String text = corpus.toString().toLowerCase(Locale.ROOT);

        boolean entities      = containsAny(text,
                "entity", "entities", "table", "tables", "module", "track",
                "customer", "order", "product", "employee", "invoice", "item",
                "record", "store", "manage");

        boolean relationships = containsAny(text,
                "belong", "related", "relationship", "link", "connect",
                "reference", "has many", "one to", "many to", "foreign");

        boolean userRoles     = containsAny(text,
                "role", "permission", "access", "admin", "staff", "login",
                "user type", "who can", "restrict", "authorize");

        boolean reporting     = containsAny(text,
                "report", "dashboard", "chart", "analytics", "summary",
                "export", "statistic", "insight", "trend", "total");

        boolean confirmed     = containsAny(text,
                "yes", "build it", "proceed", "go ahead", "let's build",
                "sounds good", "looks good", "approved", "confirm", "correct");

        // Discovery of existing app context from history (look for IDs and Names)
        String foundAppName = null;
        String foundAppId = null;

        // Scan history for "App Name: ..." or "App ID: ..." or patterns from logs/scaffold tool
        if (history != null) {
            for (ConversationMemory.Conversation turn : history) {
                String fullTurn = (turn.getMessage() + " " + turn.getResponse()).toLowerCase(Locale.ROOT);
                
                // Extract App ID (UUID pattern)
                if (foundAppId == null) {
                    java.util.regex.Matcher idMatcher = java.util.regex.Pattern.compile("([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})").matcher(fullTurn);
                    if (idMatcher.find()) {
                        foundAppId = idMatcher.group(1);
                    }
                }
                
                // Extract App Name
                if (foundAppName == null) {
                    if (fullTurn.contains("app name:") || fullTurn.contains("application name:")) {
                        int index = fullTurn.indexOf("app name:") != -1 ? fullTurn.indexOf("app name:") : fullTurn.indexOf("application name:");
                        int start = fullTurn.indexOf(":", index) + 1;
                        int end = fullTurn.indexOf("\n", start);
                        if (end == -1) end = Math.min(start + 50, fullTurn.length());
                        foundAppName = fullTurn.substring(start, end).trim();
                    }
                }
            }
        }

        return new ConversationSpec(entities, relationships, userRoles, reporting, confirmed, modification, foundAppName, foundAppId);
    }

    /**
     * Returns true when the spec is complete enough that the agent should
     * consider building (at least entities discussed and user confirmed).
     */
    public boolean isReadyToBuild() {
        return entitiesDiscussed && userConfirmed;
    }

    /**
     * Renders a compact prompt snippet (a few lines) listing covered and missing
     * topics. Injected verbatim into the agent system prompt.
     *
     * Returns an empty string when the conversation has not yet reached the
     * app-building phase (no entities discussed at all) to avoid cluttering
     * early general-chat turns.
     */
    public String toPromptSnippet() {
        // Don't inject anything for general / non-build conversations
        if (!entitiesDiscussed) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("## SPEC COVERAGE TRACKER\n");
        sb.append("Below is what has already been discussed. ");
        sb.append("Before calling scaffold_app, ensure all items are covered.\n\n");

        appendItem(sb, entitiesDiscussed,      "Core entities / data to track");
        appendItem(sb, relationshipsDiscussed,  "Data relationships between entities");
        appendItem(sb, userRolesDiscussed,      "User roles / access levels");
        appendItem(sb, reportingDiscussed,      "Reporting or dashboard needs");
        appendItem(sb, userConfirmed,           "Explicit user confirmation to build");

        if (!isReadyToBuild()) {
            sb.append("\nIMPORTANT: Items marked ✗ have NOT been discussed. ");
            sb.append("Ask about them naturally in your next response ");
            sb.append("before proceeding to build.\n");
        }

        if (currentAppName != null || currentAppId != null) {
            sb.append("\n## ACTIVE APP CONTEXT\n");
            if (currentAppName != null) sb.append("  - APP NAME: ").append(currentAppName).append("\n");
            if (currentAppId != null) sb.append("  - APP ID:   ").append(currentAppId).append("\n");
            sb.append("IMPORTANT: Use this existing app context for any updates or modification requests.\n");
        }

        sb.append('\n');
        return sb.toString();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void appendItem(StringBuilder sb, boolean covered, String label) {
        sb.append(covered ? "  ✓ " : "  ✗ ").append(label).append('\n');
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }
}
