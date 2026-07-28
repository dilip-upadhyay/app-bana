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
    private static final String F_ID            = "id";
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
            "date", T_DATETIME, "email", "phone", "status", "reference", "file");

    /** Fallback when LLM produces an invalid type (e.g. "currency", "float", "string"). */
    private static final String DEFAULT_TYPE = T_TEXT;

    /** Type aliases — common LLM hallucinations mapped to valid AppBana types. */
    private static final Map<String, String> TYPE_ALIASES = Map.ofEntries(
            Map.entry("string",     T_TEXT),
            Map.entry("varchar",    T_TEXT),
            Map.entry("integer",    T_NUMBER),
            Map.entry("int",        T_NUMBER),
            Map.entry("float",      T_DECIMAL),
            Map.entry("double",     T_DECIMAL),
            Map.entry("money",      T_DECIMAL),
            Map.entry("currency",   T_DECIMAL),
            Map.entry("timestamp",  T_DATETIME),
            Map.entry("bool",       T_BOOLEAN),
            // Phase B3 — common file-ish aliases → file
            Map.entry("document",   "file"),
            Map.entry("attachment", "file"),
            Map.entry("upload",     "file"),
            Map.entry("image",      "file"),
            Map.entry("photo",      "file"));

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

        // Step 0 — normalise field NAMES to snake_case (identifier discipline).
        // The AI prompt asks the LLM to emit snake_case, but nothing downstream
        // enforces it. If the LLM emits "Full Name" or "firstName", it propagates
        // all the way to the DB as a quoted identifier with spaces/casing, which
        // sets up drift bugs the moment the schema is renamed. Normalise once here
        // so every downstream consumer (backend, page metadata, runtime) sees the
        // same canonical name. The original label is preserved for display.
        normaliseFieldNames(fields, entityName);

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

        // Step 1c — every field with type=status MUST have a non-empty options[].
        // When the LLM omits options (common), inject a domain-neutral default so
        // the generated form renders a real <select> instead of a free-text input.
        // The runtime is defensive against missing options, but the UX defect
        // ("status" as a text box) leaked through in early Customer Onboarding
        // apps. Enforce at the metadata boundary so every future app benefits.
        enforceStatusOptions(fields, entityName);

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

        // Step 4 — Task C1.2: if approvalRequired is true, inject approval columns
        boolean approvalRequired = Boolean.TRUE.equals(entity.get("approvalRequired"));
        if (approvalRequired) {
            injectApprovalFields(fields, entityName);
        }
    }

    /**
     * Injects the 8 standard approval columns for entities with approvalRequired: true.
     *
     * <p>Task C4.3 — the canonical definition always wins over an LLM-authored one.
     * This method used to SKIP injecting a column whose name the LLM had already
     * emitted. That is exactly backwards for the approval columns: if the model
     * invented its own {@code approval_status} (say with options Yes/No, or as
     * plain text), the entity came out flagged {@code approvalRequired} but with
     * a status column the state machine cannot drive — DRAFT/PENDING/APPROVED/
     * REJECTED were not valid values for it. Same class of hazard the backend hit
     * in Review #8, where a hand-declared approval column shadowed the synthetic
     * one and two lookup paths disagreed about which definition was authoritative.
     * Now a colliding user field is renamed out of the way to {@code workflow_<name>}
     * (preserving whatever the user actually wanted to model) and the canonical
     * column is injected unconditionally, so there is exactly one definition and
     * it is always the platform's.
     */
    private static void injectApprovalFields(List<Map<String, Object>> fields, String entityName) {
        List<Map<String, Object>> approvalCols = new ArrayList<>();

        approvalCols.add(createField("approval_status", "approval_status", "status", "Approval Status", false, false, false, List.of("DRAFT", "PENDING", "APPROVED", "REJECTED")));
        approvalCols.add(createField("approval_revision", "approval_revision", T_NUMBER, "Approval Revision", false, false, false, null));
        approvalCols.add(createField("approval_parent_id", "approval_parent_id", T_TEXT, "Approval Parent ID", false, false, false, null));
        approvalCols.add(createField("submitted_by", "submitted_by", T_TEXT, "Submitted By", false, false, false, null));
        approvalCols.add(createField("submitted_at", "submitted_at", T_DATETIME, "Submitted At", false, false, false, null));
        approvalCols.add(createField("approved_by", "approved_by", T_TEXT, "Approved By", false, false, false, null));
        approvalCols.add(createField("approved_at", "approved_at", T_DATETIME, "Approved At", false, false, false, null));
        approvalCols.add(createField("rejection_reason", "rejection_reason", "longtext", "Rejection Reason", false, false, false, null));

        Set<String> reserved = new HashSet<>();
        for (Map<String, Object> col : approvalCols) {
            reserved.add(((String) col.get(F_NAME)).toLowerCase());
        }

        // Step 1 — move any user-authored field that squats on a reserved approval
        // name aside, rather than letting it suppress the canonical definition.
        Set<String> takenNames = new HashSet<>();
        for (Map<String, Object> field : fields) {
            String name = (String) field.get(F_NAME);
            if (name != null) takenNames.add(name.toLowerCase());
        }
        for (Map<String, Object> field : fields) {
            String name = (String) field.get(F_NAME);
            if (name == null || !reserved.contains(name.toLowerCase())) continue;

            String renamed = "workflow_" + name;
            int suffix = 2;
            while (takenNames.contains(renamed.toLowerCase()) || reserved.contains(renamed.toLowerCase())) {
                renamed = "workflow_" + name + "_" + suffix++;
            }
            log.warn("[SchemaEnricher] Entity '{}': field '{}' collides with the reserved approval "
                    + "column of the same name; renaming it to '{}' so the canonical approval "
                    + "definition stays authoritative", entityName, name, renamed);
            field.put(F_NAME, renamed);
            field.put("id", renamed);
            takenNames.add(renamed.toLowerCase());
        }

        // Step 2 — the canonical columns are now guaranteed collision-free.
        for (Map<String, Object> col : approvalCols) {
            fields.add(col);
            log.info("[SchemaEnricher] Entity '{}': injecting approval column '{}'", entityName, col.get(F_NAME));
        }
    }

    private static Map<String, Object> createField(String id, String name, String type, String label, boolean required, boolean pk, boolean ai, List<String> options) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("id", id);
        field.put(F_NAME, name);
        field.put(F_TYPE, type);
        field.put(F_LABEL, label);
        field.put(F_REQUIRED, required);
        field.put(F_PRIMARY_KEY, pk);
        field.put(F_AUTO_INC, ai);
        if (options != null) {
            field.put("options", new ArrayList<>(options));
        }
        return field;
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

    /** Default status pipeline used when the LLM omits options for a status field. */
    static final List<String> DEFAULT_STATUS_OPTIONS = List.of(
            "New", "In Progress", "Completed", "Cancelled");

    /**
     * Step 1c helper — every field with type=status must have a non-empty options[].
     * Missing/empty options get {@link #DEFAULT_STATUS_OPTIONS} injected. This closes
     * the "status renders as free-text input" UX defect at the metadata boundary so
     * every downstream consumer (page generator, runtime) sees a well-formed status.
     */
    static void enforceStatusOptions(List<Map<String, Object>> fields, String entityName) {
        for (Map<String, Object> field : fields) {
            if (!"status".equals(field.get(F_TYPE))) continue;
            Object opts = field.get("options");
            boolean missing = opts == null;
            boolean empty   = opts instanceof List<?> list && list.isEmpty();
            if (missing || empty) {
                field.put("options", new ArrayList<>(DEFAULT_STATUS_OPTIONS));
                log.warn("[SchemaEnricher] Entity '{}': status field '{}' had no options — injected default {}",
                        entityName, field.get(F_NAME), DEFAULT_STATUS_OPTIONS);
            }
        }
    }

    /**
     * Step 0 helper — normalise every field name in the list to snake_case,
     * preserving the original human-readable form as the label when none exists.
     */
    private void normaliseFieldNames(List<Map<String, Object>> fields, String entityName) {        for (Map<String, Object> field : fields) {
            Object rawName = field.get(F_NAME);
            if (rawName == null) continue;
            String originalName = String.valueOf(rawName);
            String normalised = toSnakeCase(originalName);
            if (!normalised.equals(originalName)) {
                log.warn("[SchemaEnricher] Entity '{}': field name '{}' \u2192 '{}' (normalised to snake_case)",
                        entityName, originalName, normalised);
                field.put(F_NAME, normalised);
                Object lbl = field.get(F_LABEL);
                if (lbl == null || String.valueOf(lbl).isBlank()) {
                    field.put(F_LABEL, originalName);
                }
            }
            if (field.containsKey(F_ID)) {
                field.put(F_ID, normalised);
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

    /**
     * Converts any human-friendly identifier to canonical snake_case.
     * Rules (applied in order):
     *   1. Split acronym\u2192Word boundaries: "HTTPResponse" \u2192 "HTTP_Response"
     *   2. Split camelCase boundaries: "firstName" \u2192 "first_Name"
     *   3. Replace whitespace / hyphens with underscores: "Full Name" \u2192 "Full_Name"
     *   4. Strip characters that are not letters, digits, or underscores
     *   5. Collapse runs of underscores
     *   6. Trim leading/trailing underscores
     *   7. Lower-case the result
     * If normalisation strips everything, the trimmed original is returned so
     * we never silently produce an empty identifier.
     */
    static String toSnakeCase(String input) {
        if (input == null) return null;
        String trimmed = input.trim();
        if (trimmed.isEmpty()) return trimmed;
        String out = trimmed
                .replaceAll("(?<=[A-Z])(?=[A-Z][a-z])", "_")
                .replaceAll("(?<=[a-z0-9])(?=[A-Z])",   "_")
                .replaceAll("[\\s\\-]+", "_")
                .replaceAll("\\W", "")
                .replaceAll("_+", "_")
                .toLowerCase();
        while (out.startsWith("_")) out = out.substring(1);
        while (out.endsWith("_")) out = out.substring(0, out.length() - 1);
        return out.isEmpty() ? trimmed : out;
    }
}
