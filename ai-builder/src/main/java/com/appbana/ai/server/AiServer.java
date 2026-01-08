package com.appbana.ai.server;

import com.appbana.ai.config.AiConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * Embedded Tomcat server for AI Builder Service
 */
public class AiServer {
    private static final Logger log = LoggerFactory.getLogger(AiServer.class);
    private final AiConfig config;
    private final Tomcat tomcat;
    private final ObjectMapper objectMapper;
    
    public AiServer(AiConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        
        // Initialize Tomcat
        this.tomcat = new Tomcat();
        this.tomcat.setPort(config.getPort());
        this.tomcat.getConnector();
        
        // Create context
        Context ctx = tomcat.addContext("", new File(".").getAbsolutePath());
        
        // Register servlets
        registerServlets(ctx);
    }
    
    private void registerServlets(Context ctx) {
        // Health check endpoint
        Tomcat.addServlet(ctx, "health", new HealthServlet());
        ctx.addServletMappingDecoded("/health", "health");
        
        // AI Chat endpoint (placeholder for now)
        Tomcat.addServlet(ctx, "chat", new ChatServlet(objectMapper));
        ctx.addServletMappingDecoded("/api/ai/chat", "chat");
        
        log.info("Registered servlets: /health, /api/ai/chat");
    }
    
    public void start() throws LifecycleException {
        tomcat.start();
        log.info("Server started on port {}", config.getPort());
    }
    
    public void stop() {
        try {
            tomcat.stop();
            tomcat.destroy();
        } catch (LifecycleException e) {
            log.error("Error stopping server", e);
        }
    }
    
    /**
     * Health check servlet
     */
    static class HealthServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.setContentType("application/json");
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getWriter().write("{\"status\":\"UP\",\"service\":\"ai-builder\"}");
        }
    }
    
    /**
     * Chat servlet (placeholder)
     */
    static class ChatServlet extends HttpServlet {
        private final ObjectMapper objectMapper;
        
        ChatServlet(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }
        
        @Override
        protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.setContentType("application/json");
            resp.setStatus(HttpServletResponse.SC_OK);
            
            // Placeholder response
            String response = """
                {
                    "message": "AI Builder Service is running! Implementation coming soon...",
                    "state": "INITIAL",
                    "timestamp": "%s"
                }
                """.formatted(java.time.Instant.now());
            
            resp.getWriter().write(response);
        }
    }
}
