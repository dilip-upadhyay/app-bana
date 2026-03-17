-- Migration: Create AI app patterns table
-- Story: 2.1 - Implement Pattern Miner

CREATE TABLE IF NOT EXISTS ai_app_patterns (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pattern_name VARCHAR(255) NOT NULL,
    app_type VARCHAR(100),
    entities JSONB NOT NULL,
    relationships JSONB,
    pages JSONB,
    usage_count INT DEFAULT 0,
    success_rate FLOAT DEFAULT 0.0,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    
    -- Indexes
    INDEX idx_ai_app_patterns_type (app_type),
    INDEX idx_ai_app_patterns_usage (usage_count DESC),
    INDEX idx_ai_app_patterns_success (success_rate DESC),
    
    -- Constraints
    CONSTRAINT chk_success_rate CHECK (success_rate >= 0.0 AND success_rate <= 1.0)
);

-- Add comments
COMMENT ON TABLE ai_app_patterns IS 'Stores discovered patterns from successfully created applications';
COMMENT ON COLUMN ai_app_patterns.pattern_name IS 'Human-readable pattern name (e.g., "CRM with Contacts and Deals")';
COMMENT ON COLUMN ai_app_patterns.app_type IS 'Type of application (e.g., "CRM", "Project Management", "E-commerce")';
COMMENT ON COLUMN ai_app_patterns.entities IS 'JSON array of entity structures';
COMMENT ON COLUMN ai_app_patterns.relationships IS 'JSON array of entity relationships';
COMMENT ON COLUMN ai_app_patterns.pages IS 'JSON array of page configurations';
COMMENT ON COLUMN ai_app_patterns.usage_count IS 'Number of times this pattern has been used';
COMMENT ON COLUMN ai_app_patterns.success_rate IS 'Success rate (0.0 to 1.0) based on user feedback';
