package com.appbana.ai;

import com.appbana.ai.AiProvider;
import com.appbana.ai.AiProviderFactory;
import com.appbana.config.AppConfig;
import com.appbana.config.ConfigManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Service to generate UI themes using AI.
 * Uses the configured AI Provider to translate natural language descriptions
 * into CSS variable sets.
 */
public class AiThemeService {

    private static final Logger LOG = LoggerFactory.getLogger(AiThemeService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
            You are a UI Design Expert. Your task is to generate a comprehensive color palette and theme configuration for a web application based on a user's description.

            You must return ONLY a valid JSON object. Do not include markdown formatting, explanations, or code blocks.

            The JSON structure must be exactly as follows:
            {
              "colors": {
                "brand": "#hex",         // Primary brand color
                "brandAccent": "#hex",   // Darker/Rich variant for hover
                "brandMuted": "#hex",    // Light/Subtle variant for backgrounds
                "bg": "#hex",            // Main page background
                "surface": "#hex",       // Card/Container background
                "surfaceAlt": "#hex",    // Alternating/Hover background
                "text": "#hex",          // Main text color
                "textSecondary": "#hex", // Muted text color
                "border": "#hex",        // Regular border
                "borderStrong": "#hex"   // Stronger border
              },
              "radius": {
                "sm": "4px",
                "md": "8px",
                "lg": "12px"
              },
              "font": "Inter, sans-serif" // Font stack
            }

            Key Rules:
            1. Ensure high contrast and accessibility.
            2. If the user asks for "Dark Mode", ensure 'bg' is dark and 'text' is light.
            3. "brand" should be the main accent color.
            4. Return ONLY the JSON object.
            """;

    public static Map<String, Object> generateTheme(String description) {
        AppConfig config = ConfigManager.getConfig();
        if (!AiProviderFactory.isAiEnabled(config)) {
            throw new RuntimeException("AI is not enabled in configuration");
        }

        try {
            LOG.info("[ThemeAI] Generating theme for: {}", description);
            AiProvider provider = AiProviderFactory.createProvider(config);

            String jsonResponse = provider.generateAppStructure(description, SYSTEM_PROMPT);

            // Cleanup markdown if present (```json ... ```)
            jsonResponse = cleanJson(jsonResponse);

            @SuppressWarnings("unchecked")
            Map<String, Object> theme = MAPPER.readValue(jsonResponse, Map.class);
            return theme;

        } catch (Exception e) {
            LOG.error("[ThemeAI] Generation failed", e);
            throw new RuntimeException("Failed to generate theme: " + e.getMessage());
        }
    }

    private static String cleanJson(String input) {
        if (input == null)
            return "{}";
        String cleaned = input.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        }
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        return cleaned.trim();
    }
}
