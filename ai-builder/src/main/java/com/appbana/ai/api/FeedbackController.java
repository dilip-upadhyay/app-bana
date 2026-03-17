package com.appbana.ai.api;

import com.appbana.ai.api.dto.FeedbackRequest;
import com.appbana.ai.learning.FeedbackLoop;
import com.appbana.ai.api.Router;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Feedback controller using plain Java Router pattern
 * Story: 5.4 - Feedback UI (Backend API)
 */
@Slf4j
public class FeedbackController {

    private final FeedbackLoop feedbackLoop;

    public FeedbackController(FeedbackLoop feedbackLoop) {
        this.feedbackLoop = feedbackLoop;
    }

    public BiConsumer<Router.HttpRequest, Router.HttpResponse> submitFeedback() {
        return (req, res) -> {
            try {
                FeedbackRequest request = req.readJson(new TypeReference<FeedbackRequest>() {
                });

                log.info("Feedback from user: {}, type: {}",
                        request.getUserId(), request.getFeedbackType());

                feedbackLoop.recordFeedback(
                        request.getUserId(),
                        request.getConversationId(),
                        request.getFeedbackType(),
                        request.getRating(),
                        request.getComment());

                res.json(200, Map.of("success", true));

            } catch (Exception e) {
                log.error("Error recording feedback", e);
                res.json(500, Map.of("error", "Failed to record feedback"));
            }
        };
    }
}
