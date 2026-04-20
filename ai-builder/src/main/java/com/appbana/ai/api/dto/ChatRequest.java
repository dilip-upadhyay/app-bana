package com.appbana.ai.api.dto;

import lombok.Data;

/**
 * Request DTO for AI chat
 * Updated: Story 8.5 - Added tenantId and appId for agent context
 */
@Data
public class ChatRequest {
    private String message;
    private String sessionId;
    private String userId;
    private String appType; // Optional: for context
    private String tenantId; // Story 8.5: For agent context
    private String appId; // Story 8.5: For agent context
    private String token; // Story 8.5: For authentication
    private String provider; // 'openai' or 'gemini'
    private java.util.List<String> images; // Base64 encoded images for Ai Understanding
}
