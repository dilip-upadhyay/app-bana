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
     * Step 1c — if a field has type=reference but is missing referenceEntity,
     * try to infer it from the field name by matching against the known entity names.
     *
     * @param fields       mutable list of field maps for one entity
     * @param entityName   name of the owning entity (for log messages)
     * @param knownEntities set of entity names declared in this scaffold run
     */
    @SuppressWarnings("unchecked")
    private void inferMissingReferenceEntities(
            List<Map<String, Object>> fields,
            String entityName,
            Set<String> knownEntities) {

        for (Map<String, Object> field : fields) {
            if (!"reference".equals(field.get(F_TYPE))) continue;
            if (field.get("referenceEntity") != null) continue; // already set

            // Try to match field name/label against known entity names
            // Normalize: strip underscores/hyphens/spaces so "celestial_body" matches "CelestialBody"
            String fieldName       = String.valueOf(field.getOrDefault(F_NAME,  "")).toLowerCase();
            String fieldLabel      = String.valueOf(field.getOrDefault(F_LABEL, "")).toLowerCase();
            String fieldNameNorm   = fieldName.replaceAll("[_\\-\\s]", "");
            String fieldLabelNorm  = fieldLabel.replaceAll("[_\\-\\s]", "");

            String matched = null;
            for (String candidate : knownEntities) {
                String candidateLower = candidate.toLowerCase();
                String candidateNorm  = candidateLower.replaceAll("[_\\-\\s]", "");
                if (fieldName.contains(candidateLower)
                        || fieldLabel.contains(candidateLower)
                        || fieldNameNorm.contains(candidateNorm)
                        || fieldLabelNorm.contains(candidateNorm)) {
                    matched = candidate; // keep original casing
                    break;
                }
            }

            if (matched != null) {
                field.put("referenceEntity", matched);
                log.info("[SchemaEnricher] Entity '{}': inferred referenceEntity='{}' for field '{}'",
                        entityName, matched, field.get(F_NAME));
            } else {
                log.warn("[SchemaEnricher] Entity '{}': cannot infer referenceEntity for field '{}' — known entities: {}",
                        entityName, field.get(F_NAME), knownEntities);
            }
        }
    }

    /**
     * Enriches all entities in the list (no reference-entity inference).
     *
     * @param entities mutable list of entity maps from LLM output
     */
    public void enrichAll(List<Map<String, Object>> entities) {
        enrichAll(entities, Collections.emptySet());
    }

    /**
     * Enriches all entities in the list, including inference of missing
     * referenceEntity values using the supplied set of sibling entity names.
     *
     * @param entities      mutable list of entity maps from LLM output
     * @param knownEntities set of entity names declared in this scaffold run
     */
    @SuppressWarnings("unchecked")
    public void enrichAll(List<Map<String, Object>> entities, Set<String> knownEntities) {
        for (Map<String, Object> entity : entities) {
            enrich(entity);
            // After baseline enrichment, fix any reference fields with missing referenceEntity
            String entityName = (String) entity.getOrDefault(F_NAME, "unknown");
            List<Map<String, Object>> fields = (List<Map<String, Object>>) entity.get("fields");
            if (fields != null && !knownEntities.isEmpty()) {
                inferMissingReferenceEntities(fields, entityName, knownEntities);
            }
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
