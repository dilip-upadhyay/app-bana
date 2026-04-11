-- Migration: Create AI conversations table
-- Story: 1.4 - Implement Conversation Memory
-- Fixed: Removed MySQL-style inline INDEX syntax (invalid in PostgreSQL)

CREATE TABLE IF NOT EXISTS ai_conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(255) NOT NULL,
    session_id UUID NOT NULL,
    message TEXT NOT NULL,
    response TEXT NOT NULL,
    intent VARCHAR(100),
    feedback INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW(),
    metadata JSONB
);

-- Indexes as separate statements (PostgreSQL-compatible)
CREATE INDEX IF NOT EXISTS idx_ai_conversations_user_id     ON ai_conversations(user_id);
CREATE INDEX IF NOT EXISTS idx_ai_conversations_session_id  ON ai_conversations(session_id);
CREATE INDEX IF NOT EXISTS idx_ai_conversations_created_at  ON ai_conversations(created_at);
CREATE INDEX IF NOT EXISTS idx_ai_conversations_user_session ON ai_conversations(user_id, session_id);

-- Comments
COMMENT ON TABLE ai_conversations IS 'Stores AI agent conversation history with embeddings in Qdrant';
COMMENT ON COLUMN ai_conversations.feedback IS 'User feedback: -1 (thumbs down), 0 (no feedback), 1 (thumbs up)';
COMMENT ON COLUMN ai_conversations.metadata IS 'Additional metadata in JSON format (vector_id, embedding_score, etc.)';
