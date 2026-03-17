package com.appbana.ai.api;

import com.appbana.ai.api.dto.PatternResponse;
import com.appbana.ai.learning.PatternMiner;
import com.appbana.ai.api.Router;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Pattern controller using plain Java Router pattern
 * Story: 5.3 - Template Previews (Backend API)
 */
@Slf4j
public class PatternController {

    private final PatternMiner patternMiner;

    public PatternController(PatternMiner patternMiner) {
        this.patternMiner = patternMiner;
    }

    public BiConsumer<Router.HttpRequest, Router.HttpResponse> getPatterns() {
        return (req, res) -> {
            try {
                String appType = req.query("appType");
                int limit = Integer.parseInt(req.query().getOrDefault("limit", "10"));

                log.info("Fetching patterns: appType={}, limit={}", appType, limit);

                List<PatternMiner.AppPattern> patterns = patternMiner.getTopPatterns(limit);

                // Filter by app type if provided
                if (appType != null && !appType.isEmpty()) {
                    patterns = patterns.stream()
                            .filter(p -> appType.equals(p.getAppType()))
                            .collect(Collectors.toList());
                }

                // Convert to response DTOs
                List<PatternResponse> response = patterns.stream()
                        .map(this::toResponse)
                        .collect(Collectors.toList());

                res.json(200, response);

            } catch (Exception e) {
                log.error("Error fetching patterns", e);
                res.json(500, Map.of("error", "Failed to fetch patterns"));
            }
        };
    }

    private PatternResponse toResponse(PatternMiner.AppPattern pattern) {
        PatternResponse response = new PatternResponse();
        response.setId(pattern.getId());
        response.setPatternName(pattern.getPatternName());
        response.setAppType(pattern.getAppType());
        response.setEntities(pattern.getEntities());
        response.setPages(pattern.getPages());
        response.setUsageCount(pattern.getUsageCount());
        response.setSuccessRate(pattern.getSuccessRate());
        return response;
    }
}
