package com.appbana.ai.api.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for app patterns
 */
@Data
public class PatternResponse {
    private String id;
    private String patternName;
    private String appType;
    private List<Map<String, Object>> entities;
    private List<Map<String, Object>> pages;
    private int usageCount;
    private double successRate;
}
