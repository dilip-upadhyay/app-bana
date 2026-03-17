-- Migration: Create AI feedback table
-- Story: 2.3 - Implement Feedback Loop

CREATE TABLE IF NOT EXISTS ai_feedback (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(255) NOT NULL,
    conversation_id UUID,
    feedback_type VARCHAR(50) NOT NULL,
    rating INT,
    comment TEXT,
    metadata JSONB,
    created_at TIMESTAMP DEFAULT NOW(),
    processed BOOLEAN DEFAULT FALSE,
    
    -- Indexes
    INDEX idx_ai_feedback_user (user_id),
    INDEX idx_ai_feedback_conversation (conversation_id),
    INDEX idx_ai_feedback_type (feedback_type),
    INDEX idx_ai_feedback_processed (processed),
    INDEX idx_ai_feedback_created (created_at DESC),
    
    -- Foreign key
    FOREIGN KEY (conversation_id) REFERENCES ai_conversations(id) ON DELETE CASCADE
);

-- Add comments
COMMENT ON TABLE ai_feedback IS 'Stores user feedback for continuous improvement';
COMMENT ON COLUMN ai_feedback.feedback_type IS 'Type: thumbs_up, thumbs_down, correction, suggestion, etc.';
COMMENT ON COLUMN ai_feedback.rating IS 'Numeric rating (1-5) if applicable';
COMMENT ON COLUMN ai_feedback.processed IS 'Whether feedback has been processed for learning';
