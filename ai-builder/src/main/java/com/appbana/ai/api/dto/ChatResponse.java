package com.appbana.ai.api.dto;

import lombok.Data;
import java.util.List;

/**
 * Response DTO for AI chat
 */
@Data
public class ChatResponse {
    private String message;
    private String intent;
    private List<String> suggestions;
    private String conversationId;
}
