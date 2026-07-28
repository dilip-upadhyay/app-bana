package com.appbana.approval;

import com.appbana.model.EntitySchema;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 * looking it up against the schema's field list must special-case them explicitly, or
 * a legitimate approval-scoped filter — {@code filter=submitted_by:=alice}, the exact
 * request the maker-side "Needs rework" view sends — is indistinguishable from a
 * typo'd field name and gets rejected.
 *
 * <p>Review #7 (root cause) — Review #6's fix special-cased the names in
 * {@code EntityCrudService.parseFilters()}/{@code buildWhere()} but treated every one
 * of them as a free-text/LIKE-able string, so {@code approval_revision} (an INTEGER
 * column) and {@code submitted_at}/{@code approved_at} (TIMESTAMP columns) 500'd with
 * a Postgres type-mismatch instead of filtering. That was one symptom of a broader gap:
 * {@code EntitySchema.getFields()} is the sole authority every filter/sort/projection/
 * groupBy code path resolves a field name against, so a bespoke per-call-site
 * workaround was always going to miss a sibling call site or a type. {@link #asFields()}
 * gives every one of those read paths the same typed {@code EntitySchema.Field}
 * objects {@code SchemaManager} would have produced had these columns been declared
 * in the schema — so the existing, already-hardened field-type dispatch
 * ({@code SchemaManager.classifyFieldType()}, {@code isCharacterKind()},
 * {@code parseFilterValue()}) applies to them automatically, with no bespoke branch to
 * keep in sync.
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

    /**
     * Declared type of each column, spelled exactly as {@code SchemaManager.classifyFieldType()}
     * expects (its type-name switch, not {@code FieldSqlKind} directly). Kept as its own map
     * rather than folded into {@link #asFields()}'s construction so the type of a single column
     * can be looked up without materializing a {@code Field} object.
     */
    private static final Map<String, String> TYPES = buildTypes();

    private static Map<String, String> buildTypes() {
        Map<String, String> t = new LinkedHashMap<>();
        t.put("approval_status", "string");
        t.put("approval_revision", "integer");
        t.put("approval_parent_id", "integer");
        t.put("submitted_by", "string");
        t.put("submitted_at", "timestamp");
        t.put("approved_by", "string");
        t.put("approved_at", "timestamp");
        t.put("rejection_reason", "text");
        return Map.copyOf(t);
    }

    /**
     * The eight columns as typed {@code EntitySchema.Field} objects — read-path only.
     * See the class javadoc for why this exists and {@code EntityCrudService.getQueryableFields()}
     * for the one place it's meant to be merged with {@code schema.getFields()}.
     *
     * <p><b>Never</b> merge these into an insert/update/validation field list: doing so would let
     * a client write {@code approval_status}/{@code submitted_by}/etc. directly through the generic
     * entity API, defeating {@code GenericEntityRoutes.enforceApprovalPreInsert()}'s guarantee that
     * only server-assigned values reach those columns.
     */
    public static List<EntitySchema.Field> asFields() {
        return NAMES.stream().map(ApprovalColumns::toField).toList();
    }

    private static EntitySchema.Field toField(String name) {
        EntitySchema.Field f = new EntitySchema.Field();
        f.setName(name);
        f.setType(TYPES.get(name));
        return f;
    }

    private ApprovalColumns() {
    }

    /** True for any of the eight injected approval columns, in any casing. */
    public static boolean isApprovalColumn(String name) {
        return name != null && FIELD_NAMES.contains(name.toLowerCase(Locale.ROOT));
    }
}
