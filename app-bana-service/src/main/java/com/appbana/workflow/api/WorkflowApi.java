package com.appbana.workflow.api;

import com.appbana.JdbcManager;
import com.appbana.workflow.WorkflowEngine;
import com.appbana.workflow.model.WorkflowDefinition;
import com.appbana.workflow.model.WorkflowInstance;
import com.appbana.workflow.model.WorkflowToken;
import com.appbana.workflow.model.dto.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.BiConsumer;

/**
 * Workflow REST API
 * Phase 1: Definition CRUD + Runtime operations + My Tasks
 */
public class WorkflowApi {
    private static final Logger LOG = LoggerFactory.getLogger(WorkflowApi.class);
    private static WorkflowEngine engine;
    
    private static ObjectMapper getMapper() {
        ObjectMapper m = new ObjectMapper();
        m.findAndRegisterModules();
        LOG.debug("ObjectMapper created with {} modules", m.getRegisteredModuleIds().size());
        return m;
    }
    
    public static void initialize() {
        try {
            engine = new WorkflowEngine(JdbcManager.getDataSource());
            LOG.info("WorkflowEngine initialized");
        } catch (Exception e) {
            LOG.error("Failed to initialize WorkflowEngine", e);
            throw new RuntimeException("WorkflowEngine initialization failed", e);
        }
    }
    
    public static WorkflowEngine getEngine() {
        if (engine == null) {
            initialize();
        }
        return engine;
    }
    
    /**
     * POST /api/workflows - Create or update workflow definition
     */
    public static BiConsumer<com.appbana.api.Router.HttpRequest, com.appbana.api.Router.HttpResponse> createOrUpdateWorkflow() {
        return (req, res) -> {
            try {
                Map<String, Object> payload = req.readJson(new TypeReference<>() {});
                
                String id = (String) payload.get("id");
                String appId = (String) payload.get("appId");
                String name = (String) payload.get("name");
                String description = (String) payload.get("description");
                String triggerEntity = (String) payload.get("triggerEntity");
                String triggerEvent = (String) payload.get("triggerEvent");
                String triggerCondition = (String) payload.get("triggerCondition");
                String status = (String) payload.getOrDefault("status", "DRAFT");
                String definitionJson = getMapper().writeValueAsString(payload.get("definition"));
                
                if (id == null || id.isBlank()) {
                    id = UUID.randomUUID().toString();
                }
                
                try (Connection conn = JdbcManager.getConnection()) {
                    // Check if exists
                    String checkSql = "SELECT id, version FROM appbana_wf_definition WHERE id = ?";
                    Integer currentVersion = null;
                    
                    try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                        ps.setString(1, id);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                currentVersion = rs.getInt("version");
                            }
                        }
                    }
                    
                    if (currentVersion != null) {
                        // Update existing (increment version if publishing)
                        int newVersion = "ACTIVE".equals(status) ? currentVersion + 1 : currentVersion;
                        
                        String updateSql = """
                            UPDATE appbana_wf_definition 
                            SET app_id = ?, name = ?, description = ?, trigger_entity = ?, 
                                trigger_event = ?, trigger_condition = ?, version = ?, 
                                status = ?, definition_json = ?, updated_at = ?, updated_by = ?
                            WHERE id = ?
                            """;
                        
                        try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                            ps.setString(1, appId);
                            ps.setString(2, name);
                            ps.setString(3, description);
                            ps.setString(4, triggerEntity);
                            ps.setString(5, triggerEvent);
                            ps.setString(6, triggerCondition);
                            ps.setInt(7, newVersion);
                            ps.setString(8, status);
                            ps.setString(9, definitionJson);
                            ps.setTimestamp(10, Timestamp.valueOf(LocalDateTime.now()));
                            ps.setString(11, "system"); // TODO: Get from JWT
                            ps.setString(12, id);
                            ps.executeUpdate();
                        }
                        
                        res.json(200, Map.of("id", id, "version", newVersion, "status", "updated"));
                    } else {
                        // Insert new
                        String insertSql = """
                            INSERT INTO appbana_wf_definition 
                            (id, app_id, name, description, trigger_entity, trigger_event, 
                             trigger_condition, version, status, definition_json, created_at, created_by)
                            VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?)
                            """;
                        
                        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                            ps.setString(1, id);
                            ps.setString(2, appId);
                            ps.setString(3, name);
                            ps.setString(4, description);
                            ps.setString(5, triggerEntity);
                            ps.setString(6, triggerEvent);
                            ps.setString(7, triggerCondition);
                            ps.setString(8, status);
                            ps.setString(9, definitionJson);
                            ps.setTimestamp(10, Timestamp.valueOf(LocalDateTime.now()));
                            ps.setString(11, "system");
                            ps.executeUpdate();
                        }
                        
                        res.json(201, Map.of("id", id, "version", 1, "status", "created"));
                    }
                }
            } catch (Exception e) {
                LOG.error("Failed to create/update workflow", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        };
    }
    
    /**
     * GET /api/workflows/:id - Get workflow definition
     */
    public static BiConsumer<com.appbana.api.Router.HttpRequest, com.appbana.api.Router.HttpResponse> getWorkflow() {
        return (req, res) -> {
            try {
                String id = req.pathParam("id");
                
                try (Connection conn = JdbcManager.getConnection()) {
                    String sql = "SELECT * FROM appbana_wf_definition WHERE id = ? ORDER BY version DESC LIMIT 1";
                    
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, id);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                WorkflowDefinitionDTO dto = mapDefinitionDTO(rs);
                                res.json(200, dto);
                            } else {
                                res.json(404, Map.of("error", "Workflow not found"));
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOG.error("Failed to get workflow", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        };
    }
    
    /**
     * GET /api/workflows - List workflows for app
     */
    public static BiConsumer<com.appbana.api.Router.HttpRequest, com.appbana.api.Router.HttpResponse> listWorkflows() {
        return (req, res) -> {
            try {
                String appId = req.query("appId");
                String status = req.query("status");
                
                try (Connection conn = JdbcManager.getConnection()) {
                    StringBuilder sql = new StringBuilder("SELECT * FROM appbana_wf_definition WHERE 1=1");
                    if (appId != null && !appId.isBlank()) {
                        sql.append(" AND app_id = ?");
                    }
                    if (status != null && !status.isBlank()) {
                        sql.append(" AND status = ?");
                    }
                    sql.append(" ORDER BY created_at DESC");
                    
                    try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                        int idx = 1;
                        if (appId != null && !appId.isBlank()) {
                            ps.setString(idx++, appId);
                        }
                        if (status != null && !status.isBlank()) {
                            ps.setString(idx++, status);
                        }
                        
                        List<WorkflowDefinitionDTO> workflows = new ArrayList<>();
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                workflows.add(mapDefinitionDTO(rs));
                            }
                        }
                        
                        LOG.info("Found {} workflows, attempting to serialize", workflows.size());
                        
                        // Serialize manually with proper ObjectMapper
                        try {
                            String json = getMapper().writeValueAsString(workflows);
                            LOG.info("Successfully serialized workflows");
                            res.text(200, json, "application/json");
                        } catch (Exception jsonEx) {
                            LOG.error("Failed to serialize workflows", jsonEx);
                            res.text(500, "{\"error\":\"" + jsonEx.getMessage() + "\"}", "application/json");
                        }
                    }
                }
            } catch (Exception e) {
                LOG.error("Failed to list workflows", e);
                res.text(500, "{\"error\":\"" + e.getMessage() + "\"}", "application/json");
            }
        };
    }
    
    /**
     * POST /api/workflows/:id/publish - Publish workflow (DRAFT → ACTIVE)
     */
    public static BiConsumer<com.appbana.api.Router.HttpRequest, com.appbana.api.Router.HttpResponse> publishWorkflow() {
        return (req, res) -> {
            try {
                String id = req.pathParam("id");
                
                try (Connection conn = JdbcManager.getConnection()) {
                    String sql = """
                        UPDATE appbana_wf_definition 
                        SET status = 'ACTIVE', version = version + 1, updated_at = ?
                        WHERE id = ? AND status = 'DRAFT'
                        """;
                    
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
                        ps.setString(2, id);
                        int rows = ps.executeUpdate();
                        
                        if (rows > 0) {
                            res.json(200, Map.of("status", "published"));
                        } else {
                            res.json(400, Map.of("error", "Workflow not found or already published"));
                        }
                    }
                }
            } catch (Exception e) {
                LOG.error("Failed to publish workflow", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        };
    }
    
    /**
     * POST /api/workflows/:id/start - Manually start workflow
     */
    public static BiConsumer<com.appbana.api.Router.HttpRequest, com.appbana.api.Router.HttpResponse> startWorkflow() {
        return (req, res) -> {
            try {
                String workflowId = req.pathParam("id");
                Map<String, Object> payload = req.readJson(new TypeReference<>() {});
                
                String entityId = (String) payload.get("entityId");
                String entityType = (String) payload.get("entityType");
                @SuppressWarnings("unchecked")
                Map<String, Object> entityData = (Map<String, Object>) payload.get("entityData");
                
                String instanceId = getEngine().startWorkflow(
                    workflowId, entityId, entityType, entityData, "system"
                );
                
                res.json(201, Map.of("instanceId", instanceId, "status", "started"));
            } catch (Exception e) {
                LOG.error("Failed to start workflow", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        };
    }
    
    /**
     * GET /api/my-tasks - Get current user's pending tasks
     */
    public static BiConsumer<com.appbana.api.Router.HttpRequest, com.appbana.api.Router.HttpResponse> getMyTasks() {
        return (req, res) -> {
            try {
                String userId = req.query("userId"); // TODO: Get from JWT
                
                try (Connection conn = JdbcManager.getConnection()) {
                    String sql = """
                        SELECT * FROM v_my_active_tasks 
                        WHERE assigned_user_id = ? OR assigned_role IN (
                            SELECT r.name FROM user_role ur 
                            JOIN role r ON ur.role_id = r.id 
                            WHERE ur.user_id = ?
                        )
                        ORDER BY due_at ASC NULLS LAST, arrived_at ASC
                        """;
                    
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, userId);
                        ps.setString(2, userId);
                        
                        List<Map<String, Object>> tasks = new ArrayList<>();
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                Map<String, Object> task = new LinkedHashMap<>();
                                task.put("tokenId", rs.getString("token_id"));
                                task.put("nodeId", rs.getString("node_id"));
                                task.put("assignedUserId", rs.getString("assigned_user_id"));
                                task.put("assignedRole", rs.getString("assigned_role"));
                                task.put("assignedQueue", rs.getString("assigned_queue"));
                                task.put("dueAt", rs.getTimestamp("due_at"));
                                task.put("slaStatus", rs.getString("sla_status"));
                                task.put("arrivedAt", rs.getTimestamp("arrived_at"));
                                task.put("instanceId", rs.getString("instance_id"));
                                task.put("entityId", rs.getString("entity_id"));
                                task.put("entityType", rs.getString("entity_type"));
                                task.put("contextData", rs.getString("context_data"));
                                task.put("workflowName", rs.getString("workflow_name"));
                                task.put("workflowDescription", rs.getString("workflow_description"));
                                task.put("appId", rs.getString("app_id"));
                                tasks.add(task);
                            }
                        }
                        
                        res.json(200, tasks);
                    }
                }
            } catch (Exception e) {
                LOG.error("Failed to get my tasks", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        };
    }
    
    /**
     * POST /api/my-tasks/:tokenId/complete - Complete task
     */
    public static BiConsumer<com.appbana.api.Router.HttpRequest, com.appbana.api.Router.HttpResponse> completeTask() {
        return (req, res) -> {
            try {
                String tokenId = req.pathParam("tokenId");
                Map<String, Object> payload = req.readJson(new TypeReference<>() {});
                
                String outcome = (String) payload.get("outcome");
                String taskData = getMapper().writeValueAsString(payload.get("taskData"));
                String userId = "system"; // TODO: Get from JWT
                
                getEngine().completeTask(tokenId, outcome, taskData, userId);
                
                res.json(200, Map.of("status", "completed"));
            } catch (Exception e) {
                LOG.error("Failed to complete task", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        };
    }
    
    /**
     * GET /api/workflow-instances - List workflow instances
     */
    public static BiConsumer<com.appbana.api.Router.HttpRequest, com.appbana.api.Router.HttpResponse> listInstances() {
        return (req, res) -> {
            try {
                String entityId = req.query("entityId");
                String entityType = req.query("entityType");
                String status = req.query("status");
                
                try (Connection conn = JdbcManager.getConnection()) {
                    StringBuilder sql = new StringBuilder("SELECT * FROM appbana_wf_instance WHERE 1=1");
                    if (entityId != null && !entityId.isBlank()) {
                        sql.append(" AND entity_id = ?");
                    }
                    if (entityType != null && !entityType.isBlank()) {
                        sql.append(" AND entity_type = ?");
                    }
                    if (status != null && !status.isBlank()) {
                        sql.append(" AND status = ?");
                    }
                    sql.append(" ORDER BY started_at DESC");
                    
                    try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                        int idx = 1;
                        if (entityId != null && !entityId.isBlank()) {
                            ps.setString(idx++, entityId);
                        }
                        if (entityType != null && !entityType.isBlank()) {
                            ps.setString(idx++, entityType);
                        }
                        if (status != null && !status.isBlank()) {
                            ps.setString(idx++, status);
                        }
                        
                        List<Map<String, Object>> instances = new ArrayList<>();
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                Map<String, Object> inst = new LinkedHashMap<>();
                                inst.put("id", rs.getString("id"));
                                inst.put("workflowDefinitionId", rs.getString("workflow_definition_id"));
                                inst.put("workflowVersion", rs.getInt("workflow_version"));
                                inst.put("appId", rs.getString("app_id"));
                                inst.put("entityId", rs.getString("entity_id"));
                                inst.put("entityType", rs.getString("entity_type"));
                                inst.put("status", rs.getString("status"));
                                inst.put("startedAt", rs.getTimestamp("started_at"));
                                inst.put("completedAt", rs.getTimestamp("completed_at"));
                                instances.add(inst);
                            }
                        }
                        
                        res.json(200, instances);
                    }
                }
            } catch (Exception e) {
                LOG.error("Failed to list instances", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        };
    }
    
    // Helper methods
    
    /**
     * Check and auto-start workflows triggered by entity operations
     * Called from PostOperationHooks after entity INSERT/UPDATE
     */
    public static void checkAndStartWorkflows(String entityType, String event, String entityId, Map<String, Object> entityData) {
        LOG.info("checkAndStartWorkflows called: entityType={}, event={}, entityId={}, entityData keys={}", 
            entityType, event, entityId, entityData != null ? entityData.keySet() : "NULL");
        
        try (Connection conn = JdbcManager.getConnection()) {
            // Find active workflows for this entity+event
            String sql = """
                SELECT id, trigger_condition FROM appbana_wf_definition 
                WHERE trigger_entity = ? AND trigger_event = ? AND status = 'ACTIVE'
                """;
            
            LOG.info("Searching for workflows: trigger_entity={}, trigger_event={}", entityType, event);
            
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, entityType);
                ps.setString(2, event);
                
                int workflowCount = 0;
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        workflowCount++;
                        String workflowId = rs.getString("id");
                        String condition = rs.getString("trigger_condition");
                        
                        // Unescape Flyway placeholder: $$ -> $
                        if (condition != null && condition.contains("$${")) {
                            condition = condition.replace("$${", "${");
                            LOG.debug("Unescaped trigger condition for workflow {}", workflowId);
                        }
                        
                        LOG.info("Found workflow #{}: id={}, condition={}", workflowCount, workflowId, condition);
                        
                        // Evaluate trigger condition
                        Map<String, Object> context = com.appbana.workflow.ExpressionEvaluator.createContext(entityType, entityData);
                        LOG.info("Evaluating trigger for workflow: {} | condition: {} | context keys: {} | entity data: {}", 
                            workflowId, condition, context.keySet(), entityData);
                        boolean shouldTrigger = com.appbana.workflow.ExpressionEvaluator.evaluateCondition(condition, context);
                        
                        LOG.info("Trigger evaluation result: shouldTrigger={}", shouldTrigger);
                        
                        if (shouldTrigger) {
                            try {
                                String instanceId = getEngine().startWorkflow(
                                    workflowId, entityId, entityType, entityData, "system"
                                );
                                LOG.info("Auto-started workflow: {} for entity: {}/{}", workflowId, entityType, entityId);
                            } catch (Exception e) {
                                LOG.error("Failed to auto-start workflow: {}", workflowId, e);
                            }
                        } else {
                            LOG.info("Workflow {} NOT triggered - condition not met", workflowId);
                        }
                    }
                }
                
                LOG.info("checkAndStartWorkflows complete: found {} workflows", workflowCount);
            }
        } catch (Exception e) {
            LOG.error("Failed to check workflows for entity: {}/{}", entityType, entityId, e);
        }
    }
    
    private static WorkflowDefinitionDTO mapDefinitionDTO(ResultSet rs) throws SQLException {
        return new WorkflowDefinitionDTO(
            rs.getString("id"),
            rs.getString("app_id"),
            rs.getString("name"),
            rs.getString("description"),
            rs.getString("trigger_entity"),
            rs.getString("trigger_event"),
            rs.getString("trigger_condition"),
            rs.getInt("version"),
            rs.getString("status"),
            rs.getString("definition_json"),
            rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null,
            rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toLocalDateTime() : null,
            rs.getString("created_by"),
            rs.getString("updated_by")
        );
    }
}
