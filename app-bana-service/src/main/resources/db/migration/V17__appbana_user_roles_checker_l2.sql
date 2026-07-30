-- V17 — two-level checker chain: a user may now be granted 'checker_l2', the
-- final-signoff role for entities with approvalLevels == 2. 'checker' continues
-- to mean the level-1 checker (unchanged, backward compatible). 'both' still
-- means maker + level-1 checker only — it deliberately does NOT expand to
-- checker_l2, since the whole point of a second level is that a different
-- person holds it.
ALTER TABLE appbana_user_roles DROP CONSTRAINT IF EXISTS appbana_user_roles_role_check;
ALTER TABLE appbana_user_roles ADD CONSTRAINT appbana_user_roles_role_check
    CHECK (role IN ('maker', 'checker', 'checker_l2', 'both'));
