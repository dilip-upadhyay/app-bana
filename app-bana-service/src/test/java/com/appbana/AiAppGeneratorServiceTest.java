package com.appbana;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class AiAppGeneratorServiceTest {
    @Test
    void testSanitizeAiJson_withTripleBackticks() {
        String raw = "```json\n{ \"action\": \"listApps\", \"options\": {} }\n```";
        String s = AiAppGeneratorService.sanitizeAiJson(raw);
        assertEquals("{ \"action\": \"listApps\", \"options\": {} }", s);
    }

    @Test
    void testSanitizeAiJson_withExtraText() {
        String raw = "I think you should do this:\n{\"action\":\"loadApp\",\"options\":{\"appId\":\"my-app\"}}\nThanks";
        String s = AiAppGeneratorService.sanitizeAiJson(raw);
        assertEquals("{\"action\":\"loadApp\",\"options\":{\"appId\":\"my-app\"}}", s);
    }

    @Test
    void testSanitizeAiJson_plainJson() {
        String raw = "{\"action\":\"deleteApp\",\"options\":{\"appId\":\"old\"}}";
        String s = AiAppGeneratorService.sanitizeAiJson(raw);
        assertEquals(raw, s);
    }

    @Test
    void testParseAiResponse_withReply() throws Exception {
        String json = """
                {
                    "reply": "Hello! I built your app.",
                    "appName": "Test App",
                    "entities": [],
                    "pages": []
                }
                """;
        AiAppGeneratorService.GenerationResult result = AiAppGeneratorService.parseAiResponse(json);
        assertTrue(result.success);
        assertEquals("Test App", result.appName);
        assertNotNull(result.payload);
        assertEquals("Hello! I built your app.", result.payload.get("reply"));
    }
}
