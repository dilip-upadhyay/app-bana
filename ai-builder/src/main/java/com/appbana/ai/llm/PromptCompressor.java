package com.appbana.ai.llm;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PromptCompressor - Reduce Token Usage via Smart Prompt Optimization
 * 
 * Compression techniques:
 * 1. Remove redundant whitespace and formatting
 * 2. Truncate verbose tool result payloads
 * 3. Summarize long conversation history
 * 4. Remove duplicate information
 * 5. Use abbreviated representations for structured data
 * 
 * Cost Optimization: Can reduce token usage by 20-40%
 */
@Slf4j
public class PromptCompressor {

    // Maximum sizes for various content types
    private static final int MAX_TOOL_RESULT_LENGTH = 2000;    // Truncate tool results
    private static final int MAX_HISTORY_STEPS = 3;             // Keep last N steps
    private static final int MAX_ENTITY_PREVIEW_FIELDS = 5;     // Summarize large entities
    private static final int MAX_JSON_DEPTH = 2;                // Flatten deep JSON

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern BLANK_LINES_PATTERN = Pattern.compile("\n{3,}");
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```\\w*\\n([\\s\\S]*?)```");

    /**
     * Compress a prompt to reduce token usage
     * 
     * @param prompt Original prompt
     * @return Compressed prompt
     */
    public static String compress(String prompt) {
        if (prompt == null || prompt.isEmpty()) {
            return prompt;
        }

        String compressed = prompt;
        int originalLength = prompt.length();

        // 1. Normalize whitespace
        compressed = normalizeWhitespace(compressed);

        // 2. Truncate large JSON blocks
        compressed = truncateLargeJsonBlocks(compressed);

        // 3. Remove redundant blank lines
        compressed = BLANK_LINES_PATTERN.matcher(compressed).replaceAll("\n\n");

        int compressedLength = compressed.length();
        double reduction = (1.0 - (double) compressedLength / originalLength) * 100;

        if (reduction > 5) {
            log.debug("[PromptCompressor] Reduced prompt from {} to {} chars ({:.1f}% reduction)",
                    originalLength, compressedLength, reduction);
        }

        return compressed;
    }

    /**
     * Compress tool results to reduce token usage
     * 
     * @param toolResults List of tool results
     * @return Compressed representation
     */
    public static String compressToolResults(List<?> toolResults) {
        if (toolResults == null || toolResults.isEmpty()) {
            return "[]";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[");

        for (int i = 0; i < toolResults.size(); i++) {
            Object result = toolResults.get(i);
            String compressed = compressToolResult(result);
            sb.append(compressed);
            if (i < toolResults.size() - 1) {
                sb.append(",");
            }
        }

        sb.append("]");
        return sb.toString();
    }

    /**
     * Compress a single tool result
     */
    @SuppressWarnings("unchecked")
    private static String compressToolResult(Object result) {
        if (result == null) {
            return "null";
        }

        String resultStr = result.toString();

        // If already short, return as-is
        if (resultStr.length() <= MAX_TOOL_RESULT_LENGTH) {
            return resultStr;
        }

        // Try to extract key information
        if (result instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) result;
            return compressMap(map);
        }

        // Truncate with indicator
        return resultStr.substring(0, MAX_TOOL_RESULT_LENGTH) + "...[truncated]";
    }

    /**
     * Compress a map by keeping only essential keys
     */
    private static String compressMap(Map<String, Object> map) {
        // Priority keys to keep
        Set<String> essentialKeys = Set.of(
                "id", "name", "displayName", "type", "status",
                "error", "message", "success", "count", "appId"
        );

        Map<String, Object> compressed = new LinkedHashMap<>();

        // Add essential keys first
        for (String key : essentialKeys) {
            if (map.containsKey(key)) {
                Object value = map.get(key);
                compressed.put(key, abbreviateValue(value));
            }
        }

        // Add remaining keys up to a limit
        int remaining = 10 - compressed.size();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (remaining <= 0) break;
            if (!essentialKeys.contains(entry.getKey())) {
                compressed.put(entry.getKey(), abbreviateValue(entry.getValue()));
                remaining--;
            }
        }

        if (compressed.size() < map.size()) {
            compressed.put("_omitted", map.size() - compressed.size());
        }

        return compressed.toString();
    }

    /**
     * Abbreviate a value if it's too long
     */
    @SuppressWarnings("unchecked")
    private static Object abbreviateValue(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof String) {
            String str = (String) value;
            if (str.length() > 200) {
                return str.substring(0, 197) + "...";
            }
            return str;
        }

        if (value instanceof List) {
            List<?> list = (List<?>) value;
            if (list.size() > 5) {
                return String.format("[%d items]", list.size());
            }
            return list;
        }

        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            if (map.size() > 5) {
                return String.format("{%d keys}", map.size());
            }
            return map;
        }

        return value;
    }

    /**
     * Normalize whitespace in a string
     */
    private static String normalizeWhitespace(String text) {
        // Preserve single newlines but collapse multiple spaces
        String[] lines = text.split("\n");
        StringBuilder result = new StringBuilder();

        for (String line : lines) {
            // Collapse multiple spaces to single space
            String normalized = WHITESPACE_PATTERN.matcher(line.trim()).replaceAll(" ");
            result.append(normalized).append("\n");
        }

        return result.toString().trim();
    }

    /**
     * Truncate large JSON blocks embedded in text
     */
    private static String truncateLargeJsonBlocks(String text) {
        // Find code blocks and truncate if too large
        Matcher matcher = CODE_BLOCK_PATTERN.matcher(text);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String codeContent = matcher.group(1);
            if (codeContent.length() > 1000) {
                // Truncate but keep structure indicators
                String truncated = codeContent.substring(0, 800) + "\n...[content truncated]...";
                matcher.appendReplacement(result, "```\n" + Matcher.quoteReplacement(truncated) + "\n```");
            }
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Create a compressed entity summary for prompts
     * 
     * @param entities List of entity metadata
     * @return Compressed summary string
     */
    public static String compressEntities(List<Map<String, Object>> entities) {
        if (entities == null || entities.isEmpty()) {
            return "No entities";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(entities.size()).append(" entities: ");

        for (int i = 0; i < entities.size(); i++) {
            Map<String, Object> entity = entities.get(i);
            String name = (String) entity.getOrDefault("name", "?");
            
            @SuppressWarnings("unchecked")
            List<?> fields = (List<?>) entity.get("fields");
            int fieldCount = fields != null ? fields.size() : 0;

            sb.append(name).append("(").append(fieldCount).append(" fields)");
            
            if (i < entities.size() - 1) {
                sb.append(", ");
            }
        }

        return sb.toString();
    }

    /**
     * Compress agent execution history for continuation prompts
     * 
     * @param history Full execution history
     * @return Compressed history keeping only recent steps
     */
    public static List<?> compressHistory(List<?> history) {
        if (history == null || history.size() <= MAX_HISTORY_STEPS) {
            return history;
        }

        // Keep first step (original context) and last N steps (recent context)
        List<Object> compressed = new ArrayList<>();
        
        // Add first step
        compressed.add(history.get(0));
        
        // Add summary marker
        int skipped = history.size() - MAX_HISTORY_STEPS - 1;
        compressed.add(Map.of("_summary", String.format("[%d steps omitted for brevity]", skipped)));
        
        // Add last N steps
        for (int i = history.size() - MAX_HISTORY_STEPS; i < history.size(); i++) {
            compressed.add(history.get(i));
        }

        log.debug("[PromptCompressor] Compressed history from {} to {} steps", history.size(), compressed.size());
        return compressed;
    }

    /**
     * Estimate token count for a string (rough approximation)
     * OpenAI uses ~4 characters per token on average
     */
    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return text.length() / 4;
    }

    /**
     * Check if prompt needs compression based on token estimate
     */
    public static boolean needsCompression(String prompt, int maxTokens) {
        int estimated = estimateTokens(prompt);
        return estimated > maxTokens;
    }

    /**
     * Get compression statistics
     */
    public record CompressionStats(
            int originalLength,
            int compressedLength,
            double reductionPercent,
            int estimatedTokensSaved
    ) {}

    /**
     * Compress with statistics
     */
    public static CompressionResult compressWithStats(String prompt) {
        int originalLength = prompt.length();
        String compressed = compress(prompt);
        int compressedLength = compressed.length();
        
        double reductionPercent = (1.0 - (double) compressedLength / originalLength) * 100;
        int tokensSaved = (originalLength - compressedLength) / 4;

        return new CompressionResult(
                compressed,
                new CompressionStats(originalLength, compressedLength, reductionPercent, tokensSaved)
        );
    }

    public record CompressionResult(String compressed, CompressionStats stats) {}
}
