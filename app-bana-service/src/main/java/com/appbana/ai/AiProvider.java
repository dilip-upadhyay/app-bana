package com.appbana.ai;

/**
 * Interface for AI providers (OpenAI, Anthropic, Ollama, etc.)
 * Allows switching between different AI services while maintaining consistent API
 */
public interface AiProvider {
    
    /**
     * Generate app metadata from natural language description
     * 
     * @param userPrompt The user's app description (e.g., "Create a blog app with posts and comments")
     * @param systemPrompt System instructions explaining AppBana capabilities
     * @return JSON string with app structure: entities, relationships, pages
     * @throws Exception if AI call fails
     */
    String generateAppStructure(String userPrompt, String systemPrompt) throws Exception;
    
    /**
     * Test connection to AI provider
     * 
     * @return true if connection successful, false otherwise
     */
    boolean testConnection();
    
    /**
     * Get provider name (openai, anthropic, ollama)
     */
    String getProviderName();
}
