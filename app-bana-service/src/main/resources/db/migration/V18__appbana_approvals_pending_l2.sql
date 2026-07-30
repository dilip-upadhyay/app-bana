-- Two-level checker chain: appbana_approvals.to_state must accept PENDING_L2 as both an
-- intermediate hop (PENDING -> PENDING_L2 on a level-1 approve) and a state a row can be
-- rejected back INTO is not applicable here (level-2 reject goes to PENDING, not PENDING_L2),
-- but PENDING_L2 must still be representable as a `to_state` value. Also widened from_state
-- for symmetry/future-proofing even though it currently has no CHECK constraint of its own.
--
-- Discovered by the new two-level ApprovalServiceTest coverage: without this, the very first
-- level-1 approve on a 2-level entity 500s at the audit-log insert (inside the same transaction
-- as the state change), rolling back the whole approve.
ALTER TABLE appbana_approvals DROP CONSTRAINT IF EXISTS appbana_approvals_to_state_check;
ALTER TABLE appbana_approvals ADD CONSTRAINT appbana_approvals_to_state_check
    CHECK (to_state IN ('DRAFT', 'PENDING', 'PENDING_L2', 'APPROVED', 'REJECTED'));
