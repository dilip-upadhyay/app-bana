package com.appbana.ai.api.dto;

import lombok.Data;

/**
 * Request DTO for feedback submission
 */
@Data
public class FeedbackRequest {
    private String conversationId;
    private String userId;
    private int rating; // -1 (thumbs down), 0 (neutral), 1 (thumbs up)
    private String comment;
    private String feedbackType; // "thumbs_up", "thumbs_down", "correction", etc.
}
