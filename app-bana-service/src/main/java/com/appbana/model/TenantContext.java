package com.appbana.model;

import java.util.function.Supplier;

/**
 * Context holder for tenant and application isolation.
 * 
 * Used to ensure all entity operations are scoped to a specific
 * tenant and application, preventing data leakage between different
 * tenants or applications.
 * 
 * Supports ThreadLocal storage for implicit context propagation through
 * the call stack without passing parameters everywhere.
 * 
 * Usage:
 * <pre>
 * // Explicit context passing
 * TenantContext context = new TenantContext("tenant-123", "app-hr");
 * crud.insertRecord(context, schema, data);
 * 
 * // ThreadLocal context propagation
 * TenantContext.set(context);
 * try {
 *     // Context automatically available in nested calls
 *     crud.insertRecord(TenantContext.get(), schema, data);
 * } finally {
 *     TenantContext.clear(); // Always cleanup
 * }
 * 
 * // Auto-cleanup with runWithContext
 * TenantContext.runWithContext(context, () -> {
 *     crud.insertRecord(TenantContext.get(), schema, data);
 *     return null;
 * });
 * </pre>
 */
public class TenantContext {
    private static final ThreadLocal<TenantContext> CONTEXT = new ThreadLocal<>();
    
    private final String tenantId;
    private final String appId;
    private final String userId;      // Optional: user making the request
    private final String requestId;   // Optional: for request tracing
    
    /**
     * Default tenant for single-tenant deployments
     */
    public static final String DEFAULT_TENANT = "default";
    
    /**
     * Create a tenant context with minimal fields
     * 
     * @param tenantId Tenant identifier (company/organization)
     * @param appId Application identifier within the tenant
     */
    public TenantContext(String tenantId, String appId) {
        this(tenantId, appId, null, null);
    }
    
    /**
     * Create a tenant context with all fields
     * 
     * @param tenantId Tenant identifier (company/organization)
     * @param appId Application identifier within the tenant
     * @param userId User identifier (optional)
     * @param requestId Request trace ID (optional)
     */
    public TenantContext(String tenantId, String appId, String userId, String requestId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId cannot be null or empty");
        }
        if (appId == null || appId.isBlank()) {
            throw new IllegalArgumentException("appId cannot be null or empty");
        }
        this.tenantId = tenantId;
        this.appId = appId;
        this.userId = userId;
        this.requestId = requestId;
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
    
    // Getters
    public String getTenantId() {
        return tenantId;
    }
    
    public String getAppId() {
        return appId;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public String getRequestId() {
        return requestId;
    }
    
    /**
     * Create a copy with updated userId
     * 
     * @param userId New user ID
     * @return New TenantContext instance
     */
    public TenantContext withUserId(String userId) {
        return new TenantContext(this.tenantId, this.appId, userId, this.requestId);
    }
    
    /**
     * Create a copy with updated requestId
     * 
     * @param requestId New request ID
     * @return New TenantContext instance
     */
    public TenantContext withRequestId(String requestId) {
        return new TenantContext(this.tenantId, this.appId, this.userId, requestId);
    }
    
    // ThreadLocal operations
    
    /**
     * Set context in ThreadLocal for current thread
     * 
     * @param context TenantContext to set
     */
    public static void set(TenantContext context) {
        CONTEXT.set(context);
    }
    
    /**
     * Get context from ThreadLocal
     * 
     * @return Current TenantContext
     * @throws IllegalStateException if context not set
     */
    public static TenantContext get() {
        TenantContext context = CONTEXT.get();
        if (context == null) {
            throw new IllegalStateException("TenantContext not set in current thread");
        }
        return context;
    }
    
    /**
     * Get context from ThreadLocal (null-safe)
     * 
     * @return Current TenantContext or null if not set
     */
    public static TenantContext getOrNull() {
        return CONTEXT.get();
    }
    
    /**
     * Check if context is set in current thread
     * 
     * @return true if context is set
     */
    public static boolean isSet() {
        return CONTEXT.get() != null;
    }
    
    /**
     * Clear context from ThreadLocal
     * 
     * IMPORTANT: Always call this in finally block to prevent memory leaks
     */
    public static void clear() {
        CONTEXT.remove();
    }
    
    /**
     * Execute code with context and auto-cleanup
     * 
     * @param context TenantContext to use
     * @param supplier Code to execute
     * @param <T> Return type
     * @return Result from supplier
     */
    public static <T> T runWithContext(TenantContext context, Supplier<T> supplier) {
        TenantContext previous = CONTEXT.get();
        try {
            CONTEXT.set(context);
            return supplier.get();
        } finally {
            if (previous != null) {
                CONTEXT.set(previous);
            } else {
                CONTEXT.remove();
            }
        }
    }
    
    @Override
    public String toString() {
        return "TenantContext{" +
               "tenantId='" + tenantId + '\'' +
               ", appId='" + appId + '\'' +
               (userId != null ? ", userId='" + userId + '\'' : "") +
               (requestId != null ? ", requestId='" + requestId + '\'' : "") +
               '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TenantContext that = (TenantContext) o;
        return tenantId.equals(that.tenantId) && 
               appId.equals(that.appId) &&
               (userId == null ? that.userId == null : userId.equals(that.userId)) &&
               (requestId == null ? that.requestId == null : requestId.equals(that.requestId));
    }
    
    @Override
    public int hashCode() {
        int result = 31 * tenantId.hashCode() + appId.hashCode();
        if (userId != null) {
            result = 31 * result + userId.hashCode();
        }
        if (requestId != null) {
            result = 31 * result + requestId.hashCode();
        }
        return result;
    }
}
