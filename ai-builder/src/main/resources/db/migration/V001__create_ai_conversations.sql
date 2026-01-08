-- Migration: Create AI conversations table
-- Story: 1.4 - Implement Conversation Memory

CREATE TABLE IF NOT EXISTS ai_conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(255) NOT NULL,
    session_id UUID NOT NULL,
    message TEXT NOT NULL,
    response TEXT NOT NULL,
    intent VARCHAR(100),
    feedback INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW(),
    metadata JSONB,
    
    -- Indexes for common queries
    INDEX idx_ai_conversations_user_id (user_id),
    INDEX idx_ai_conversations_session_id (session_id),
    INDEX idx_ai_conversations_created_at (created_at),
    INDEX idx_ai_conversations_user_session (user_id, session_id)
);

-- Add comment
COMMENT ON TABLE ai_conversations IS 'Stores AI agent conversation history with embeddings in Qdrant';
COMMENT ON COLUMN ai_conversations.feedback IS 'User feedback: -1 (thumbs down), 0 (no feedback), 1 (thumbs up)';
COMMENT ON COLUMN ai_conversations.metadata IS 'Additional metadata in JSON format (vector_id, embedding_score, etc.)';
