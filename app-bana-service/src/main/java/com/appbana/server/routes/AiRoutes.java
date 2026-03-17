package com.appbana.server.routes;

import com.appbana.api.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AI Builder routes
 * Registers AI chat and agent endpoints using the Router pattern
 */
public class AiRoutes {
    private static final Logger LOG = LoggerFactory.getLogger(AiRoutes.class);

    public static void register(Router router) {
        LOG.info("Registering AI routes");

        // TODO: Initialize AiChatController with dependencies
        // For now, these are placeholder routes

        // Regular chat endpoint
        router.post("/api/ai/chat", (req, res) -> {
            res.json(200, java.util.Map.of(
                    "message", "AI Builder integration in progress",
                    "status", "placeholder"));
        });

        // Agent-based chat endpoint
        router.post("/api/ai/chat/agent", (req, res) -> {
            res.json(200, java.util.Map.of(
                    "message", "Agent endpoint - integration in progress",
                    "status", "placeholder"));
        });

        LOG.info("AI routes registered: /api/ai/chat, /api/ai/chat/agent");
    }
}
