package com.appbana.ai;

import com.appbana.config.ConfigManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class DataSeederService {
    private static final Logger LOG = LoggerFactory.getLogger(DataSeederService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
            You are a Data Generation Expert.
            Generate realistic JSON data for the provided entity schema.

            RULES:
            1. Return ONLY a JSON Array of objects.
            2. Do not include markdown formatting (like ```json).
            3. Make the data realistic and varied.
            """;

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> generateData(String entityName, String schemaJson, int count) {
        var config = ConfigManager.getConfig();
        if (!AiProviderFactory.isAiEnabled(config)) {
            throw new RuntimeException("AI is not enabled");
        }

        try {
            LOG.info("[DataSeeder] Generating {} items for {}", count, entityName);
            AiProvider provider = AiProviderFactory.createProvider(config);

            String userPrompt = String.format(
                    "Entity: %s\nSchema: %s\nCount: %d\n\nGenerate realistic sample data.",
                    entityName, schemaJson, count);

            String jsonResponse = provider.generateAppStructure(userPrompt, SYSTEM_PROMPT);

            // Clean up
            jsonResponse = cleanJson(jsonResponse);

            List<Map<String, Object>> data = MAPPER.readValue(jsonResponse, List.class);
            return data;

        } catch (Exception e) {
            LOG.error("Seeding failed", e);
            throw new RuntimeException("Failed to generate data: " + e.getMessage());
        }
    }

    private static String cleanJson(String text) {
        text = text.trim();
        if (text.startsWith("```json")) {
            text = text.substring(7);
        }
        if (text.startsWith("```")) {
            text = text.substring(3);
        }
        if (text.endsWith("```")) {
            text = text.substring(0, text.length() - 3);
        }
        return text.trim();
    }
}
