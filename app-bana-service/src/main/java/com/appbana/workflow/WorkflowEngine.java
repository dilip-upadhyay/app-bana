package com.appbana.workflow;

import com.appbana.workflow.model.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Workflow Engine - Core execution logic
 * Phase 1: Basic execution with USER_TASK, SERVICE_TASK, DECISION
 * 
 * Key responsibilities:
 * 1. Start workflow instances (manual or auto-triggered)
 * 2. Execute transitions (user completes task → advance to next node)
 * 3. Evaluate decision node conditions
 * 4. Handle SERVICE_TASK auto-execution
 * 5. Manage workflow versioning (instances lock to definition version)
 */
public class WorkflowEngine {
    private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);
    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    
    public WorkflowEngine(DataSource dataSource) {
        this.dataSource = dataSource;
        this.objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules(); // Support Java 8 date/time
    }
    
    /**
     * Start a new workflow instance
     * Called when entity is created/updated and trigger condition matches
     * 
     * @param workflowId Workflow definition ID
     * @param entityId Entity that triggered workflow
     * @param entityType Entity name (e.g., "PaymentRequest")
     * @param entityData Entity data as Map
     * @param userId User who triggered workflow
     * @return New workflow instance ID
     */
    public String startWorkflow(String workflowId, String entityId, String entityType, 
                                Map<String, Object> entityData, String userId) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            
            try {
                // 1. Load workflow definition (ACTIVE only)
                WorkflowDefinition definition = loadWorkflowDefinition(conn, workflowId);
                if (definition == null || definition.getStatus() != WorkflowDefinition.WorkflowStatus.ACTIVE) {
                    throw new IllegalStateException("Workflow not found or not active: " + workflowId);
                }
                
                // 2. Parse workflow JSON
                Map<String, Object> workflowJson = objectMapper.readValue(
                    definition.getDefinitionJson(), 
                    new TypeReference<Map<String, Object>>() {}
                );
                
                @SuppressWarnings("unchecked")
                Map<String, Map<String, Object>> nodes = (Map<String, Map<String, Object>>) workflowJson.get("nodes");
                
                // 3. Create workflow instance (lock to current version)
                String instanceId = UUID.randomUUID().toString();
                String contextJson = objectMapper.writeValueAsString(entityData);
                
                String insertInstance = """
                    INSERT INTO appbana_wf_instance 
                    (id, workflow_definition_id, workflow_version, app_id, entity_id, entity_type, 
                     status, started_at, context_data, created_by)
                    VALUES (?, ?, ?, ?, ?, ?, 'RUNNING', ?, ?, ?)
                    """;
                
                try (PreparedStatement ps = conn.prepareStatement(insertInstance)) {
                    ps.setString(1, instanceId);
                    ps.setString(2, definition.getId());
                    ps.setInt(3, definition.getVersion());
                    ps.setString(4, definition.getAppId());
                    ps.setString(5, entityId);
                    ps.setString(6, entityType);
                    ps.setTimestamp(7, Timestamp.valueOf(LocalDateTime.now()));
                    ps.setString(8, contextJson);
                    ps.setString(9, userId);
                    ps.executeUpdate();
                }
                
                // 4. Find START node and create initial token
                String startNodeId = findStartNode(nodes);
                if (startNodeId == null) {
                    throw new IllegalStateException("Workflow has no START node: " + workflowId);
                }
                
                createToken(conn, instanceId, startNodeId, "START", null);
                
                // 5. Immediately transition from START to first real node
                advanceFromNode(conn, instanceId, startNodeId, nodes, workflowJson, 
                               entityType, entityData);
                
                conn.commit();
                log.info("Started workflow instance: {} for entity: {}/{}", instanceId, entityType, entityId);
                return instanceId;
                
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (Exception e) {
            log.error("Failed to start workflow: {}", workflowId, e);
            throw new RuntimeException("Failed to start workflow", e);
        }
    }
    
    /**
     * Complete a USER_TASK and transition to next node
     * 
     * @param tokenId Token ID (from my-tasks API)
     * @param outcome User's decision (e.g., "APPROVE", "REJECT")
     * @param taskData User's form data (JSON string)
     * @param userId User who completed task
     */
    public void completeTask(String tokenId, String outcome, String taskData, String userId) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            
            try {
                // 1. Load token
                WorkflowToken token = loadToken(conn, tokenId);
                if (token == null || token.getStatus() != WorkflowToken.TokenStatus.ACTIVE) {
                    throw new IllegalStateException("Token not found or not active: " + tokenId);
                }
                
                if (token.getNodeType() != WorkflowToken.NodeType.USER_TASK) {
                    throw new IllegalStateException("Token is not a USER_TASK: " + tokenId);
                }
                
                // 2. Mark token as completed
                String updateToken = """
                    UPDATE appbana_wf_token 
                    SET status = 'COMPLETED', completed_at = ?, completed_by = ?, 
                        outcome = ?, task_data = ?
                    WHERE id = ?
                    """;
                
                try (PreparedStatement ps = conn.prepareStatement(updateToken)) {
                    ps.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
                    ps.setString(2, userId);
                    ps.setString(3, outcome);
                    ps.setString(4, taskData);
                    ps.setString(5, tokenId);
                    ps.executeUpdate();
                }
                
                // 3. Load workflow instance and definition
                WorkflowInstance instance = loadInstance(conn, token.getWorkflowInstanceId());
                WorkflowDefinition definition = loadWorkflowDefinitionByInstanceVersion(
                    conn, instance.getWorkflowDefinitionId(), instance.getWorkflowVersion()
                );
                
                Map<String, Object> workflowJson = objectMapper.readValue(
                    definition.getDefinitionJson(), 
                    new TypeReference<Map<String, Object>>() {}
                );
                
                @SuppressWarnings("unchecked")
                Map<String, Map<String, Object>> nodes = (Map<String, Map<String, Object>>) workflowJson.get("nodes");
                
                // 4. Load entity data from context
                Map<String, Object> entityData = objectMapper.readValue(
                    instance.getContextData(), 
                    new TypeReference<Map<String, Object>>() {}
                );
                
                // 5. Advance to next node
                advanceFromNode(conn, instance.getId(), token.getNodeId(), nodes, workflowJson, 
                               instance.getEntityType(), entityData);
                
                conn.commit();
                log.info("Completed task: {} with outcome: {}", tokenId, outcome);
                
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (Exception e) {
            log.error("Failed to complete task: {}", tokenId, e);
            throw new RuntimeException("Failed to complete task", e);
        }
    }
    
    /**
     * Advance from current node to next node(s)
     * Handles DECISION nodes, SERVICE_TASK auto-execution, and END nodes
     */
    private void advanceFromNode(Connection conn, String instanceId, String fromNodeId,
                                Map<String, Map<String, Object>> nodes,
                                Map<String, Object> workflowJson,
                                String entityType, Map<String, Object> entityData) throws Exception {
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> transitions = (List<Map<String, Object>>) workflowJson.get("transitions");
        
        // Find transitions from this node
        List<Map<String, Object>> outgoing = transitions.stream()
            .filter(t -> fromNodeId.equals(t.get("from")))
            .toList();
        
        if (outgoing.isEmpty()) {
            log.warn("No outgoing transitions from node: {}", fromNodeId);
            return;
        }
        
        // Evaluate conditions to find next node(s)
        Map<String, Object> context = ExpressionEvaluator.createContext(entityType, entityData);
        
        for (Map<String, Object> transition : outgoing) {
            String condition = (String) transition.get("condition");
            String toNodeId = (String) transition.get("to");
            
            // Evaluate condition (null condition = always take transition)
            if (condition == null || ExpressionEvaluator.evaluateCondition(condition, context)) {
                Map<String, Object> toNode = nodes.get(toNodeId);
                String nodeType = (String) toNode.get("type");
                
                if ("END".equals(nodeType)) {
                    // Complete workflow
                    completeWorkflowInstance(conn, instanceId);
                    log.info("Workflow completed: {}", instanceId);
                    
                } else if ("USER_TASK".equals(nodeType)) {
                    // Create token and pause (wait for user)
                    createUserTaskToken(conn, instanceId, toNodeId, toNode, entityData);
                    log.info("Created USER_TASK token: {} in instance: {}", toNodeId, instanceId);
                    
                } else if ("SERVICE_TASK".equals(nodeType)) {
                    // Execute service task immediately
                    executeServiceTask(conn, instanceId, toNodeId, toNode, entityData);
                    // Continue advancing (recursive)
                    advanceFromNode(conn, instanceId, toNodeId, nodes, workflowJson, entityType, entityData);
                    
                } else if ("DECISION".equals(nodeType)) {
                    // Decision nodes have no token, just route
                    advanceFromNode(conn, instanceId, toNodeId, nodes, workflowJson, entityType, entityData);
                    
                } else if ("WAIT".equals(nodeType)) {
                    // Phase 2: Time-based wait
                    log.warn("WAIT nodes not yet implemented: {}", toNodeId);
                }
                
                // For Phase 1: Take only first matching transition (no parallel)
                break;
            }
        }
    }
    
    /**
     * Create token for USER_TASK (pauses workflow)
     */
    private void createUserTaskToken(Connection conn, String instanceId, String nodeId,
                                    Map<String, Object> nodeData, Map<String, Object> entityData) throws Exception {
        String tokenId = UUID.randomUUID().toString();
        String assignmentType = (String) nodeData.get("assignmentType");
        Integer slaHours = (Integer) nodeData.get("slaHours");
        
        LocalDateTime dueAt = slaHours != null ? LocalDateTime.now().plusHours(slaHours) : null;
        
        String sql = """
            INSERT INTO appbana_wf_token 
            (id, workflow_instance_id, node_id, node_type, status, assignment_type, 
             assigned_user_id, assigned_role, assigned_queue, due_at, sla_status, arrived_at)
            VALUES (?, ?, ?, 'USER_TASK', 'ACTIVE', ?, ?, ?, ?, ?, 'ON_TIME', ?)
            """;
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tokenId);
            ps.setString(2, instanceId);
            ps.setString(3, nodeId);
            ps.setString(4, assignmentType);
            
            // Resolve assignment
            if ("USER".equals(assignmentType)) {
                String userId = (String) nodeData.get("assignedUserId");
                ps.setString(5, userId);
                ps.setNull(6, Types.VARCHAR);
                ps.setNull(7, Types.VARCHAR);
            } else if ("ROLE".equals(assignmentType)) {
                String role = (String) nodeData.get("assignedRole");
                ps.setNull(5, Types.VARCHAR);
                ps.setString(6, role);
                ps.setNull(7, Types.VARCHAR);
            } else if ("QUEUE".equals(assignmentType)) {
                String queue = (String) nodeData.get("assignedQueue");
                ps.setNull(5, Types.VARCHAR);
                ps.setNull(6, Types.VARCHAR);
                ps.setString(7, queue);
            } else if ("DYNAMIC".equals(assignmentType)) {
                String expression = (String) nodeData.get("assignmentExpression");
                Object resolvedUser = ExpressionEvaluator.evaluateValue(expression, 
                    ExpressionEvaluator.createContext("entity", entityData));
                ps.setString(5, resolvedUser != null ? resolvedUser.toString() : null);
                ps.setNull(6, Types.VARCHAR);
                ps.setNull(7, Types.VARCHAR);
            } else {
                ps.setNull(5, Types.VARCHAR);
                ps.setNull(6, Types.VARCHAR);
                ps.setNull(7, Types.VARCHAR);
            }
            
            if (dueAt != null) {
                ps.setTimestamp(8, Timestamp.valueOf(dueAt));
            } else {
                ps.setNull(8, Types.TIMESTAMP);
            }
            
            ps.setTimestamp(9, Timestamp.valueOf(LocalDateTime.now()));
            ps.executeUpdate();
        }
    }
    
    /**
     * Execute SERVICE_TASK immediately (no user interaction)
     * Phase 1: Log action only (Phase 2 will implement actual updates)
     */
    private void executeServiceTask(Connection conn, String instanceId, String nodeId,
                                   Map<String, Object> nodeData, Map<String, Object> entityData) throws Exception {
        String serviceAction = (String) nodeData.get("serviceAction");
        log.info("Executing SERVICE_TASK: {} action: {}", nodeId, serviceAction);
        
        // Create and immediately complete token (for audit trail)
        String tokenId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        
        String sql = """
            INSERT INTO appbana_wf_token 
            (id, workflow_instance_id, node_id, node_type, status, arrived_at, completed_at)
            VALUES (?, ?, ?, 'SERVICE_TASK', 'COMPLETED', ?, ?)
            """;
        
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tokenId);
            ps.setString(2, instanceId);
            ps.setString(3, nodeId);
            ps.setTimestamp(4, Timestamp.valueOf(now));
            ps.setTimestamp(5, Timestamp.valueOf(now));
            ps.executeUpdate();
        }
        
        // Phase 2: Implement UPDATE_ENTITY, SEND_EMAIL, etc.
    }
    
    /**
     * Create generic token (for START nodes)
     */
    private void createToken(Connection conn, String instanceId, String nodeId, String nodeType, String status) throws SQLException {
        String tokenId = UUID.randomUUID().toString();
        String sql = """
            INSERT INTO appbana_wf_token 
            (id, workflow_instance_id, node_id, node_type, status, arrived_at, completed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
        
        LocalDateTime now = LocalDateTime.now();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tokenId);
            ps.setString(2, instanceId);
            ps.setString(3, nodeId);
            ps.setString(4, nodeType);
            ps.setString(5, status != null ? status : "COMPLETED");
            ps.setTimestamp(6, Timestamp.valueOf(now));
            ps.setTimestamp(7, Timestamp.valueOf(now));
            ps.executeUpdate();
        }
    }
    
    /**
     * Mark workflow instance as completed
     */
    private void completeWorkflowInstance(Connection conn, String instanceId) throws SQLException {
        String sql = "UPDATE appbana_wf_instance SET status = 'COMPLETED', completed_at = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            ps.setString(2, instanceId);
            ps.executeUpdate();
        }
    }
    
    /**
     * Find START node in workflow graph
     */
    private String findStartNode(Map<String, Map<String, Object>> nodes) {
        return nodes.entrySet().stream()
            .filter(e -> "START".equals(e.getValue().get("type")))
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(null);
    }
    
    // ==================== Database Queries ====================
    
    private WorkflowDefinition loadWorkflowDefinition(Connection conn, String workflowId) throws SQLException {
        String sql = "SELECT * FROM appbana_wf_definition WHERE id = ? AND status = 'ACTIVE'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, workflowId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapWorkflowDefinition(rs);
                }
            }
        }
        return null;
    }
    
    private WorkflowDefinition loadWorkflowDefinitionByInstanceVersion(Connection conn, String defId, int version) throws SQLException {
        String sql = "SELECT * FROM appbana_wf_definition WHERE id = ? AND version = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, defId);
            ps.setInt(2, version);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapWorkflowDefinition(rs);
                }
            }
        }
        return null;
    }
    
    private WorkflowInstance loadInstance(Connection conn, String instanceId) throws SQLException {
        String sql = "SELECT * FROM appbana_wf_instance WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, instanceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapWorkflowInstance(rs);
                }
            }
        }
        return null;
    }
    
    private WorkflowToken loadToken(Connection conn, String tokenId) throws SQLException {
        String sql = "SELECT * FROM appbana_wf_token WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tokenId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapWorkflowToken(rs);
                }
            }
        }
        return null;
    }
    
    private WorkflowDefinition mapWorkflowDefinition(ResultSet rs) throws SQLException {
        WorkflowDefinition def = new WorkflowDefinition();
        def.setId(rs.getString("id"));
        def.setAppId(rs.getString("app_id"));
        def.setName(rs.getString("name"));
        def.setDescription(rs.getString("description"));
        def.setTriggerEntity(rs.getString("trigger_entity"));
        def.setTriggerEvent(rs.getString("trigger_event"));
        def.setTriggerCondition(rs.getString("trigger_condition"));
        def.setVersion(rs.getInt("version"));
        def.setStatus(WorkflowDefinition.WorkflowStatus.valueOf(rs.getString("status")));
        def.setDefinitionJson(rs.getString("definition_json"));
        
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) def.setCreatedAt(createdAt.toLocalDateTime());
        
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) def.setUpdatedAt(updatedAt.toLocalDateTime());
        
        def.setCreatedBy(rs.getString("created_by"));
        def.setUpdatedBy(rs.getString("updated_by"));
        return def;
    }
    
    private WorkflowInstance mapWorkflowInstance(ResultSet rs) throws SQLException {
        WorkflowInstance inst = new WorkflowInstance();
        inst.setId(rs.getString("id"));
        inst.setWorkflowDefinitionId(rs.getString("workflow_definition_id"));
        inst.setWorkflowVersion(rs.getInt("workflow_version"));
        inst.setAppId(rs.getString("app_id"));
        inst.setEntityId(rs.getString("entity_id"));
        inst.setEntityType(rs.getString("entity_type"));
        inst.setStatus(WorkflowInstance.InstanceStatus.valueOf(rs.getString("status")));
        
        Timestamp startedAt = rs.getTimestamp("started_at");
        if (startedAt != null) inst.setStartedAt(startedAt.toLocalDateTime());
        
        Timestamp completedAt = rs.getTimestamp("completed_at");
        if (completedAt != null) inst.setCompletedAt(completedAt.toLocalDateTime());
        
        inst.setErrorMessage(rs.getString("error_message"));
        inst.setContextData(rs.getString("context_data"));
        inst.setCreatedBy(rs.getString("created_by"));
        return inst;
    }
    
    private WorkflowToken mapWorkflowToken(ResultSet rs) throws SQLException {
        WorkflowToken token = new WorkflowToken();
        token.setId(rs.getString("id"));
        token.setWorkflowInstanceId(rs.getString("workflow_instance_id"));
        token.setNodeId(rs.getString("node_id"));
        
        String nodeType = rs.getString("node_type");
        if (nodeType != null) token.setNodeType(WorkflowToken.NodeType.valueOf(nodeType));
        
        String status = rs.getString("status");
        if (status != null) token.setStatus(WorkflowToken.TokenStatus.valueOf(status));
        
        String assignmentType = rs.getString("assignment_type");
        if (assignmentType != null) token.setAssignmentType(WorkflowToken.AssignmentType.valueOf(assignmentType));
        
        token.setAssignedUserId(rs.getString("assigned_user_id"));
        token.setAssignedRole(rs.getString("assigned_role"));
        token.setAssignedQueue(rs.getString("assigned_queue"));
        
        Timestamp dueAt = rs.getTimestamp("due_at");
        if (dueAt != null) token.setDueAt(dueAt.toLocalDateTime());
        
        String slaStatus = rs.getString("sla_status");
        if (slaStatus != null) token.setSlaStatus(WorkflowToken.SlaStatus.valueOf(slaStatus));
        
        Timestamp arrivedAt = rs.getTimestamp("arrived_at");
        if (arrivedAt != null) token.setArrivedAt(arrivedAt.toLocalDateTime());
        
        Timestamp completedAt = rs.getTimestamp("completed_at");
        if (completedAt != null) token.setCompletedAt(completedAt.toLocalDateTime());
        
        token.setCompletedBy(rs.getString("completed_by"));
        token.setOutcome(rs.getString("outcome"));
        token.setTaskData(rs.getString("task_data"));
        return token;
    }
}
