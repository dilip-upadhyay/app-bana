package com.appbana.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.Instant;
import java.util.List;

/**
 * Domain model representing a versioned deployment of an application.
 * Each deployment creates a new version record with complete snapshot.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppVersion {
    private Long id;
    private String appId;
    private String tenantId;
    private Integer version;
    private Environment environment;
    private DeploymentStatus status;
    
    // Full app snapshot as JSON string (will be stored as JSONB in PostgreSQL)
    private String appSnapshot;
    
    // Deployment metadata
    private List<String> tablesCreated;
    private String deployedBy;
    private Instant deployedAt;
    private Long durationMs;
    
    // Error tracking
    private String errorMessage;
    private String errorStackTrace;
    
    // Audit
    private String notes;
    
    /**
     * Environment types for deployment targets
     */
    public enum Environment {
        DEV,    // Development
        SIT,    // System Integration Testing (Staging)
        PROD    // Production
    }
    
    /**
     * Deployment status outcomes
     */
    public enum DeploymentStatus {
        SUCCESS,        // All tables created successfully
        FAILED,         // Deployment failed (transaction rolled back)
        ROLLED_BACK     // Manually rolled back after initial success
    }
    
    /**
     * Check if deployment was successful
     */
    public boolean isSuccessful() {
        return status == DeploymentStatus.SUCCESS;
    }
    
    /**
     * Check if this is a production deployment
     */
    public boolean isProduction() {
        return environment == Environment.PROD;
    }
}
