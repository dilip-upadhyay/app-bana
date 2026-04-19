package com.appbana.ai.agent.tool;

import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * SchemaEnricher — Phase 1 of AI Schema Quality Plan
 *
 * Merges LLM-generated entity field lists with a mandatory baseline, ensuring
 * every entity always has structural fields (id, created_at, updated_at)
 * regardless of what the LLM chose to include.
 *
 * Applied post-LLM inside ScaffoldAppTool before entities are posted to the
 * backend. Zero LLM dependency — pure deterministic Java.
 */
@Slf4j
public class SchemaEnricher {

    // --- Field key constants ---
    private static final String F_NAME          = "name";
    private static final String F_LABEL         = "label";
    private static final String F_TYPE          = "type";
    private static final String F_REQUIRED      = "required";
    private static final String F_PRIMARY_KEY   = "primaryKey";
    private static final String F_AUTO_INC      = "autoIncrement";

    // --- Approved type constants ---
    private static final String T_TEXT     = "text";
    private static final String T_NUMBER   = "number";
    private static final String T_INTEGER  = "integer"; // backend validator requires this for autoIncrement PKs
    private static final String T_DECIMAL  = "decimal";
    private static final String T_DATETIME = "datetime";
    private static final String T_BOOLEAN  = "boolean";

    /** Field types approved by AppBana SchemaManager. Used for type coercion. */
    private static final Set<String> VALID_TYPES = Set.of(
            T_TEXT, "longtext", T_NUMBER, T_DECIMAL, T_BOOLEAN,
            "date", T_DATETIME, "email", "phone", "status", "reference");

    /** Fallback when LLM produces an invalid type (e.g. "currency", "float", "string"). */
    private static final String DEFAULT_TYPE = T_TEXT;

    /** Type aliases — common LLM hallucinations mapped to valid AppBana types. */
    private static final Map<String, String> TYPE_ALIASES = Map.of(
            "string",    T_TEXT,
            "varchar",   T_TEXT,
            "integer",   T_NUMBER,
            "int",       T_NUMBER,
            "float",     T_DECIMAL,
            "double",    T_DECIMAL,
            "money",     T_DECIMAL,
            "currency",  T_DECIMAL,
            "timestamp", T_DATETIME,
            "bool",      T_BOOLEAN);

    /**
     * Baseline fields injected at the front of every entity's field list.
     * Defined in insertion order: id first, then audit timestamps.
     */
    private static List<Map<String, Object>> baselineFields() {
        List<Map<String, Object>> fields = new ArrayList<>();

        Map<String, Object> id = new LinkedHashMap<>();
        id.put("id", "id");
        id.put(F_NAME, "id");
        id.put(F_TYPE, T_INTEGER); // backend requires 'integer' (not 'number') for autoIncrement PKs
        id.put(F_LABEL, "ID");
        id.put(F_REQUIRED, true);
        id.put(F_PRIMARY_KEY, true);
        id.put(F_AUTO_INC, true);
        fields.add(id);

        Map<String, Object> createdAt = new LinkedHashMap<>();
        createdAt.put("id", "created_at");
        createdAt.put(F_NAME, "created_at");
        createdAt.put(F_TYPE, T_DATETIME);
        createdAt.put(F_LABEL, "Created At");
        createdAt.put(F_REQUIRED, false);
        createdAt.put(F_PRIMARY_KEY, false);
        createdAt.put(F_AUTO_INC, false);
        fields.add(createdAt);

        Map<String, Object> updatedAt = new LinkedHashMap<>();
        updatedAt.put("id", "updated_at");
        updatedAt.put(F_NAME, "updated_at");
        updatedAt.put(F_TYPE, T_DATETIME);
        updatedAt.put(F_LABEL, "Updated At");
        updatedAt.put(F_REQUIRED, false);
        updatedAt.put(F_PRIMARY_KEY, false);
        updatedAt.put(F_AUTO_INC, false);
        fields.add(updatedAt);

        return fields;
    }

    /**
     * Enriches a single entity definition in-place:
     * 1. Normalises field types (coerces aliases, replaces unknowns with "text")
     * 2. Prepends any missing baseline fields (deduplicates by field name)
     *
     * @param entity mutable entity map from LLM output
     */
    @SuppressWarnings("unchecked")
    public void enrich(Map<String, Object> entity) {
        String entityName = (String) entity.getOrDefault(F_NAME, "unknown");

        List<Map<String, Object>> fields = (List<Map<String, Object>>) entity.get("fields");
        if (fields == null) {
            fields = new ArrayList<>();
            entity.put("fields", fields);
        }

        // Step 1 — normalise types
        for (Map<String, Object> field : fields) {
            String original = (String) field.get(F_TYPE);
            String coerced = coerceType(original);
            if (!coerced.equals(original)) {
                log.warn("[SchemaEnricher] Entity '{}': field '{}' type '{}' \u2192 '{}' (coerced)",
                        entityName, field.get(F_NAME), original, coerced);
                field.put(F_TYPE, coerced);
            }
        }

        // Step 1b — any autoIncrement+primaryKey field MUST be type 'integer'.
        // The backend SchemaManager.validate() only accepts 'int', 'integer', or 'long'.
        // The LLM and even SchemaEnricher's own baseline used 'number' — fix both.
        for (Map<String, Object> field : fields) {
            boolean isPk = Boolean.TRUE.equals(field.get(F_PRIMARY_KEY));
            boolean isAi = Boolean.TRUE.equals(field.get(F_AUTO_INC));
            if (isPk && isAi) {
                String type = (String) field.get(F_TYPE);
                boolean isIntegerCompatible = T_INTEGER.equals(type) || "int".equals(type) || "long".equals(type);
                if (!isIntegerCompatible) {
                    log.warn("[SchemaEnricher] Entity '{}': autoIncrement PK field '{}' has type '{}' \u2192 forcing 'integer'",
                            entityName, field.get(F_NAME), type);
                    field.put(F_TYPE, T_INTEGER);
                }
            }
        }

        // Step 2 — build set of existing field names (case-insensitive)
        Set<String> existingNames = new HashSet<>();
        for (Map<String, Object> field : fields) {
            String name = (String) field.get(F_NAME);
            if (name != null) existingNames.add(name.toLowerCase());
        }

        // Step 3 — prepend missing baseline fields at the front
        List<Map<String, Object>> toInsert = new ArrayList<>();
        for (Map<String, Object> baseline : baselineFields()) {
            String bName = (String) baseline.get(F_NAME);
            if (!existingNames.contains(bName.toLowerCase())) {
                toInsert.add(baseline);
                log.info("[SchemaEnricher] Entity '{}': injecting baseline field '{}'", entityName, bName);
            }
        }

        if (!toInsert.isEmpty()) {
            fields.addAll(0, toInsert);
        }
    }

    /**
     * Enriches all entities in the list.
     *
     * @param entities mutable list of entity maps from LLM output
     */
    public void enrichAll(List<Map<String, Object>> entities) {
        for (Map<String, Object> entity : entities) {
            enrich(entity);
        }
    }

    /**
     * Resolves a raw LLM-produced type string to a valid AppBana type.
     * Priority: exact match → alias lookup → fallback to "text".
     */
    private String coerceType(String raw) {
        if (raw == null) return DEFAULT_TYPE;
        String lower = raw.toLowerCase().trim();
        if (VALID_TYPES.contains(lower)) return lower;
        String aliased = TYPE_ALIASES.get(lower);
        if (aliased != null) return aliased;
        log.warn("[SchemaEnricher] Unknown type '{}' — falling back to '{}'", raw, DEFAULT_TYPE);
        return DEFAULT_TYPE;
    }
}
