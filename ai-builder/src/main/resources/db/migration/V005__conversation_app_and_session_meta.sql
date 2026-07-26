-- V005: Stage 3 — Session picker + app-scoped session listing
--
-- 1) app_id column on ai_conversations lets the studio session picker
--    filter conversations to the currently-selected app.
-- 2) ai_chat_session_meta stores user-editable session metadata
--    (title, is_deleted). We keep it in a separate table so that
--    chat history rows themselves remain append-only.

ALTER TABLE ai_conversations
    ADD COLUMN IF NOT EXISTS app_id VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_ai_conversations_app_id
    ON ai_conversations(app_id);

CREATE INDEX IF NOT EXISTS idx_ai_conversations_user_app
    ON ai_conversations(user_id, app_id);

CREATE TABLE IF NOT EXISTS ai_chat_session_meta (
    session_id  UUID PRIMARY KEY,
    user_id     VARCHAR(255) NOT NULL,
    title       VARCHAR(255),
    is_deleted  BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_chat_session_meta_user
    ON ai_chat_session_meta(user_id);
