package com.appbana.model;

/**
 * Context holder for tenant and application isolation.
 * 
 * Used to ensure all entity operations are scoped to a specific
 * tenant and application, preventing data leakage between different
 * tenants or applications.
 * 
 * Usage:
 * <pre>
 * TenantContext context = new TenantContext("tenant-123", "app-hr");
 * crud.insertRecord(context, schema, data);
 * </pre>
 */
public class TenantContext {
    private final String tenantId;
    private final String appId;
    
    /**
     * Default tenant for single-tenant deployments
     */
    public static final String DEFAULT_TENANT = "default";
    
    /**
     * Create a tenant context
     * 
     * @param tenantId Tenant identifier (company/organization)
     * @param appId Application identifier within the tenant
     */
    public TenantContext(String tenantId, String appId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId cannot be null or empty");
        }
        if (appId == null || appId.isBlank()) {
            throw new IllegalArgumentException("appId cannot be null or empty");
        }
        this.tenantId = tenantId;
        this.appId = appId;
    }
    
    /**
     * Create default tenant context (for single-tenant mode)
     * 
     * @param appId Application identifier
     * @return TenantContext with default tenant
     */
    public static TenantContext forApp(String appId) {
        return new TenantContext(DEFAULT_TENANT, appId);
    }
    
    public String getTenantId() {
        return tenantId;
    }
    
    public String getAppId() {
        return appId;
    }
    
    @Override
    public String toString() {
        return "TenantContext{tenantId='" + tenantId + "', appId='" + appId + "'}";
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TenantContext that = (TenantContext) o;
        return tenantId.equals(that.tenantId) && appId.equals(that.appId);
    }
    
    @Override
    public int hashCode() {
        return 31 * tenantId.hashCode() + appId.hashCode();
    }
}
