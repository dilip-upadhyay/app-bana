package com.appbana.approval;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * ApprovalColumns — the eight system columns {@code SchemaManager} injects into the
 * physical table of every approval-required entity ({@code approval_status},
 * {@code approval_revision}, {@code approval_parent_id}, {@code submitted_by},
 * {@code submitted_at}, {@code approved_by}, {@code approved_at}, {@code rejection_reason}).
 *
 * <p>Review #6 (blocker) — these columns are physical-only: they are never members of
 * {@code EntitySchema.getFields()}, so any code that resolves a filter/column name by
 * looking it up against the schema's field list (e.g.
 * {@code EntityCrudService.parseFilters()} / {@code buildWhere()}) must special-case
 * them explicitly, or a legitimate approval-scoped filter — {@code
 * filter=submitted_by:=alice}, the exact request the maker-side "Needs rework" view
 * sends — is indistinguishable from a typo'd field name and gets rejected.
 *
 * <p>Single canonical source on the Java side. Previously duplicated as two separate
 * lists inside {@code GenericEntityRoutes} (now delegate here); the TypeScript mirror is
 * {@code app-bana-runtime/src/runtime/approval-columns.ts}.
 */
public final class ApprovalColumns {

    /** Canonical, lower_snake_case column names. */
    public static final List<String> NAMES = List.of(
            "approval_status",
            "approval_revision",
            "approval_parent_id",
            "submitted_by",
            "submitted_at",
            "approved_by",
            "approved_at",
            "rejection_reason"
    );

    /** {@link #NAMES} as a set, for O(1) case-normalized membership checks. */
    public static final Set<String> FIELD_NAMES = Set.copyOf(NAMES);

    /**
     * Both the lower- and UPPER-case spelling of every column, for callers that build
     * lists/sets keyed by the exact casing a request body or a DB result row uses.
     */
    public static final List<String> NAMES_BOTH_CASES = NAMES.stream()
            .flatMap(n -> Stream.of(n, n.toUpperCase(Locale.ROOT)))
            .toList();

    private ApprovalColumns() {
    }

    /** True for any of the eight injected approval columns, in any casing. */
    public static boolean isApprovalColumn(String name) {
        return name != null && FIELD_NAMES.contains(name.toLowerCase(Locale.ROOT));
    }
}
