package com.appbana.ai.agent.tool;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task C4.6 — {@code batch_update_entities}' {@code remove_fields} operation is the one
 * ai-builder write path that can break the {@code approvalRequired == true} &hArr; "the eight
 * physical approval columns exist" invariant from the <em>removal</em> side.
 *
 * <p>SchemaManager now guarantees those columns whenever the flag is set, but nothing stopped
 * this tool from dropping them again while leaving the flag true — which yields exactly the
 * state C4.6 exists to eliminate: an entity that accepts inserts and then 500s on submit,
 * approve, and the checker's pending queue.
 */
class BatchUpdateApprovalColumnGuardTest {

    private static Map<String, Object> entity(boolean approvalRequired) {
        Map<String, Object> e = new HashMap<>();
        e.put("name", "LoanApplication");
        e.put("approvalRequired", approvalRequired);
        return e;
    }

    /**
     * Driven off {@code RESERVED_APPROVAL_COLUMNS} rather than a hardcoded list, so a ninth
     * column is covered automatically instead of leaving a silent hole.
     */
    @Test
    void everyReservedApprovalColumnIsRefusedWhileTheFlagIsSet() {
        for (String column : SchemaEnricher.RESERVED_APPROVAL_COLUMNS) {
            List<String> blocked = BatchUpdateEntitiesTool.reservedApprovalColumnsIn(
                    entity(true), Set.of(column));
            assertEquals(List.of(column), blocked,
                    "removing '" + column + "' from an approval-required entity must be refused");
        }
    }

    @Test
    void theGuardReportsEveryOffendingColumnNotJustTheFirst() {
        List<String> blocked = BatchUpdateEntitiesTool.reservedApprovalColumnsIn(
                entity(true), Set.of("approval_status", "submitted_by", "notes"));

        assertEquals(List.of("approval_status", "submitted_by"), blocked,
                "the warning must name every offending column so the caller can fix them in one pass");
    }

    @Test
    void removingOrdinaryBusinessFieldsIsStillAllowedOnAnApprovalEntity() {
        assertTrue(BatchUpdateEntitiesTool.reservedApprovalColumnsIn(
                entity(true), Set.of("notes", "nickname")).isEmpty(),
                "the guard must not block unrelated field removals");
    }

    /**
     * Without the workflow the eight names carry no special meaning, so a user-defined field
     * that merely happens to share one is the caller's to delete. Guarding unconditionally
     * would make a legitimate rename impossible on a non-approval entity.
     */
    @Test
    void theSameNamesAreRemovableWhenTheEntityHasNoApprovalWorkflow() {
        assertTrue(BatchUpdateEntitiesTool.reservedApprovalColumnsIn(
                entity(false), Set.of("approval_status", "submitted_by")).isEmpty(),
                "no approval workflow means no reserved names");
    }

    @Test
    void namesAreMatchedCaseAndWhitespaceInsensitively() {
        List<String> blocked = BatchUpdateEntitiesTool.reservedApprovalColumnsIn(
                entity(true), Set.of("  APPROVAL_STATUS  "));

        assertEquals(1, blocked.size(),
                "a differently-cased or padded name is the same column to Postgres, so the guard "
                        + "must not be bypassable by spelling: " + blocked);
    }

    @Test
    void aMissingOrFlaglessEntityIsTreatedAsUnguarded() {
        assertTrue(BatchUpdateEntitiesTool.reservedApprovalColumnsIn(null, Set.of("approval_status")).isEmpty());
        assertTrue(BatchUpdateEntitiesTool.reservedApprovalColumnsIn(new HashMap<>(), Set.of("approval_status"))
                .isEmpty());
        assertTrue(BatchUpdateEntitiesTool.reservedApprovalColumnsIn(entity(true), null).isEmpty());
    }
}
