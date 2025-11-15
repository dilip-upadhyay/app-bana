package com.appbana.ai;

import java.util.*;

/**
 * AgentMemoryService - Stores conversation history, user preferences, and feedback for learning/evolving agent.
 * For demo: in-memory map per user/session. Can be extended to persistent storage.
 */
public class AgentMemoryService {
    private static final Map<String, List<MemoryEntry>> memory = new HashMap<>();
    private static final Map<String, Map<String, Object>> preferences = new HashMap<>();
    private static final Map<String, List<FeedbackEntry>> feedback = new HashMap<>();

    // Conversation history
    public static void record(String userId, String input, String response) {
        memory.computeIfAbsent(userId, k -> new ArrayList<>())
              .add(new MemoryEntry(System.currentTimeMillis(), input, response));
    }
    public static List<MemoryEntry> getHistory(String userId) {
        return memory.getOrDefault(userId, Collections.emptyList());
    }
    public static void clearHistory(String userId) {
        memory.remove(userId);
    }

    // User preferences
    public static void setPreference(String userId, String key, Object value) {
        preferences.computeIfAbsent(userId, k -> new HashMap<>()).put(key, value);
    }
    public static Object getPreference(String userId, String key) {
        Map<String, Object> prefs = preferences.get(userId);
        return prefs != null ? prefs.get(key) : null;
    }
    public static Map<String, Object> getAllPreferences(String userId) {
        return preferences.getOrDefault(userId, Collections.emptyMap());
    }

    // Feedback
    public static void recordFeedback(String userId, String input, String response, boolean positive, String comment) {
        feedback.computeIfAbsent(userId, k -> new ArrayList<>())
                .add(new FeedbackEntry(System.currentTimeMillis(), input, response, positive, comment));
    }
    public static List<FeedbackEntry> getFeedback(String userId) {
        return feedback.getOrDefault(userId, Collections.emptyList());
    }

    public static class MemoryEntry {
        public long timestamp;
        public String input;
        public String response;
        public MemoryEntry(long timestamp, String input, String response) {
            this.timestamp = timestamp;
            this.input = input;
            this.response = response;
        }
    }

    public static class FeedbackEntry {
        public long timestamp;
        public String input;
        public String response;
        public boolean positive;
        public String comment;
        public FeedbackEntry(long timestamp, String input, String response, boolean positive, String comment) {
            this.timestamp = timestamp;
            this.input = input;
            this.response = response;
            this.positive = positive;
            this.comment = comment;
        }
    }
}
