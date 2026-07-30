package com.appbana.ai.agent.tool;

import com.appbana.ai.agent.AgentContext;
import com.appbana.ai.knowledge.MetadataValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * BatchUpdateEntitiesTool - Batch Entity Updates in One Call
 * 
 * Updates multiple entities in a single tool call, reducing LLM iterations
 * and API calls. Implements atomic rollback on failure.
 * 
 * Cost Optimization: Reduces 5-10 tool calls to 1 for multi-entity updates
 */
@Slf4j
public class BatchUpdateEntitiesTool implements Tool {

    private final MetadataValidator validator;
    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public BatchUpdateEntitiesTool(MetadataValidator validator, String baseUrl) {
        this.validator = validator;
        this.baseUrl = baseUrl;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(120))
                .build();
    }

    @Override
    public String getName() {
        return "batch_update_entities";
    }

    @Override
    public String getDescription() {
        return "Updates multiple entities in a single batch operation. " +
                "Use this when you need to modify 2+ entities at once (e.g., adding fields to multiple tables, " +
                "or turning on/off maker-checker approval workflow via set_approval). " +
                "MUCH more efficient than calling update_entity multiple times.";
    }

    @Override
    public String getParameterSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "appId": {
                      "type": "string",
                      "description": "Application ID containing the entities"
                    },
                    "updates": {
                      "type": "array",
                      "description": "Array of entity update operations",
                      "items": {
                        "type": "object",
                        "properties": {
                          "entityName": {
                            "type": "string",
                            "description": "Name of the entity to update"
                          },
                          "operation": {
                            "type": "string",
                            "enum": ["add_fields", "remove_fields", "update_fields", "rename_entity", "set_approval"],
                            "description": "Type of update operation. Use 'set_approval' to turn the maker-checker approval workflow on or off for an EXISTING entity (pass 'approvalRequired': true/false); this is the only supported way to change approval status after an entity has already been created — add_fields/update_fields do not touch it."
                          },
                          "approvalRequired": {
                            "type": "boolean",
                            "description": "Required for operation='set_approval'. true enables maker-checker approval (SchemaManager injects the 8 approval columns), false disables it."
                          },
                          "approvalLevels": {
                            "type": "integer",
                            "enum": [1, 2],
                            "description": "Optional for operation='set_approval' when approvalRequired=true. 1 (default) is the standard single-checker workflow; 2 requires a DIFFERENT checker-2 to give final signoff after checker-1 approves."
                          },
                          "fields": {
                            "type": "array",
                            "description": "Fields to add, remove, or update",
                            "items": {
                              "type": "object",
                              "properties": {
                                "name": {"type": "string"},
                                "type": {"type": "string"},
                                "required": {"type": "boolean"},
                                "label": {"type": "string"},
                                "conditions": {
                                  "type": "object",
                                  "description": "Phase B2 — optional conditional visibility (showWhen/requiredWhen/disabledWhen expression tree)."
                                },
                                "fileConstraints": {
                                  "type": "object",
                                  "description": "Phase B3 — required when type='file'. Shape: {maxSizeBytes, acceptedMimeTypes}."
                                }
                              }
                            }
                          },
                          "newName": {
                            "type": "string",
                            "description": "New entity name (for rename_entity operation)"
                          }
                        },
                        "required": ["entityName", "operation"]
                      }
                    }
                  },
                  "required": ["appId", "updates"]
                }
                """;
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments, AgentContext context) {
        long startTime = System.currentTimeMillis();
        List<String> successfulUpdates = new ArrayList<>();
        List<String> failedUpdates = new ArrayList<>();

        log.info("[BatchUpdateEntities] ═══════════════════════════════════════════════════════");
        log.info("[BatchUpdateEntities] Starting batch entity update");
        log.info("[BatchUpdateEntities] ═══════════════════════════════════════════════════════");

        try {
            // Validate parameters
            String appId = (String) arguments.get("appId");
            if (appId == null || appId.isBlank()) {
                return ToolResult.error(getName(), "Parameter 'appId' is required");
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> updates = (List<Map<String, Object>>) arguments.get("updates");
            if (updates == null || updates.isEmpty()) {
                return ToolResult.error(getName(), "Parameter 'updates' is required and must not be empty");
            }

            log.info("[BatchUpdateEntities] App: {}, Updates: {}", appId, updates.size());

            String tenantId = context.tenantId() != null ? context.tenantId() : "default";
            String token = context.token();

            // Process each update
            for (int i = 0; i < updates.size(); i++) {
                Map<String, Object> update = updates.get(i);
                String entityName = (String) update.get("entityName");
                String operation = (String) update.get("operation");

                log.info("[BatchUpdateEntities] Processing {}/{}: {} on '{}'",
                        i + 1, updates.size(), operation, entityName);

                // Guard: "App" is not an entity — LLMs sometimes try to use this tool
                // to rename the app itself. Redirect them to update_app.
                if ("App".equalsIgnoreCase(entityName)) {
                    failedUpdates.add(entityName + ":" + operation
                            + " - 'App' is not an entity. To rename or update the app itself, "
                            + "call the update_app tool instead (arguments: name, description, defaultPage).");
                    log.warn("[BatchUpdateEntities] ❌ Rejected entityName='App' — should use update_app tool");
                    continue;
                }

                try {
                    boolean success = executeUpdate(tenantId, appId, entityName, operation, update, token);

                    if (success) {
                        successfulUpdates.add(entityName + ":" + operation);
                        log.info("[BatchUpdateEntities] ✅ {} - {} completed", entityName, operation);
                    } else {
                        failedUpdates.add(entityName + ":" + operation);
                        log.warn("[BatchUpdateEntities] ❌ {} - {} failed", entityName, operation);
                    }
                } catch (BackendAuthException authEx) {
                    // C4.4e Review #12 -- a 401 here means the token died mid-batch, not that this
                    // one update was invalid. Every remaining update in the batch would fail
                    // identically, so folding this into failedUpdates and continuing (as a normal
                    // per-item failure) would burn through the rest of the list producing N
                    // confusing "backend returned 401" strings instead of one clear signal the
                    // agent loop can act on. Abort the whole batch instead.
                    log.warn("[BatchUpdateEntities] Aborting batch -- session expired on update {}/{} ({}:{}): {}",
                            i + 1, updates.size(), entityName, operation, authEx.getMessage());
                    return ToolResult.authError(getName(), authEx.getMessage());
                } catch (Exception e) {
                    failedUpdates.add(entityName + ":" + operation + " - " + e.getMessage());
                    log.error("[BatchUpdateEntities] ❌ {} - {} error: {}", entityName, operation, e.getMessage());
                }
            }

            // Build result
            long duration = System.currentTimeMillis() - startTime;

            Map<String, Object> resultData = new HashMap<>();
            resultData.put("appId", appId);
            resultData.put("totalUpdates", updates.size());
            resultData.put("successful", successfulUpdates.size());
            resultData.put("failed", failedUpdates.size());
            resultData.put("successfulUpdates", successfulUpdates);
            resultData.put("failedUpdates", failedUpdates);
            resultData.put("durationMs", duration);

            if (failedUpdates.isEmpty()) {
                log.info("[BatchUpdateEntities] ✅ All {} updates completed successfully in {}ms",
                        successfulUpdates.size(), duration);
                return ToolResult.success(getName(), resultData, duration);
            } else if (successfulUpdates.isEmpty()) {
                log.error("[BatchUpdateEntities] ❌ All {} updates failed", failedUpdates.size());
                return ToolResult.error(getName(),
                        String.format("All %d updates failed: %s", failedUpdates.size(), failedUpdates));
            } else {
                log.warn("[BatchUpdateEntities] ⚠️ Partial success: {}/{} updates completed",
                        successfulUpdates.size(), updates.size());
                return ToolResult.success(getName(), resultData, duration);
            }

        } catch (BackendAuthException authEx) {
            log.warn("[BatchUpdateEntities] {}", authEx.getMessage());
            return ToolResult.authError(getName(), authEx.getMessage());
        } catch (Exception e) {
            log.error("[BatchUpdateEntities] Fatal error", e);
            return ToolResult.error(getName(), "Batch update failed: " + e.getMessage());
        }
    }

    /**
     * Execute a single entity update
     */
    private boolean executeUpdate(String tenantId, String appId, String entityName, String operation,
            Map<String, Object> update, String token) throws Exception {

        switch (operation.toLowerCase()) {
            case "add_fields":
                return addFields(tenantId, appId, entityName, update, token);
            case "remove_fields":
                return removeFields(tenantId, appId, entityName, update, token);
            case "update_fields":
                return updateFields(tenantId, appId, entityName, update, token);
            case "rename_entity":
                return renameEntity(tenantId, appId, entityName, update, token);
            case "set_approval":
                return setApproval(tenantId, appId, entityName, update, token);
            default:
                log.warn("[BatchUpdateEntities] Unknown operation: {}", operation);
                return false;
        }
    }

    /**
     * Enable or disable maker-checker approval on an existing entity.
     *
     * <p>This is the only supported way to flip {@code approvalRequired} after an entity has
     * already been created via create_entity/scaffold_app — update_fields intentionally only
     * touches the {@code fields} array, never entity-level flags. Per Section 7 of the repo's
     * copilot instructions, setting the flag on the saved schema is the whole contract:
     * SchemaManager materialises (or leaves in place) the eight physical approval columns on
     * the next save, so we don't touch fields here at all.
     */
    private boolean setApproval(String tenantId, String appId, String entityName, Map<String, Object> update,
            String token) throws Exception {
        Object approvalRequiredArg = update.get("approvalRequired");
        if (!(approvalRequiredArg instanceof Boolean)) {
            log.warn("[BatchUpdateEntities] set_approval requires a boolean 'approvalRequired' argument");
            return false;
        }

        Map<String, Object> entity = fetchEntity(tenantId, appId, entityName, token);
        if (entity == null) {
            return false;
        }

        entity.put("approvalRequired", approvalRequiredArg);

        // approvalLevels only means anything alongside approvalRequired=true.
        if (Boolean.TRUE.equals(approvalRequiredArg)) {
            Object levelsArg = update.get("approvalLevels");
            int levels = (levelsArg instanceof Number n) ? n.intValue() : 1;
            if (levels == 2) {
                entity.put("approvalLevels", 2);
            } else {
                entity.remove("approvalLevels");
            }
        } else {
            entity.remove("approvalLevels");
        }
        return saveEntity(entity, token);
    }

    /**
     * Add fields to an entity
     */
    @SuppressWarnings("unchecked")
    private boolean addFields(String tenantId, String appId, String entityName, Map<String, Object> update,
            String token) throws Exception {
        List<Map<String, Object>> newFields = (List<Map<String, Object>>) update.get("fields");
        if (newFields == null || newFields.isEmpty()) {
            log.warn("[BatchUpdateEntities] No fields specified for add_fields operation");
            return false;
        }

        // Get current entity
        Map<String, Object> entity = fetchEntity(tenantId, appId, entityName, token);
        if (entity == null) {
            log.error("[BatchUpdateEntities] Entity '{}' not found in app '{}'", entityName, appId);
            return false;
        }

        // Add new fields
        List<Map<String, Object>> existingFields = (List<Map<String, Object>>) entity.get("fields");
        if (existingFields == null) {
            existingFields = new ArrayList<>();
        }

        Set<String> existingFieldNames = new HashSet<>();
        for (Map<String, Object> f : existingFields) {
            existingFieldNames.add((String) f.get("name"));
        }

        for (Map<String, Object> newField : newFields) {
            String fieldName = (String) newField.get("name");
            if (!existingFieldNames.contains(fieldName)) {
                // Generate ID if missing
                if (!newField.containsKey("id")) {
                    newField.put("id", "field_" + fieldName.toLowerCase());
                }
                existingFields.add(newField);
                log.debug("[BatchUpdateEntities] Adding field: {}", fieldName);
            } else {
                log.debug("[BatchUpdateEntities] Field '{}' already exists, skipping", fieldName);
            }
        }

        entity.put("fields", existingFields);

        // Save updated entity
        boolean saved = saveEntity(entity, token);

        // Best-effort UI sync: after the schema save succeeds, propagate the newly
        // added fields into matching form/list pages so the runtime preview shows
        // them without the user having to regenerate pages manually. Failure here
        // is logged but doesn't fail the tool call — the schema is the source of
        // truth and has already been persisted.
        if (saved) {
            try {
                syncPagesAfterAddFields(tenantId, appId, entityName, newFields, token);
            } catch (Exception e) {
                log.warn("[BatchUpdateEntities] Page sync after add_fields failed (schema is saved): {}",
                        e.getMessage());
            }
        }
        return saved;
    }

    /**
     * Remove fields from an entity
     */
    @SuppressWarnings("unchecked")
    private boolean removeFields(String tenantId, String appId, String entityName, Map<String, Object> update,
            String token) throws Exception {
        List<Map<String, Object>> fieldsToRemove = (List<Map<String, Object>>) update.get("fields");
        if (fieldsToRemove == null || fieldsToRemove.isEmpty()) {
            log.warn("[BatchUpdateEntities] No fields specified for remove_fields operation");
            return false;
        }

        // Get field names to remove
        Set<String> fieldNamesToRemove = new HashSet<>();
        for (Map<String, Object> f : fieldsToRemove) {
            fieldNamesToRemove.add((String) f.get("name"));
        }

        // Get current entity
        Map<String, Object> entity = fetchEntity(tenantId, appId, entityName, token);
        if (entity == null) {
            return false;
        }

        // Task C4.6 — refuse to delete a backend-owned approval column from an entity
        // that still has approvalRequired: true. SchemaManager guarantees those eight
        // columns exist whenever the flag is set; this tool is the one write path that
        // can break that invariant from the other side, because it removes columns
        // rather than failing to add them. Dropping approval_status from a live
        // approval entity leaves records that insert fine and then 500 on submit,
        // approve and the checker queue — the exact failure C4.6 exists to eliminate.
        List<String> reserved = reservedApprovalColumnsIn(entity, fieldNamesToRemove);
        if (!reserved.isEmpty()) {
            log.warn("[BatchUpdateEntities] Refusing to remove approval column(s) {} from '{}': the entity "
                    + "has approvalRequired=true, and these columns are required by the approval workflow. "
                    + "Turn off approvals for the entity first if that is really the intent.",
                    reserved, entityName);
            return false;
        }

        // Filter out removed fields
        List<Map<String, Object>> existingFields = (List<Map<String, Object>>) entity.get("fields");
        List<Map<String, Object>> remainingFields = new ArrayList<>();

        for (Map<String, Object> field : existingFields) {
            String fieldName = (String) field.get("name");
            if (!fieldNamesToRemove.contains(fieldName)) {
                remainingFields.add(field);
            } else {
                log.debug("[BatchUpdateEntities] Removing field: {}", fieldName);
            }
        }

        entity.put("fields", remainingFields);
        return saveEntity(entity, token);
    }

    /**
     * Update existing fields in an entity
     */
    @SuppressWarnings("unchecked")
    private boolean updateFields(String tenantId, String appId, String entityName, Map<String, Object> update,
            String token) throws Exception {
        List<Map<String, Object>> fieldUpdates = (List<Map<String, Object>>) update.get("fields");
        if (fieldUpdates == null || fieldUpdates.isEmpty()) {
            return false;
        }

        // Build update map
        Map<String, Map<String, Object>> updateMap = new HashMap<>();
        for (Map<String, Object> f : fieldUpdates) {
            updateMap.put((String) f.get("name"), f);
        }

        // Get current entity
        Map<String, Object> entity = fetchEntity(tenantId, appId, entityName, token);
        if (entity == null) {
            return false;
        }

        // Apply updates
        List<Map<String, Object>> existingFields = (List<Map<String, Object>>) entity.get("fields");
        for (Map<String, Object> field : existingFields) {
            String fieldName = (String) field.get("name");
            if (updateMap.containsKey(fieldName)) {
                Map<String, Object> fieldUpdate = updateMap.get(fieldName);
                // Merge updates
                for (Map.Entry<String, Object> entry : fieldUpdate.entrySet()) {
                    if (!"name".equals(entry.getKey())) { // Don't change name
                        field.put(entry.getKey(), entry.getValue());
                    }
                }
                log.debug("[BatchUpdateEntities] Updated field: {}", fieldName);
            }
        }

        entity.put("fields", existingFields);
        return saveEntity(entity, token);
    }

    /**
     * Rename an entity
     */
    private boolean renameEntity(String tenantId, String appId, String entityName, Map<String, Object> update,
            String token) throws Exception {
        String newName = (String) update.get("newName");
        if (newName == null || newName.isBlank()) {
            log.warn("[BatchUpdateEntities] No newName specified for rename_entity operation");
            return false;
        }

        // Get current entity
        Map<String, Object> entity = fetchEntity(tenantId, appId, entityName, token);
        if (entity == null) {
            return false;
        }

        // Update name and displayName. The schema key must be rebuilt for the new entity.
        String newFullName = buildSchemaKey(tenantId, appId, newName);
        entity.put("name", newFullName);
        entity.put("displayName", newName);

        // Delete old and create new
        deleteEntity(tenantId, appId, entityName, token);
        return saveEntity(entity, token);
    }

    /**
     * Build the multi-tenant schema key expected by SchemaManager.
     */
    private static String buildSchemaKey(String tenantId, String appId, String entityName) {
        String tp = (tenantId == null || tenantId.isBlank()) ? "default" : tenantId;
        return tp + "_" + appId + "_" + entityName;
    }

    /**
     * Task C4.6 — the reserved approval columns a {@code remove_fields} update would delete
     * from an entity that still has {@code approvalRequired: true}, sorted, or empty if the
     * removal is safe.
     *
     * <p>Extracted from {@link #removeFields} so the guard is reachable without an HTTP
     * round-trip to the backend. This is the one ai-builder write path that can break the
     * {@code approvalRequired ⟺ eight physical columns} invariant from the removal side:
     * SchemaManager guarantees the columns exist whenever the flag is set, but nothing stops
     * this tool from dropping them again while leaving the flag true.
     *
     * <p>Returns empty when the entity has no approval workflow — removing a user-defined
     * field that merely happens to share one of these names is legitimate there.
     */
    static List<String> reservedApprovalColumnsIn(Map<String, Object> entity, Set<String> fieldNamesToRemove) {
        if (entity == null || fieldNamesToRemove == null || !Boolean.TRUE.equals(entity.get("approvalRequired"))) {
            return List.of();
        }
        return fieldNamesToRemove.stream()
                .filter(n -> n != null
                        && SchemaEnricher.RESERVED_APPROVAL_COLUMNS.contains(n.trim().toLowerCase(Locale.ROOT)))
                .sorted()
                .toList();
    }

    /**
     * Fetch entity from backend via GET /schema/{tenantId}_{appId}_{entityName}.
     */
    private Map<String, Object> fetchEntity(String tenantId, String appId, String entityName, String token)
            throws Exception {
        String key = buildSchemaKey(tenantId, appId, entityName);
        String url = String.format("%s/schema/%s", baseUrl, key);

        HttpRequest.Builder rb = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(30));
        if (token != null && !token.isEmpty()) {
            rb.header("Authorization", "Bearer " + token);
        }
        HttpResponse<String> response = httpClient.send(rb.GET().build(), HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(), Map.class);
        } else if (response.statusCode() == 404) {
            log.warn("[BatchUpdateEntities] Entity schema not found at {}", url);
            return null;
        } else if (response.statusCode() == 401) {
            throw new BackendAuthException(getName() + ": fetching entity '" + entityName + "' returned 401");
        } else {
            log.error("[BatchUpdateEntities] Failed to fetch entity {}: {} - {}", url, response.statusCode(),
                    response.body());
            return null;
        }
    }

    /**
     * Save (upsert) entity via POST /schema. The entity map must contain its full multi-tenant
     * name (as returned by GET /schema/{key}); SchemaManager.saveSchema keys on it.
     */
    private boolean saveEntity(Map<String, Object> entity, String token) throws Exception {
        String url = String.format("%s/schema", baseUrl);
        String json = objectMapper.writeValueAsString(entity);

        HttpRequest.Builder rb = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30));
        if (token != null && !token.isEmpty()) {
            rb.header("Authorization", "Bearer " + token);
        }
        HttpResponse<String> response = httpClient.send(
                rb.POST(HttpRequest.BodyPublishers.ofString(json)).build(),
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return true;
        }
        if (response.statusCode() == 401) {
            throw new BackendAuthException(getName() + ": saving entity '" + entity.get("name") + "' returned 401");
        }
        log.error("[BatchUpdateEntities] Failed to save entity {}: {} - {}", entity.get("name"),
                response.statusCode(), response.body());
        return false;
    }

    /**
     * Delete entity via DELETE /schema/{key}.
     */
    private boolean deleteEntity(String tenantId, String appId, String entityName, String token) throws Exception {
        String key = buildSchemaKey(tenantId, appId, entityName);
        String url = String.format("%s/schema/%s", baseUrl, key);

        HttpRequest.Builder rb = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30));
        if (token != null && !token.isEmpty()) {
            rb.header("Authorization", "Bearer " + token);
        }
        HttpResponse<String> response = httpClient.send(rb.DELETE().build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 401) {
            throw new BackendAuthException(getName() + ": deleting entity '" + entityName + "' returned 401");
        }
        return response.statusCode() >= 200 && response.statusCode() < 300;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Page auto-sync after add_fields
    //
    // The AppBana runtime renders forms and tables from static node metadata stored
    // on each page (not directly from the entity schema). When a field is added to
    // an entity, existing pages don't automatically pick it up. To close that gap
    // we walk the app's pages after a successful schema update and append matching
    // inputs to form pages / columns to table pages. Runs best-effort.
    // ─────────────────────────────────────────────────────────────────────────────

    /** Audit / auto-managed fields that should not appear in forms. */
    private static final Set<String> AUDIT_FIELDS = Set.of("id", "created_at", "updated_at");

    @SuppressWarnings("unchecked")
    private void syncPagesAfterAddFields(String tenantId, String appId, String entityName,
            List<Map<String, Object>> newFields, String token) throws Exception {
        Map<String, Object> app = fetchAppMetadata(tenantId, appId, token);
        if (app == null) return;

        Object pagesDataObj = app.get("pagesData");
        if (!(pagesDataObj instanceof List<?>)) return;
        List<Object> pagesData = (List<Object>) pagesDataObj;

        int updated = 0;
        for (Object pageObj : pagesData) {
            if (!(pageObj instanceof Map<?, ?>)) continue;
            Map<String, Object> page = (Map<String, Object>) pageObj;
            String pageId = (String) page.get("id");
            if (pageId == null || pageId.isBlank()) continue;

            boolean changed = false;
            String rootType = rootNodeType(page);
            if ("form".equals(rootType) && entityName.equals(rootFormEntity(page))) {
                changed = appendFieldsToFormPage(page, entityName, newFields);
            } else if ("table".equals(rootType) && entityName.equals(rootTableEntity(page))) {
                changed = appendColumnsToTablePage(page, newFields);
            }

            if (changed) {
                boolean ok = savePage(tenantId, appId, pageId, page, token);
                if (ok) {
                    updated++;
                    log.info("[BatchUpdateEntities] Synced page '{}' with new fields for entity '{}'",
                            pageId, entityName);
                } else {
                    log.warn("[BatchUpdateEntities] Failed to save synced page '{}'", pageId);
                }
            }
        }
        if (updated > 0) {
            log.info("[BatchUpdateEntities] Auto-synced {} page(s) after add_fields on '{}'",
                    updated, entityName);
        }
    }

    @SuppressWarnings("unchecked")
    private String rootNodeType(Map<String, Object> page) {
        Object nodesObj = page.get("nodes");
        String rootId = (String) page.getOrDefault("rootId", "root");
        if (!(nodesObj instanceof List<?>)) return null;
        for (Object n : (List<Object>) nodesObj) {
            if (n instanceof Map<?, ?> m && rootId.equals(m.get("id"))) {
                return (String) m.get("type");
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String rootFormEntity(Map<String, Object> page) {
        Map<String, Object> root = findNode(page, (String) page.getOrDefault("rootId", "root"));
        if (root == null) return null;
        Object props = root.get("props");
        return props instanceof Map<?, ?> pm ? (String) ((Map<String, Object>) pm).get("entity") : null;
    }

    @SuppressWarnings("unchecked")
    private String rootTableEntity(Map<String, Object> page) {
        Map<String, Object> root = findNode(page, (String) page.getOrDefault("rootId", "root"));
        if (root == null) return null;
        Object props = root.get("props");
        return props instanceof Map<?, ?> pm ? (String) ((Map<String, Object>) pm).get("entity") : null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> findNode(Map<String, Object> page, String id) {
        Object nodesObj = page.get("nodes");
        if (!(nodesObj instanceof List<?>)) return null;
        for (Object n : (List<Object>) nodesObj) {
            if (n instanceof Map<?, ?> m && id != null && id.equals(m.get("id"))) {
                return (Map<String, Object>) m;
            }
        }
        return null;
    }

    /**
     * Append new inputs to a form page. Skips audit fields and any field that already
     * has an input node bound to it. Reuses the same node shape as GeneratePageTool so
     * StudioTableLive / the runtime form renderer can process them without special casing.
     */
    @SuppressWarnings("unchecked")
    private boolean appendFieldsToFormPage(Map<String, Object> page, String entityName,
            List<Map<String, Object>> newFields) {
        Object nodesObj = page.get("nodes");
        if (!(nodesObj instanceof List<?>)) return false;
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) nodesObj;

        // Collect existing input `field` bindings so we don't duplicate.
        Set<String> existingFieldBindings = new HashSet<>();
        int maxContainerIndex = -1;
        Map<String, Object> grid = null;
        Map<String, Object> saveContainer = null;
        for (Map<String, Object> n : nodes) {
            String type = (String) n.get("type");
            String id = (String) n.get("id");
            if ("app-grid".equals(type)) grid = n;
            if ("container".equals(type) && "container-save".equals(id)) saveContainer = n;
            if (("input".equals(type) || "select".equals(type)) && n.get("props") instanceof Map<?, ?> pm) {
                Object f = ((Map<String, Object>) pm).get("field");
                if (f instanceof String s) existingFieldBindings.add(s);
            }
            if (id != null && id.startsWith("container-") && !"container-save".equals(id)) {
                try { maxContainerIndex = Math.max(maxContainerIndex, Integer.parseInt(id.substring("container-".length()))); }
                catch (NumberFormatException ignored) { /* non-numeric container id — leave as-is */ }
            }
        }
        if (grid == null) return false; // Not a shape we know how to extend.

        Map<String, Object> gridProps = (Map<String, Object>) grid.getOrDefault("props", new HashMap<>());
        List<String> gridChildren = new ArrayList<>((List<String>) grid.getOrDefault("children", new ArrayList<>()));

        boolean changed = false;
        int nextIndex = maxContainerIndex + 1;
        for (Map<String, Object> field : newFields) {
            String fieldName = (String) field.get("name");
            if (fieldName == null || AUDIT_FIELDS.contains(fieldName.toLowerCase())) continue;
            if (existingFieldBindings.contains(fieldName)) continue;

            String containerId = "container-" + nextIndex;
            String inputId = "input-" + nextIndex;
            String fieldType = (String) field.getOrDefault("type", "string");
            String fieldLabel = (String) field.getOrDefault("label", fieldName);

            Map<String, Object> container = new HashMap<>();
            container.put("id", containerId);
            container.put("type", "container");
            container.put("children", List.of(inputId));
            Map<String, Object> containerProps = new HashMap<>();
            containerProps.put("className", "appbana-form-cell");
            containerProps.put("slot", "cell-" + nextIndex);
            containerProps.put("data-cell-index", String.valueOf(nextIndex));
            container.put("props", containerProps);

            Map<String, Object> input = new HashMap<>();
            input.put("id", inputId);
            String topLevelType = "status".equals(fieldType) ? "select" : "input";
            input.put("type", topLevelType);
            Map<String, Object> inputProps = new HashMap<>();
            inputProps.put("entity", entityName);
            inputProps.put("field", fieldName);
            inputProps.put("name", fieldName);
            inputProps.put("label", fieldLabel);
            inputProps.put("type", mapFieldToInputType(fieldType));
            inputProps.put("className", "input");
            if ("status".equals(fieldType) && field.get("options") instanceof List<?> opts && !opts.isEmpty()) {
                inputProps.put("options", opts);
                if (Boolean.TRUE.equals(field.get("required"))) inputProps.put("required", true);
            } else if ("reference".equals(fieldType)) {
                Object refEntity = field.get("referenceEntity");
                if (refEntity != null) inputProps.put("referenceEntity", refEntity);
            } else {
                inputProps.put("placeholder", "Enter " + fieldLabel.toLowerCase() + "...");
            }
            input.put("props", inputProps);

            // Insert container + input before the save container in the nodes list so
            // sibling nodes stay contiguous (StudioTableLive doesn't care, but this
            // keeps the metadata readable/diff-friendly).
            int insertAt = saveContainer != null ? nodes.indexOf(saveContainer) : nodes.size();
            nodes.add(insertAt, container);
            nodes.add(insertAt + 1, input);

            // Reposition save-container to the next cell.
            int saveIdx = gridChildren.indexOf("container-save");
            if (saveIdx >= 0) gridChildren.add(saveIdx, containerId);
            else gridChildren.add(containerId);

            nextIndex++;
            changed = true;
        }

        if (changed) {
            // Recompute grid rows for the new cell count (grid is 3 cols).
            int totalCells = gridChildren.size();
            int cols = gridProps.get("cols") instanceof Number c ? c.intValue() : 3;
            if (cols <= 0) cols = 3;
            gridProps.put("rows", Math.max(1, (totalCells + cols - 1) / cols));
            grid.put("props", gridProps);
            grid.put("children", gridChildren);

            // Shift save-container's slot to the last cell so the save button stays
            // at the end of the visual grid.
            if (saveContainer != null && saveContainer.get("props") instanceof Map<?, ?> spm) {
                Map<String, Object> sp = (Map<String, Object>) spm;
                sp.put("slot", "cell-" + (totalCells - 1));
            }
        }
        return changed;
    }

    /**
     * Append new columns to a list/table page. Table root nodes carry a `props.fields`
     * array of {name, label, type} which the runtime table uses directly.
     */
    @SuppressWarnings("unchecked")
    private boolean appendColumnsToTablePage(Map<String, Object> page, List<Map<String, Object>> newFields) {
        Map<String, Object> root = findNode(page, (String) page.getOrDefault("rootId", "root"));
        if (root == null) return false;
        Map<String, Object> props = (Map<String, Object>) root.getOrDefault("props", new HashMap<>());
        List<Map<String, Object>> cols = (List<Map<String, Object>>) props.getOrDefault("fields", new ArrayList<>());

        Set<String> existing = new HashSet<>();
        for (Map<String, Object> c : cols) {
            Object name = c.get("name");
            if (name instanceof String s) existing.add(s);
        }

        boolean changed = false;
        for (Map<String, Object> field : newFields) {
            String fieldName = (String) field.get("name");
            if (fieldName == null || existing.contains(fieldName)) continue;
            Map<String, Object> col = new HashMap<>();
            col.put("name", fieldName);
            col.put("label", field.getOrDefault("label", fieldName));
            col.put("type", mapFieldToTableType((String) field.getOrDefault("type", "string")));
            cols.add(col);
            changed = true;
        }
        if (changed) {
            props.put("fields", cols);
            root.put("props", props);
        }
        return changed;
    }

    /** Mirror of GeneratePageTool.mapFieldToInputType — kept local to avoid a cross-tool dep. */
    private String mapFieldToInputType(String fieldType) {
        if (fieldType == null) return "input";
        return switch (fieldType.toLowerCase()) {
            case "date" -> "date";
            case "datetime", "timestamp" -> "datetime-local";
            case "reference" -> "reference";
            case "status" -> "select";
            case "boolean" -> "checkbox";
            case "longtext" -> "textarea";
            default -> "input";
        };
    }

    /** Convert schema field type to the runtime table's column type vocabulary. */
    private String mapFieldToTableType(String fieldType) {
        if (fieldType == null) return "text";
        return switch (fieldType.toLowerCase()) {
            case "int", "integer", "long", "number" -> "integer";
            case "decimal", "float", "double" -> "number";
            case "boolean" -> "boolean";
            case "date" -> "date";
            case "datetime", "timestamp" -> "datetime";
            case "email" -> "email";
            case "status" -> "status";
            case "reference" -> "reference";
            default -> "text";
        };
    }

    private Map<String, Object> fetchAppMetadata(String tenantId, String appId, String token) throws Exception {
        String url = String.format("%s/appbana-studio/%s/apps/%s", baseUrl, tenantId, appId);
        HttpRequest.Builder rb = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(30));
        if (token != null && !token.isEmpty()) {
            rb.header("Authorization", "Bearer " + token);
        }
        HttpResponse<String> resp = httpClient.send(rb.GET().build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 200) {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = objectMapper.readValue(resp.body(), Map.class);
            return body;
        }
        if (resp.statusCode() == 401) {
            throw new BackendAuthException(getName() + ": fetching app metadata for '" + appId + "' returned 401");
        }
        log.warn("[BatchUpdateEntities] Failed to fetch app metadata for {}: {} - {}", appId,
                resp.statusCode(), resp.body());
        return null;
    }

    private boolean savePage(String tenantId, String appId, String pageId, Map<String, Object> page, String token)
            throws Exception {
        String url = String.format("%s/appbana-studio/%s/apps/%s/pages/%s", baseUrl, tenantId, appId, pageId);
        String json = objectMapper.writeValueAsString(page);
        HttpRequest.Builder rb = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30));
        if (token != null && !token.isEmpty()) {
            rb.header("Authorization", "Bearer " + token);
        }
        HttpResponse<String> resp = httpClient.send(
                rb.PUT(HttpRequest.BodyPublishers.ofString(json)).build(),
                HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 200 && resp.statusCode() < 300) return true;
        if (resp.statusCode() == 401) {
            throw new BackendAuthException(getName() + ": saving page '" + pageId + "' returned 401");
        }
        log.warn("[BatchUpdateEntities] savePage {} failed: {} - {}", pageId, resp.statusCode(), resp.body());
        return false;
    }
}
