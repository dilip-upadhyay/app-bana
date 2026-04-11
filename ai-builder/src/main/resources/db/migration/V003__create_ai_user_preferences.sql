-- Migration: Create AI user preferences table
-- Story: 2.2 - Implement User Preference Engine
-- Fixed: Removed MySQL-style inline INDEX syntax (invalid in PostgreSQL)

CREATE TABLE IF NOT EXISTS ai_user_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(255) NOT NULL,
    preference_type VARCHAR(50) NOT NULL,
    preference_key VARCHAR(255) NOT NULL,
    preference_value TEXT NOT NULL,
    confidence FLOAT DEFAULT 1.0,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    
    -- Unique constraint
    UNIQUE (user_id, preference_type, preference_key)
);

-- Indexes as separate statements (PostgreSQL-compatible)
CREATE INDEX IF NOT EXISTS idx_ai_user_preferences_user     ON ai_user_preferences(user_id);
CREATE INDEX IF NOT EXISTS idx_ai_user_preferences_type     ON ai_user_preferences(preference_type);
CREATE INDEX IF NOT EXISTS idx_ai_user_preferences_user_key ON ai_user_preferences(user_id, preference_key);

-- Add comments
COMMENT ON TABLE ai_user_preferences IS 'Stores learned user preferences for personalization';
COMMENT ON COLUMN ai_user_preferences.preference_type IS 'Type: naming, styling, entity_structure, workflow, etc.';
COMMENT ON COLUMN ai_user_preferences.preference_key IS 'Specific preference key (e.g., "entity_naming_style")';
COMMENT ON COLUMN ai_user_preferences.preference_value IS 'Preference value (JSON or plain text)';
COMMENT ON COLUMN ai_user_preferences.confidence IS 'Confidence score (0.0 to 1.0)';
