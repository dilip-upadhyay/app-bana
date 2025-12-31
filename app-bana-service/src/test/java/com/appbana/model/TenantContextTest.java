package com.appbana.model;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for TenantContext
 * 
 * Covers:
 * - Creation with minimal and full fields
 * - Validation (null/blank checks)
 * - ThreadLocal operations
 * - Immutable updates (withUserId, withRequestId)
 * - equals(), hashCode(), toString()
 * - Thread isolation
 */
public class TenantContextTest {
    
    @AfterEach
    void cleanup() {
        // Always clean up ThreadLocal after each test
        TenantContext.clear();
    }
    
    // === Creation Tests ===
    
    @Test
    @DisplayName("Should create context with minimal fields")
    void testCreateContextWithMinimalFields() {
        TenantContext context = new TenantContext("acme-corp", "hr-app");
        
        assertEquals("acme-corp", context.getTenantId());
        assertEquals("hr-app", context.getAppId());
        assertNull(context.getUserId());
        assertNull(context.getRequestId());
    }
    
    @Test
    @DisplayName("Should create context with all fields")
    void testCreateContextWithAllFields() {
        TenantContext context = new TenantContext("acme-corp", "hr-app", "user-123", "req-456");
        
        assertEquals("acme-corp", context.getTenantId());
        assertEquals("hr-app", context.getAppId());
        assertEquals("user-123", context.getUserId());
        assertEquals("req-456", context.getRequestId());
    }
    
    // === Validation Tests ===
    
    @Test
    @DisplayName("Should throw exception when tenant ID is null")
    void testTenantIdRequired() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new TenantContext(null, "hr-app")
        );
        
        assertTrue(exception.getMessage().contains("tenantId"));
    }
    
    @Test
    @DisplayName("Should throw exception when tenant ID is blank")
    void testTenantIdNotBlank() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new TenantContext("  ", "hr-app")
        );
        
        assertTrue(exception.getMessage().contains("tenantId"));
    }
    
    @Test
    @DisplayName("Should throw exception when app ID is null")
    void testAppIdRequired() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new TenantContext("acme-corp", null)
        );
        
        assertTrue(exception.getMessage().contains("appId"));
    }
    
    @Test
    @DisplayName("Should throw exception when app ID is blank")
    void testAppIdNotBlank() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new TenantContext("acme-corp", "  ")
        );
        
        assertTrue(exception.getMessage().contains("appId"));
    }
    
    // === ThreadLocal Operations Tests ===
    
    @Test
    @DisplayName("Should set and get context from ThreadLocal")
    void testThreadLocalSetAndGet() {
        TenantContext context = new TenantContext("acme-corp", "hr-app");
        
        TenantContext.set(context);
        TenantContext retrieved = TenantContext.get();
        
        assertEquals(context, retrieved);
        assertEquals("acme-corp", retrieved.getTenantId());
        assertEquals("hr-app", retrieved.getAppId());
    }
    
    @Test
    @DisplayName("Should throw exception when getting context without setting it")
    void testGetWithoutSet() {
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            TenantContext::get
        );
        
        assertTrue(exception.getMessage().contains("not set"));
    }
    
    @Test
    @DisplayName("Should return null when using getOrNull without setting context")
    void testGetOrNull() {
        assertNull(TenantContext.getOrNull());
    }
    
    @Test
    @DisplayName("Should return true when context is set")
    void testIsSet() {
        assertFalse(TenantContext.isSet());
        
        TenantContext.set(new TenantContext("acme-corp", "hr-app"));
        assertTrue(TenantContext.isSet());
    }
    
    @Test
    @DisplayName("Should clear context from ThreadLocal")
    void testClear() {
        TenantContext context = new TenantContext("acme-corp", "hr-app");
        TenantContext.set(context);
        
        assertTrue(TenantContext.isSet());
        
        TenantContext.clear();
        
        assertFalse(TenantContext.isSet());
        assertThrows(IllegalStateException.class, TenantContext::get);
    }
    
    @Test
    @DisplayName("Should run code with context and auto-clear")
    void testRunWithContext() {
        TenantContext context = new TenantContext("acme-corp", "hr-app");
        
        String result = TenantContext.runWithContext(context, () -> {
            TenantContext retrieved = TenantContext.get();
            assertEquals("acme-corp", retrieved.getTenantId());
            return "success";
        });
        
        assertEquals("success", result);
        assertFalse(TenantContext.isSet(), "Context should be cleared after execution");
    }
    
    @Test
    @DisplayName("Should clear context even when exception is thrown")
    void testRunWithContextClearsOnException() {
        TenantContext context = new TenantContext("acme-corp", "hr-app");
        
        assertThrows(RuntimeException.class, () -> {
            TenantContext.runWithContext(context, () -> {
                throw new RuntimeException("Test exception");
            });
        });
        
        assertFalse(TenantContext.isSet(), "Context should be cleared even after exception");
    }
    
    // === Immutable Update Tests ===
    
    @Test
    @DisplayName("Should create new context with different user ID")
    void testWithUserId() {
        TenantContext original = new TenantContext("acme-corp", "hr-app", "user-1", "req-1");
        TenantContext updated = original.withUserId("user-2");
        
        assertEquals("acme-corp", updated.getTenantId());
        assertEquals("hr-app", updated.getAppId());
        assertEquals("user-2", updated.getUserId());
        assertEquals("req-1", updated.getRequestId());
        
        // Original should be unchanged
        assertEquals("user-1", original.getUserId());
    }
    
    @Test
    @DisplayName("Should create new context with different request ID")
    void testWithRequestId() {
        TenantContext original = new TenantContext("acme-corp", "hr-app", "user-1", "req-1");
        TenantContext updated = original.withRequestId("req-2");
        
        assertEquals("acme-corp", updated.getTenantId());
        assertEquals("hr-app", updated.getAppId());
        assertEquals("user-1", updated.getUserId());
        assertEquals("req-2", updated.getRequestId());
        
        // Original should be unchanged
        assertEquals("req-1", original.getRequestId());
    }
    
    // === Object Method Tests ===
    
    @Test
    @DisplayName("Should generate correct string representation")
    void testToString() {
        TenantContext context = new TenantContext("acme-corp", "hr-app", "user-1", "req-1");
        String str = context.toString();
        
        assertTrue(str.contains("acme-corp"));
        assertTrue(str.contains("hr-app"));
        assertTrue(str.contains("user-1"));
        assertTrue(str.contains("req-1"));
    }
    
    @Test
    @DisplayName("Should correctly compare contexts for equality")
    void testEquals() {
        TenantContext context1 = new TenantContext("acme-corp", "hr-app", "user-1", "req-1");
        TenantContext context2 = new TenantContext("acme-corp", "hr-app", "user-1", "req-1");
        TenantContext context3 = new TenantContext("acme-corp", "hr-app", "user-2", "req-1");
        
        assertEquals(context1, context2);
        assertNotEquals(context1, context3);
        assertNotEquals(context1, null);
        assertNotEquals(context1, "not a context");
    }
    
    @Test
    @DisplayName("Should generate consistent hash codes")
    void testHashCode() {
        TenantContext context1 = new TenantContext("acme-corp", "hr-app", "user-1", "req-1");
        TenantContext context2 = new TenantContext("acme-corp", "hr-app", "user-1", "req-1");
        
        assertEquals(context1.hashCode(), context2.hashCode());
    }
    
    // === Thread Isolation Test ===
    
    @Test
    @DisplayName("Should isolate context between threads")
    void testThreadIsolation() throws InterruptedException {
        TenantContext mainContext = new TenantContext("main-tenant", "main-app");
        TenantContext.set(mainContext);
        
        Thread otherThread = new Thread(() -> {
            // Other thread should not see main thread's context
            assertFalse(TenantContext.isSet());
            
            // Set different context in other thread
            TenantContext otherContext = new TenantContext("other-tenant", "other-app");
            TenantContext.set(otherContext);
            
            assertEquals("other-tenant", TenantContext.get().getTenantId());
        });
        
        otherThread.start();
        otherThread.join();
        
        // Main thread's context should still be intact
        assertEquals("main-tenant", TenantContext.get().getTenantId());
    }
}
