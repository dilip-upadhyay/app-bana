package com.appbana.ai.api.dto;

import lombok.Data;

/**
 * Request DTO for AI chat
 */
@Data
public class ChatRequest {
    private String message;
    private String sessionId;
    private String userId;
    private String appType; // Optional: for context
}
