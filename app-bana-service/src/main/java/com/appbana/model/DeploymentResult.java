package com.appbana.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;

/**
 * Result of an app deployment operation.
 * Contains version info, created tables, and timing data.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeploymentResult {
    private boolean success;
    private Long versionId;
    private Integer version;
    private String appId;
    private String tenantId;
    private AppVersion.Environment environment;
    
    // Deployment details
    private List<String> tablesCreated;
    private Long durationMs;
    
    // Error info (if failed)
    private String errorMessage;
    private String errorDetails;
    
    /**
     * Create a successful deployment result
     */
    public static DeploymentResult success(AppVersion appVersion) {
        return DeploymentResult.builder()
                .success(true)
                .versionId(appVersion.getId())
                .version(appVersion.getVersion())
                .appId(appVersion.getAppId())
                .tenantId(appVersion.getTenantId())
                .environment(appVersion.getEnvironment())
                .tablesCreated(appVersion.getTablesCreated())
                .durationMs(appVersion.getDurationMs())
                .build();
    }
    
    /**
     * Create a failed deployment result
     */
    public static DeploymentResult failure(String appId, String tenantId, 
                                          AppVersion.Environment environment,
                                          String errorMessage, String errorDetails) {
        return DeploymentResult.builder()
                .success(false)
                .appId(appId)
                .tenantId(tenantId)
                .environment(environment)
                .errorMessage(errorMessage)
                .errorDetails(errorDetails)
                .build();
    }
    
    /**
     * Get a human-readable summary
     */
    public String getSummary() {
        if (success) {
            return String.format("Successfully deployed %s v%d to %s in %dms (%d tables created)",
                    appId, version, environment, durationMs, 
                    tablesCreated != null ? tablesCreated.size() : 0);
        } else {
            return String.format("Failed to deploy %s to %s: %s",
                    appId, environment, errorMessage);
        }
    }
}
