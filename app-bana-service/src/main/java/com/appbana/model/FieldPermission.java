package com.appbana.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Field-Level Security (FLS) Permission Entity
 * 
 * <p>Represents granular field-level permissions for HIPAA/PCI-DSS compliance.
 * Controls which roles can read and edit specific fields on entities.</p>
 * 
 * <h3>Permission Model</h3>
 * <ul>
 *   <li><b>roleId</b>: Role that has the permission</li>
 *   <li><b>entityName</b>: Entity name (e.g., "User", "Project", "Invoice")</li>
 *   <li><b>fieldName</b>: Field name or "*" for wildcard (all fields)</li>
 *   <li><b>readable</b>: Can the role read this field?</li>
 *   <li><b>editable</b>: Can the role edit this field?</li>
 * </ul>
 * 
 * <h3>Examples</h3>
 * <pre>
 * // Admin can read/write all User fields (using Builder)
 * FieldPermission.builder()
 *     .roleId("admin-role-id")
 *     .entityName("User")
 *     .fieldName("*")
 *     .readable(true)
 *     .editable(true)
 *     .build();
 * 
 * // Manager can read User.salary but not edit
 * FieldPermission.builder()
 *     .roleId("manager-role-id")
 *     .entityName("User")
 *     .fieldName("salary")
 *     .readable(true)
 *     .editable(false)
 *     .build();
 * </pre>
 * 
 * <h3>Wildcard Matching</h3>
 * <ul>
 *   <li>fieldName="*" grants access to ALL fields</li>
 *   <li>Specific field permissions override wildcards</li>
 *   <li>Multiple roles combine (OR logic): read/edit if ANY role grants</li>
 * </ul>
 * 
 * <p>Uses Lombok for cleaner code with @Builder pattern</p>
 * 
 * @see com.appbana.service.PermissionService#canReadField(String, String, String)
 * @see com.appbana.service.PermissionService#canEditField(String, String, String)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldPermission {
    
    @Builder.Default
    private String id = UUID.randomUUID().toString();
    
    private String roleId;
    private String entityName;
    private String fieldName;
    private boolean readable;
    private boolean editable;
    
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    // Constants for common field names
    public static final String WILDCARD = "*";
    public static final String FIELD_SALARY = "salary";
    public static final String FIELD_SSN = "ssn";
    public static final String FIELD_PASSWORD_HASH = "passwordHash";
    public static final String FIELD_CREDIT_CARD = "creditCard";
    
    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }
    
    public String getEntityName() {
        return entityName;
    }
    
    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }
    
    public String getFieldName() {
        return fieldName;
    }
    
    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }
    
    public boolean isReadable() {
        return readable;
    }
    
    public void setReadable(boolean readable) {
        this.readable = readable;
    }
    
    public boolean isEditable() {
        return editable;
    }
    
    public void setEditable(boolean editable) {
        this.editable = editable;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    // Utility Methods
    
    /**
     * Check if this permission is a wildcard (applies to all fields)
     * 
     * @return true if fieldName is "*"
     */
    public boolean isWildcard() {
        return WILDCARD.equals(fieldName);
    }
    
    /**
     * Check if this permission matches a specific field
     * 
     * @param targetFieldName Field name to check
     * @return true if wildcard OR exact field name match
     */
    public boolean matchesField(String targetFieldName) {
        return isWildcard() || fieldName.equals(targetFieldName);
    }
    
    /**
     * Create a wildcard permission (all fields)
     * 
     * @param roleId Role ID
     * @param entityName Entity name
     * @param readable Can read all fields?
     * @param editable Can edit all fields?
     * @return New FieldPermission with wildcard
     */
    public static FieldPermission createWildcard(String roleId, String entityName, 
                                                 boolean readable, boolean editable) {
        return FieldPermission.builder()
                .roleId(roleId)
                .entityName(entityName)
                .fieldName(WILDCARD)
                .readable(readable)
                .editable(editable)
                .build();
    }
    
    /**
     * Create a read-only permission
     * 
     * @param roleId Role ID
     * @param entityName Entity name
     * @param fieldName Field name
     * @return New FieldPermission (readable=true, editable=false)
     */
    public static FieldPermission createReadOnly(String roleId, String entityName, 
                                                 String fieldName) {
        return FieldPermission.builder()
                .roleId(roleId)
                .entityName(entityName)
                .fieldName(fieldName)
                .readable(true)
                .editable(false)
                .build();
    }
    
    /**
     * Create a read-write permission
     * 
     * @param roleId Role ID
     * @param entityName Entity name
     * @param fieldName Field name
     * @return New FieldPermission (readable=true, editable=true)
     */
    public static FieldPermission createReadWrite(String roleId, String entityName, 
                                                  String fieldName) {
        return FieldPermission.builder()
                .roleId(roleId)
                .entityName(entityName)
                .fieldName(fieldName)
                .readable(true)
                .editable(true)
                .build();
    }
    
    /**
     * Update the updatedAt timestamp
     */
    public void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}
