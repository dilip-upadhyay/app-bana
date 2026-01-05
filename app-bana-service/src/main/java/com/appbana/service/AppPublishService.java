package com.appbana.service;

import com.appbana.SchemaManager;
import com.appbana.converter.EntitySchemaConverter;
import com.appbana.exception.DeploymentException;
import com.appbana.exception.ValidationException;
import com.appbana.model.AppVersion;
import com.appbana.model.AppVersion.Environment;
import com.appbana.model.AppVersion.DeploymentStatus;
import com.appbana.model.DeploymentResult;
import com.appbana.model.EntitySchema;
import com.appbana.model.TenantContext;
import com.appbana.repository.AppVersionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Core service for publishing apps to environments.
 * Handles transactional schema deployment with versioning.
 */
public class AppPublishService {
    private static final Logger LOG = LoggerFactory.getLogger(AppPublishService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private final Connection connection;
    private final SchemaManager schemaManager;
    private final AppVersionRepository versionRepository;
    
    public AppPublishService(Connection connection, SchemaManager schemaManager) {
        this.connection = connection;
        this.schemaManager = schemaManager;
        this.versionRepository = new AppVersionRepository(connection);
    }
    
    /**
     * Publish an app to a specific environment.
     * Creates all entity tables in a single transaction.
     * 
     * @param appMetaJson Full AppMeta JSON from frontend
     * @param appId App identifier
     * @param tenantId Tenant identifier
     * @param environment Target environment (DEV/SIT/PROD)
     * @param userId User performing deployment
     * @return DeploymentResult with version info and created tables
     */
    public DeploymentResult publishApp(String appMetaJson, String appId, String tenantId, 
                                       Environment environment, String userId) {
        long startTime = System.currentTimeMillis();
        LOG.info("[PUBLISH] Starting deployment: app={}, tenant={}, env={}, user={}", 
                appId, tenantId, environment, userId);
        
        try {
            // Parse AppMeta JSON
            JsonNode appMeta = objectMapper.readTree(appMetaJson);
            
            // Step 1: Validate and convert entities to schemas
            LOG.info("[PUBLISH] Step 1: Validating and converting entities...");
            List<EntitySchema> schemas = validateAndConvertEntities(appMeta);
            LOG.info("[PUBLISH] Validated {} entities", schemas.size());
            
            // Step 2: Get next version number
            int nextVersion = versionRepository.getNextVersion(appId, tenantId, environment);
            LOG.info("[PUBLISH] Step 2: Next version number: {}", nextVersion);
            
            // Step 3: Deploy schemas transactionally
            LOG.info("[PUBLISH] Step 3: Deploying schemas in transaction...");
            List<String> tablesCreated = deploySchemasTransactionally(schemas, appId, tenantId, environment);
            LOG.info("[PUBLISH] Successfully created {} tables: {}", tablesCreated.size(), tablesCreated);
            
            // Step 4: Save version snapshot
            long duration = System.currentTimeMillis() - startTime;
            LOG.info("[PUBLISH] Step 4: Saving version snapshot...");
            AppVersion appVersion = saveVersionSnapshot(
                    appId, tenantId, nextVersion, environment, appMetaJson,
                    tablesCreated, userId, duration, DeploymentStatus.SUCCESS, null, null
            );
            
            LOG.info("[PUBLISH] ✅ Deployment completed successfully in {}ms", duration);
            return DeploymentResult.success(appVersion);
            
        } catch (ValidationException e) {
            long duration = System.currentTimeMillis() - startTime;
            LOG.error("[PUBLISH] ❌ Validation failed: {}", e.getMessage());
            return DeploymentResult.failure(appId, tenantId, environment, 
                    "Validation failed: " + e.getMessage(), getStackTrace(e));
                    
        } catch (DeploymentException e) {
            long duration = System.currentTimeMillis() - startTime;
            LOG.error("[PUBLISH] ❌ Deployment failed: {}", e.getMessage(), e);
            return DeploymentResult.failure(appId, tenantId, environment,
                    "Deployment failed: " + e.getMessage(), getStackTrace(e));
                    
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            LOG.error("[PUBLISH] ❌ Unexpected error: {}", e.getMessage(), e);
            return DeploymentResult.failure(appId, tenantId, environment,
                    "Unexpected error: " + e.getMessage(), getStackTrace(e));
        }
    }
    
    /**
     * Step 1: Validate entities and convert to EntitySchema objects
     */
    private List<EntitySchema> validateAndConvertEntities(JsonNode appMeta) throws ValidationException {
        List<EntitySchema> schemas = new ArrayList<>();
        
        // Get entities array
        JsonNode entitiesNode = appMeta.get("entities");
        if (entitiesNode == null || !entitiesNode.isArray() || entitiesNode.size() == 0) {
            throw new ValidationException("App must have at least one entity");
        }
        
        LOG.debug("[PUBLISH] Found {} entities to validate", entitiesNode.size());
        
        for (JsonNode entityNode : entitiesNode) {
            // Get entity name
            String entityName = entityNode.has("name") ? entityNode.get("name").asText() : null;
            if (entityName == null || entityName.isEmpty()) {
                throw new ValidationException("Entity must have a name");
            }
            
            // Validate entity name
            if (!EntitySchemaConverter.isValidEntityName(entityName)) {
                throw new ValidationException("Invalid entity name: " + entityName + 
                        " (must start with letter, contain only alphanumeric and underscore)");
            }
            
            // Validate fields exist
            JsonNode fieldsNode = entityNode.get("fields");
            if (fieldsNode == null || !fieldsNode.isArray() || fieldsNode.size() == 0) {
                throw new ValidationException("Entity " + entityName + " must have at least one field");
            }
            
            // Validate each field name
            for (JsonNode fieldNode : fieldsNode) {
                String fieldName = fieldNode.has("name") ? fieldNode.get("name").asText() : null;
                if (fieldName == null || fieldName.isEmpty()) {
                    throw new ValidationException("All fields in entity " + entityName + " must have a name");
                }
                if (!EntitySchemaConverter.isValidFieldName(fieldName)) {
                    throw new ValidationException("Invalid field name: " + fieldName + 
                            " in entity " + entityName);
                }
            }
            
            // Convert to EntitySchema
            EntitySchema schema = EntitySchemaConverter.convert(entityName, entityNode);
            schemas.add(schema);
            LOG.debug("[PUBLISH] Validated entity: {} with {} fields", entityName, schema.getFields().size());
        }
        
        return schemas;
    }
    
    /**
     * Step 3: Deploy schemas in a single transaction.
     * If any table creation fails, all changes are rolled back.
     */
    private List<String> deploySchemasTransactionally(List<EntitySchema> schemas, 
                                                      String appId, String tenantId,
                                                      Environment environment) throws DeploymentException {
        List<String> tablesCreated = new ArrayList<>();
        boolean originalAutoCommit = true;
        
        try {
            // Start transaction
            originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            LOG.debug("[PUBLISH] Transaction started (autoCommit=false)");
            
            // Create each table
            for (EntitySchema schema : schemas) {
                String logicalEntityName = schema.getName(); // e.g., "User"
                
                // Set tenant/app context but KEEP the logical entity name
                // SchemaManager will handle physical naming via getPhysicalTableName()
                schema.setTenantId(tenantId);
                schema.setAppId(appId);
                
                // Set TenantContext with environment for physical table naming
                TenantContext.set(new TenantContext(tenantId, appId, environment.name()));
                
                try {
                    // Get physical table name AFTER setting context (SchemaManager uses TenantContext)
                    String physicalTableName = SchemaManager.getPhysicalTableName(schema);
                    LOG.info("[PUBLISH] Creating table: {} for entity: {}", physicalTableName, logicalEntityName);
                    
                    // Create table via SchemaManager (it will use getPhysicalTableName internally)
                    SchemaManager.saveSchema(schema);
                    
                    tablesCreated.add(physicalTableName);
                    LOG.debug("[PUBLISH] Table created successfully: {}", physicalTableName);
                } finally {
                    // Clear context after each schema
                    TenantContext.clear();
                }
            }
            
            // Commit transaction
            connection.commit();
            LOG.info("[PUBLISH] Transaction committed successfully");
            
            return tablesCreated;
            
        } catch (Exception e) {
            // Rollback on any error
            try {
                connection.rollback();
                LOG.error("[PUBLISH] Transaction rolled back due to error: {}", e.getMessage());
            } catch (Exception rollbackEx) {
                LOG.error("[PUBLISH] Failed to rollback transaction", rollbackEx);
            }
            
            throw new DeploymentException("Failed to create tables: " + e.getMessage(), e);
            
        } finally {
            // Restore original auto-commit
            try {
                connection.setAutoCommit(originalAutoCommit);
                LOG.debug("[PUBLISH] Transaction ended (autoCommit={})", originalAutoCommit);
            } catch (Exception e) {
                LOG.error("[PUBLISH] Failed to restore autoCommit", e);
            }
        }
    }
    
    /**
     * Step 4: Save version snapshot to app_versions table
     */
    private AppVersion saveVersionSnapshot(String appId, String tenantId, int version,
                                          Environment environment, String appSnapshot,
                                          List<String> tablesCreated, String deployedBy,
                                          long durationMs, DeploymentStatus status,
                                          String errorMessage, String errorStackTrace) throws Exception {
        AppVersion appVersion = AppVersion.builder()
                .appId(appId)
                .tenantId(tenantId)
                .version(version)
                .environment(environment)
                .status(status)
                .appSnapshot(appSnapshot)
                .tablesCreated(tablesCreated)
                .deployedBy(deployedBy)
                .deployedAt(Instant.now())
                .durationMs(durationMs)
                .errorMessage(errorMessage)
                .errorStackTrace(errorStackTrace)
                .build();
        
        return versionRepository.save(appVersion);
    }
    
    /**
     * Generate physical table name with environment prefix.
     * Format: app_{ENV}_{tenantId}_{entityName}
     * Example: app_DEV_tenant1_customer
     */
    
    /**
     * Get stack trace as string
     */
    private String getStackTrace(Throwable e) {
        StringBuilder sb = new StringBuilder();
        sb.append(e.getClass().getName()).append(": ").append(e.getMessage()).append("\n");
        for (StackTraceElement element : e.getStackTrace()) {
            sb.append("\tat ").append(element.toString()).append("\n");
        }
        return sb.toString();
    }
}
