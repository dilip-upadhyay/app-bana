package com.appbana.service;

import com.appbana.JdbcManager;
import com.appbana.SchemaManager;
import com.appbana.approval.ApprovalColumns;
import com.appbana.model.EntitySchema;
import com.appbana.model.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Entity CRUD and query operations for schema-driven dynamic entities.
 *
 * Supports multi-tenant isolation through TenantContext and physical table
 * names.
 * Each tenant+app combination gets a separate physical table, eliminating the
 * need
 * for tenant_id/app_id columns in runtime entity tables.
 *
 * Extracted from {@code ApiServer} to enable modular, testable route handlers.
 */
public class EntityCrudService {

    private static final Logger LOG = LoggerFactory.getLogger(EntityCrudService.class);

    // ==================== Context-Aware Methods (Multi-Tenant)
    // ====================

    /**
     * Insert record with tenant/app isolation
     * 
     * @param context TenantContext for isolation
     * @param schema  Entity schema
     * @param data    Record data
     * @return Generated primary key
     */
    public Object insertRecord(TenantContext context, EntitySchema schema, Map<String, Object> data)
            throws SQLException {
        if (context == null) {
            throw new IllegalArgumentException("TenantContext is required");
        }
        // Physical table name already provides tenant/app isolation - no need for
        // columns
        return insertRecordLegacy(schema, data);
    }

    /**
     * List all records for given tenant/app
     */
    public List<Map<String, Object>> listAll(TenantContext context, EntitySchema schema) throws SQLException {
        if (context == null) {
            throw new IllegalArgumentException("TenantContext is required");
        }
        String tableName = SchemaManager.getPhysicalTableName(schema);
        String sql = "SELECT * FROM " + quote(tableName);
        try (Connection c = schemaConnection(schema);
                PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            return toList(rs);
        }
    }

    /**
     * Get record by ID within tenant/app scope
     */
    public Map<String, Object> getById(TenantContext context, EntitySchema schema, String id) throws SQLException {
        if (context == null) {
            throw new IllegalArgumentException("TenantContext is required");
        }
        EntitySchema.Field pk = schema.getFields().stream().filter(EntitySchema.Field::isPrimaryKey).findFirst()
                .orElse(null);
        if (pk == null) {
            return null;
        }
        String sql = "SELECT * FROM " + quote(SchemaManager.getPhysicalTableName(schema)) +
                " WHERE " + quote(pk.getName()) + " = ?";
        try (Connection c = schemaConnection(schema);
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, parseId(id, pk));
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> list = toList(rs);
                return list.isEmpty() ? null : list.getFirst();
            }
        }
    }

    /**
     * Update record by ID within tenant/app scope
     */
    public int updateById(TenantContext context, EntitySchema schema, String id, Map<String, Object> data)
            throws SQLException {
        if (context == null) {
            throw new IllegalArgumentException("TenantContext is required");
        }
        EntitySchema.Field pk = schema.getFields().stream().filter(EntitySchema.Field::isPrimaryKey).findFirst()
                .orElse(null);
        if (pk == null) {
            return 0;
        }
        List<String> set = new ArrayList<>();
        List<Object> vals = new ArrayList<>();
        for (EntitySchema.Field f : schema.getFields()) {
            if (f.isPrimaryKey()) {
                continue;
            }
            if (data.containsKey(f.getName())) {
                Object raw = data.get(f.getName());
                Object val = coerceAndValidate(f, raw);
                set.add(quote(f.getName()) + " = ?");
                vals.add(val);
            }
        }
        if (set.isEmpty()) {
            return 0;
        }
        String sql = "UPDATE " + quote(SchemaManager.getPhysicalTableName(schema)) + " SET " + String.join(",", set) +
                " WHERE " + quote(pk.getName()) + " = ?";
        try (Connection c = schemaConnection(schema);
                PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            for (Object v : vals) {
                ps.setObject(i++, v);
            }
            ps.setObject(i, parseId(id, pk));
            return ps.executeUpdate();
        }
    }

    /**
     * Delete record by ID within tenant/app scope
     */
    public int deleteById(TenantContext context, EntitySchema schema, String id) throws SQLException {
        if (context == null) {
            throw new IllegalArgumentException("TenantContext is required");
        }
        EntitySchema.Field pk = schema.getFields().stream().filter(EntitySchema.Field::isPrimaryKey).findFirst()
                .orElse(null);
        if (pk == null) {
            return 0;
        }
        String sql = "DELETE FROM " + quote(SchemaManager.getPhysicalTableName(schema)) +
                " WHERE " + quote(pk.getName()) + " = ?";
        try (Connection c = schemaConnection(schema);
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, parseId(id, pk));
            return ps.executeUpdate();
        }
    }

    // ==================== Legacy Methods (Backward Compatible)
    // ====================

    /**
     * @deprecated Use insertRecord(TenantContext, schema, data) for tenant
     *             isolation
     */
    @Deprecated
    public Object insertRecord(EntitySchema schema, Map<String, Object> data) throws SQLException {
        return insertRecordLegacy(schema, data);
    }

    /**
     * The complete writable column list for {@code rows} — every declared field except an
     * auto-increment PK, plus any approval column the server has already staged into the row.
     *
     * <p>C4.6 follow-up — this is the single builder {@code insertRecordLegacy}, {@link #insertBatch}
     * and the approval-revision branch of {@link #updateById} all derive their column list from. It
     * exists because deriving that list from {@code schema.getFields()} alone is now wrong for
     * approval-required entities: C4.6 made the eight approval columns physical-only, so
     * {@code getFields()} no longer mentions them, and the server-assigned
     * {@code approval_status} / {@code approval_revision} / {@code submitted_by} values staged by
     * {@code GenericEntityRoutes} were being silently dropped on the floor — landing rows with a
     * NULL status that {@code ApprovalService.Status.fromValue(null)} then reads back as DRAFT, so
     * nothing downstream complained. The single-record insert was fixed in the C4.6 commit; the
     * batch insert and the revision update were not. Centralising the rule here is what stops a
     * fourth write path repeating it.
     *
     * <p><b>Security</b> — an approval column is included only when the row map already contains
     * that key, and only callers that opt in reach this for updates. That is what keeps this
     * consistent with {@code ApprovalColumns.asFields()}'s "never merge these into an insert field
     * list" warning: {@code enforceApprovalPreInsert()} strips every client-supplied approval value
     * before putting the server's own values back, so on insert paths the only approval keys that
     * can be present are ones the server itself staged. A forged {@code approval_status=APPROVED}
     * in a request body never survives to this point.
     *
     * <p>Presence is evaluated across the whole batch (union, not per row) because one
     * {@code PreparedStatement} is shared by every element, so the column list must be fixed for
     * all of them; an element missing a key simply binds null.
     */
    static List<EntitySchema.Field> writableFields(EntitySchema schema, List<Map<String, Object>> rows) {
        List<EntitySchema.Field> out = new ArrayList<>();
        for (EntitySchema.Field f : schema.getFields()) {
            if (f.isPrimaryKey() && f.isAutoIncrement()) {
                continue;
            }
            out.add(f);
        }
        if (!schema.isApprovalRequired()) {
            return out;
        }
        Set<String> declared = new HashSet<>();
        for (EntitySchema.Field f : out) {
            if (f.getName() != null) {
                declared.add(f.getName().toLowerCase(Locale.ROOT));
            }
        }
        for (EntitySchema.Field approvalField : ApprovalColumns.asFields()) {
            String name = approvalField.getName();
            // Both sides normalised: ApprovalColumns.NAMES is lower-case today, so comparing it
            // raw would work by coincidence rather than by construction.
            if (declared.contains(name.toLowerCase(Locale.ROOT))) {
                continue;
            }
            boolean staged = rows.stream().anyMatch(r -> r != null && r.containsKey(name));
            if (staged) {
                out.add(approvalField);
            }
        }
        return out;
    }

    private Object insertRecordLegacy(EntitySchema schema, Map<String, Object> data) throws SQLException {
        List<EntitySchema.Field> fields = writableFields(schema, List.of(data));
        List<String> cols = new ArrayList<>();
        List<String> placeholders = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        for (EntitySchema.Field field : fields) {
            if (field.isPrimaryKey()) {
                // Generate UUID if missing and type is compatible
                if (!data.containsKey(field.getName())) {
                    String type = field.getType().toLowerCase(Locale.ROOT);
                    if (isCharacterKind(type)) {
                        data.put(field.getName(), java.util.UUID.randomUUID().toString());
                    }
                }
            }

            String fieldName = field.getName();
            Object raw = data.get(fieldName);

            // Fuzzy matching: if field is missing, try matching by label or with spaces instead of underscores
            if (raw == null) {
                String label = field.getLabel();
                if (label != null && data.containsKey(label)) {
                    raw = data.get(label);
                } else {
                    // try common variations: replace underscores with spaces
                    String withSpaces = fieldName.replace('_', ' ');
                    if (data.containsKey(withSpaces)) {
                        raw = data.get(withSpaces);
                    }
                }
            }

            // Audit field auto-injection
            if (raw == null || (raw instanceof String s && s.isBlank())) {
                if ("created_at".equalsIgnoreCase(fieldName) || "updated_at".equalsIgnoreCase(fieldName)) {
                    raw = new Timestamp(System.currentTimeMillis());
                }
            }

            cols.add(quote(fieldName));
            placeholders.add("?");
            Object val = coerceAndValidate(field, raw);
            values.add(val);
        }

        String tableName = SchemaManager.getPhysicalTableName(schema);
        String sql = "INSERT INTO " + quote(tableName) + " (" + String.join(",", cols) + ") VALUES ("
                + String.join(",", placeholders) + ")";

        try (Connection c = schemaConnection(schema);
                PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < values.size(); i++) {
                ps.setObject(i + 1, values.get(i));
            }
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    Object generatedId = rs.getObject(1);
                    if (generatedId != null) {
                        return generatedId;
                    }
                    LOG.warn("[INSERT] Generated keys row found but ID value is null for table: {}", tableName);
                }
            }

            // If no generated key returned (e.g. client provided UUID), return PK
            EntitySchema.Field pk = schema.getFields().stream().filter(EntitySchema.Field::isPrimaryKey).findFirst()
                    .orElse(null);
            if (pk != null && data.containsKey(pk.getName())) {
                return data.get(pk.getName());
            }
        }

        LOG.warn("[INSERT] No ID could be retrieved after insertion for table: {}. Data keys: {}", 
                SchemaManager.getPhysicalTableName(schema), data.keySet());
        return -1L; // Return numeric sentinel instead of null to prevent NPE
    }

    public List<Map<String, Object>> listAll(EntitySchema schema) throws SQLException {
        // Review #9 (High) — this used to be a bare SELECT *, which returns EVERY
        // physical column on the table, including the 8 approval columns (raw,
        // uppercase DB keys) for any approval-required entity. Review #7's
        // default-projection leak guardrail was documented and enforced in
        // listAdvanced()'s default (no fields=) path, but this method — the ONE
        // that a bare `GET /api/{entity}` with no query params at all actually
        // calls — never got the same treatment, so the guardrail held on only
        // one of the route's two code paths. Project schema.getFields()
        // explicitly instead, aliased to preserve declared casing, mirroring
        // listAdvanced()'s default projection exactly — same guardrail, now
        // enforced on both branches. Falls back to SELECT * only for the
        // (schema-invalid, shouldn't happen) case of a schema with no fields.
        List<EntitySchema.Field> fields = schema.getFields();
        String sql;
        if (fields == null || fields.isEmpty()) {
            sql = "SELECT * FROM " + quote(SchemaManager.getPhysicalTableName(schema));
        } else {
            List<String> selectCols = new ArrayList<>();
            for (EntitySchema.Field f : fields) {
                selectCols.add(quote(f.getName()) + " AS \"" + f.getName() + "\"");
            }
            sql = "SELECT " + String.join(",", selectCols) + " FROM "
                    + quote(SchemaManager.getPhysicalTableName(schema));
        }
        try (Connection c = schemaConnection(schema);
                PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            return toList(rs);
        }
    }

    public Map<String, Object> getById(EntitySchema schema, Object id) throws SQLException {
        EntitySchema.Field pk = schema.getFields().stream().filter(EntitySchema.Field::isPrimaryKey).findFirst()
                .orElse(null);
        if (pk == null) {
            return null;
        }
        String sql = "SELECT * FROM " + quote(SchemaManager.getPhysicalTableName(schema)) + " WHERE "
                + quote(pk.getName()) + " = ?";
        
        Object parsedId;
        if (id instanceof String idStr) {
            parsedId = parseId(idStr, pk);
        } else {
            parsedId = id; // use raw object (Long, Integer, etc.) if already available
        }
        
        LOG.info("[GET_BY_ID] SQL: {}, ID: {} ({})", sql, parsedId, (parsedId != null ? parsedId.getClass().getSimpleName() : "null"));
        
        try (Connection c = schemaConnection(schema);
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, parsedId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> list = toList(rs);
                return list.isEmpty() ? null : list.getFirst();
            }
        }
    }

    public int updateById(EntitySchema schema, String id, Map<String, Object> data) throws SQLException {
        return updateById(schema, id, data, false);
    }

    /**
     * C4.6a/b — {@code allowApprovalColumns} opts a caller into writing the eight physical
     * approval columns.
     *
     * <p><b>Precondition for passing {@code true}:</b> the caller must already have run
     * {@code GenericEntityRoutes.applyApprovalPutGuard()} (or otherwise constructed the approval
     * values itself). That guard calls {@code stripApprovalColumns(data)} unconditionally for any
     * approval-required entity before it returns, so every approval key still present afterwards
     * is server-staged. All three client PUT routes satisfy this, which is why they pass
     * {@code true}.
     *
     * <p>C4.6a originally justified the {@code false} default as "the exclusion IS the guard
     * against a forged {@code approval_status=APPROVED}". That was wrong, and a mutation test
     * disproved it: flipping the default leaked only {@code submitted_by=alice_maker} — the
     * server's own value — never the forged {@code eve_attacker}, because
     * {@code stripApprovalColumns} had already removed it. The real effect of the exclusion was
     * to silently discard the three values the guard deliberately re-stages
     * ({@code approval_status=DRAFT}, {@code rejection_reason=null}, {@code submitted_by}), so a
     * maker editing a REJECTED row in place got a 200 with the business edit applied while the
     * row stayed REJECTED carrying its stale reason.
     *
     * <p>The default remains {@code false} as defence-in-depth for any future caller that reaches
     * this method without running the guard first.
     */
    public int updateById(EntitySchema schema, String id, Map<String, Object> data, boolean allowApprovalColumns)
            throws SQLException {
        EntitySchema.Field pk = schema.getFields().stream().filter(EntitySchema.Field::isPrimaryKey).findFirst()
                .orElse(null);
        if (pk == null) {
            return 0;
        }
        List<EntitySchema.Field> candidates = allowApprovalColumns
                ? writableFields(schema, List.of(data))
                : schema.getFields();
        List<String> set = new ArrayList<>();
        List<Object> vals = new ArrayList<>();
        for (EntitySchema.Field f : candidates) {
            if (f.isPrimaryKey()) {
                continue;
            }
            if (data.containsKey(f.getName())) {
                Object raw = data.get(f.getName());
                Object val = coerceAndValidate(f, raw);
                set.add(quote(f.getName()) + " = ?");
                vals.add(val);
            }
        }
        if (set.isEmpty()) {
            return 0;
        }

        String sql = "UPDATE " + quote(SchemaManager.getPhysicalTableName(schema)) + " SET " + String.join(",", set)
                + " WHERE "
                + quote(pk.getName()) + " = ?";

        try (Connection c = schemaConnection(schema);
                PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            for (Object v : vals) {
                ps.setObject(i++, v);
            }
            ps.setObject(i, parseId(id, pk));
            return ps.executeUpdate();
        }
    }

    public int deleteById(EntitySchema schema, String id) throws SQLException {
        EntitySchema.Field pk = schema.getFields().stream().filter(EntitySchema.Field::isPrimaryKey).findFirst()
                .orElse(null);
        if (pk == null) {
            return 0;
        }
        String sql = "DELETE FROM " + quote(SchemaManager.getPhysicalTableName(schema)) + " WHERE "
                + quote(pk.getName()) + " = ?";
        try (Connection c = schemaConnection(schema);
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, parseId(id, pk));
            return ps.executeUpdate();
        }
    }

    /**
     * C2.3 — finds the single "open" (not yet approved) revision row whose
     * {@code approval_parent_id} points at {@code parentId}.
     *
     * <p>Open means {@code approval_status} is DRAFT, PENDING or REJECTED. An APPROVED
     * revision cannot exist: {@code ApprovalService.approveRecord} merges it into the
     * parent and deletes it in the same transaction.
     *
     * <p>Returns {@code null} when the entity has no approval workflow, or when no open
     * revision exists.
     *
     * <p>C4.6 — this used to probe {@code schema.getFields()} for an {@code approval_parent_id}
     * member. That was a capability check standing in for the real question ("does this entity
     * have an approval workflow?") and it only worked while SchemaEnricher declared the eight
     * columns as schema fields. Now that SchemaManager guarantees the physical columns from
     * {@code approvalRequired} alone, the flag is the authority; probing getFields() would
     * report "no revision support" for every correctly-provisioned approval entity and silently
     * downgrade a revision to an in-place edit of a live APPROVED row.
     */
    public Map<String, Object> findOpenRevision(EntitySchema schema, String parentId) throws SQLException {
        if (schema == null || parentId == null || parentId.isBlank() || !schema.isApprovalRequired()) {
            return null;
        }
        EntitySchema.Field pk = schema.getFields().stream().filter(EntitySchema.Field::isPrimaryKey).findFirst()
                .orElse(null);
        if (pk == null) {
            return null;
        }

        String sql = "SELECT * FROM " + quote(SchemaManager.getPhysicalTableName(schema))
                + " WHERE " + quote("approval_parent_id") + " = ?"
                + " AND UPPER(" + quote("approval_status") + ") IN ('DRAFT','PENDING','REJECTED')"
                + " ORDER BY " + quote("approval_revision") + " DESC, " + quote(pk.getName()) + " DESC";

        try (Connection c = schemaConnection(schema);
                PreparedStatement ps = c.prepareStatement(sql)) {
            // C4.6 — approval_parent_id is INTEGER (ApprovalColumns declares it to match the
            // parent's auto-increment PK type). Binding the id as a String made Postgres reject
            // the whole query with "operator does not exist: integer = character varying".
            // It survived before only because fixtures hand-declared the column as text; the
            // enricher's own VARCHAR(255) spelling disagreed with the PK type it points at.
            ps.setObject(1, ApprovalColumns.parentIdValue(parentId));
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> list = toList(rs);
                return list.isEmpty() ? null : list.getFirst();
            }
        }
    }

    /**
     * C2.3 — takes a {@code SELECT ... FOR UPDATE} lock on one row and holds it until closed.
     *
     * <p>Used to serialise revision creation for a given parent. {@code findOpenRevision} and the
     * subsequent insert/update run on their own pooled connections, but because a competing request
     * blocks on this same parent row before it can read, the find-then-write sequence is still
     * mutually exclusive: the loser only proceeds after the winner has committed and released.
     *
     * <p>Returns {@code null} when the schema has no primary key. Never commits — the lock exists
     * purely as a mutex, so {@link RowLock#close()} rolls back.
     */
    public RowLock lockRow(EntitySchema schema, String rowId) throws SQLException {
        EntitySchema.Field pk = schema.getFields().stream().filter(EntitySchema.Field::isPrimaryKey).findFirst()
                .orElse(null);
        if (pk == null || rowId == null || rowId.isBlank()) {
            return null;
        }
        String sql = "SELECT " + quote(pk.getName()) + " FROM " + quote(SchemaManager.getPhysicalTableName(schema))
                + " WHERE " + quote(pk.getName()) + " = ? FOR UPDATE";
        Connection c = schemaConnection(schema);
        try {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setObject(1, parseId(rowId, pk));
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                }
            }
            return new RowLock(c);
        } catch (SQLException e) {
            try {
                c.rollback();
            } catch (SQLException ignored) {
                // best effort — the connection is being discarded anyway
            }
            try {
                c.setAutoCommit(true);
            } catch (SQLException ignored) {
                // best effort
            }
            c.close();
            throw e;
        }
    }

    /** Handle for the row lock taken by {@link #lockRow}. Closing releases it. */
    public static final class RowLock implements AutoCloseable {
        private final Connection conn;

        private RowLock(Connection conn) {
            this.conn = conn;
        }

        @Override
        public void close() {
            try {
                conn.rollback();
            } catch (SQLException e) {
                LOG.warn("[REVISION_LOCK] rollback on release failed: {}", e.getMessage());
            }
            try {
                conn.setAutoCommit(true);
            } catch (SQLException e) {
                LOG.warn("[REVISION_LOCK] autocommit restore failed: {}", e.getMessage());
            }
            try {
                conn.close();
            } catch (SQLException e) {
                LOG.warn("[REVISION_LOCK] close failed: {}", e.getMessage());
            }
        }
    }

    /**
     * Review #7 (root cause) — {@code schema.getFields()} is the sole authority every
     * filter/sort/projection/groupBy code path resolves a field name against. The 8
     * approval system columns are physical-only ({@code SchemaManager} creates them in
     * the table for every approval-required entity) and are never members of
     * {@code getFields()}. Review #6 patched two call sites (parseFilters/buildWhere)
     * with a bespoke {@code isApprovalColumn()} branch that treated every one of them as
     * a free-text/LIKE-able string — which is why {@code approval_revision} (INTEGER)
     * and {@code submitted_at}/{@code approved_at} (TIMESTAMP) 500'd instead of
     * filtering, and why {@code sort=}/{@code fields=} on those columns silently no-op'd
     * (the branch never existed there at all).
     *
     * <p>This is the single fieldMap-building step every READ path should use instead:
     * {@code schema.getFields()} plus {@link ApprovalColumns#asFields()}, but ONLY when
     * the entity actually has an approval workflow ({@code schema.isApprovalRequired()})
     * — otherwise a schema with no physical {@code approval_status} etc. column would
     * 500 with "column does not exist" instead of 400 for an unrecognized field (D3).
     *
     * <p><b>Read-path only.</b> Insert/update/validation must keep using
     * {@code schema.getFields()} directly so a client can never write these columns
     * through the generic entity API — see {@code enforceApprovalPreInsert()}/
     * {@code stripApprovalColumns()} in {@code GenericEntityRoutes}. The default
     * projection (no {@code fields=} requested) must also keep using
     * {@code schema.getFields()} directly, not this method — otherwise every list
     * response would silently grow 8 columns and leak approval metadata to every caller
     * that doesn't ask for it.
     */
    private static List<EntitySchema.Field> getQueryableFields(EntitySchema schema) {
        if (!schema.isApprovalRequired()) {
            return schema.getFields();
        }
        List<EntitySchema.Field> combined = new ArrayList<>(schema.getFields());
        // Review #8 (Nit) — if a schema declares one of the 8 approval columns as
        // a real field of its own (ApprovalRoutesSecurityTest's fixture does this),
        // appending the synthetic definition unconditionally put both in this list.
        // fieldMap-building callers (parseFilters/listAdvanced) put()-last-wins so
        // the synthetic one silently shadowed the declared one, while
        // resolveQueryableField()'s loop is first-wins and returned the declared
        // one — two lookup paths disagreeing about which Field object is
        // authoritative for the same name. Harmless today because
        // ApprovalColumns.TYPES matches the physical DDL column-for-column, but
        // skip the synthetic field outright when the name is already declared so
        // both paths agree by construction instead of by coincidence.
        Set<String> declared = new HashSet<>();
        for (EntitySchema.Field f : schema.getFields()) {
            declared.add(f.getName().toLowerCase(Locale.ROOT));
        }
        for (EntitySchema.Field f : ApprovalColumns.asFields()) {
            if (declared.add(f.getName().toLowerCase(Locale.ROOT))) {
                combined.add(f);
            }
        }
        return combined;
    }

    /**
     * Resolves {@code name} to its canonical field name (case-insensitive), including
     * the approval columns per {@link #getQueryableFields}. Returns {@code null} when
     * unrecognized — callers (e.g. {@code groupBy=}) should fail closed with a 400
     * rather than silently accepting an arbitrary/unknown column name.
     */
    public String resolveQueryableField(EntitySchema schema, String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        for (EntitySchema.Field f : getQueryableFields(schema)) {
            if (f.getName().equalsIgnoreCase(name)) {
                return f.getName();
            }
        }
        return null;
    }

    public Map<String, Object> parseFilters(String raw, EntitySchema schema) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            LOG.debug("[FILTER] No filter string provided");
            return map;
        }
        // Review #5 (High A) — this used to re-decode `raw` here with
        // java.net.URLDecoder.decode(), on top of the decoding Router already
        // performs via URI.getQuery() (which decodes %XX percent-escapes per
        // RFC 3986 before this method ever sees the string). URLDecoder applies
        // application/x-www-form-urlencoded semantics, where a literal '+' —
        // already correctly restored from a client's %2B by Router — gets
        // turned into a space. That silently corrupted phone numbers, timezone
        // offsets ("+05:30"), base64, and any value containing '+'. The value
        // arrives already decoded; do not decode it again.
        LOG.debug("[FILTER] Parsing filter string: {}", raw);
        String[] pairs = raw.split(",");
        Map<String, EntitySchema.Field> fieldMap = new HashMap<>();
        for (EntitySchema.Field f : getQueryableFields(schema)) {
            fieldMap.put(f.getName().toLowerCase(Locale.ROOT), f);
            LOG.debug("[FILTER] Schema field: {} (lowercased: {})", f.getName(), f.getName().toLowerCase(Locale.ROOT));
        }
        for (String p : pairs) {
            int idx = p.indexOf(":");
            if (idx <= 0) {
                LOG.debug("[FILTER] Skipping invalid pair (no colon or at start): {}", p);
                continue;
            }
            String name = p.substring(0, idx).trim();
            String val = p.substring(idx + 1).trim();
            if (name.isEmpty()) {
                LOG.debug("[FILTER] Skipping pair with empty name");
                continue;
            }
            // C3.9 — a leading '=' on the value requests an exact comparison
            // instead of the default substring LIKE. See ExactMatch.
            boolean exact = val.startsWith("=");
            if (exact) {
                val = val.substring(1).trim();
            }
            LOG.debug("[FILTER] Looking up field '{}' (lowercased: '{}') in schema", name,
                    name.toLowerCase(Locale.ROOT));
            EntitySchema.Field f = fieldMap.get(name.toLowerCase(Locale.ROOT));
            if (f == null) {
                // Review #5 (High A) — this used to `continue` (ignore unknown field),
                // which is the exact same fail-open hazard the null-value check just
                // below closes for a bad *value*: a typo'd field name (e.g.
                // "?filter=statuss:open" instead of "status:open") silently produced an
                // unscoped 200 instead of the caller's intended filter. filter= is the
                // only scoping mechanism for several callers (child records, saved
                // views, approval queues), so a typo here is a correctness/data-exposure
                // hazard, not just a UX one. Fail closed like the value check does,
                // rather than leaving this one hole in the same fix.
                //
                // Review #7 (root cause) — fieldMap above is built from
                // getQueryableFields(), which already folds in the 8 approval columns
                // as typed fields when schema.isApprovalRequired(). So this branch is
                // reached for a genuinely unknown name OR an approval-column name on an
                // entity that doesn't have the approval workflow enabled (D3) — both
                // cases are correctly a 400, not a silent pass-through.
                throw new FieldValidationException(name, "unknown filter field");
            }
            // Checked before parseFilterValue() below, because a bare
            // Integer.parseInt/BigDecimal/Instant.parse on "10..100" would fail
            // to parse and produce a correct-but-unhelpful "invalid value" 400
            // instead of being recognised as a range. A single '.' cannot
            // collide (fractional-second timestamps use one dot, never two).
            int dotdot = val.indexOf("..");
            if (dotdot >= 0) {
                map.put(f.getName(), parseRange(f, val, dotdot));
                continue;
            }
            Object parsed = parseFilterValue(f, val);
            if (parsed == null) {
                // Review #4 (High A) — a filter value that fails to coerce for the
                // field's type used to be stored as null here and then silently
                // dropped by buildWhere() (fail-open: 200 with an unscoped or
                // under-scoped result). filter= is the only scoping mechanism for
                // several callers (child records, saved views, approval queues),
                // so failing open is a correctness/data-exposure hazard, not just
                // a UX one. Fail closed instead: reject the whole request with
                // 400 and say exactly which field/value was bad.
                throw new FieldValidationException(f.getName(),
                        "invalid value '" + val + "' for type '" + f.getType() + "'");
            }
            LOG.debug("[FILTER] Parsed filter: {}={} (type: {}, parsed value: {})", f.getName(), val, f.getType(),
                    parsed);
            map.put(f.getName(), exact ? new ExactMatch(parsed) : parsed); // canonical
        }
        LOG.debug("[FILTER] Final filter map: {}", map);
        return map;
    }

    /**
     * Marks a filter value that must be compared with {@code =} rather than the
     * default case-insensitive {@code LIKE '%value%'}.
     *
     * <p>C3.9 — string filters default to a substring match, which is right for a
     * user typing into a search box and wrong for anything identity-shaped. The
     * approval "Needs rework" view scopes a list to {@code submitted_by}, and
     * under a substring match the user {@code bob} would also see every record
     * submitted by {@code bobby}. Silently, and with a 200.
     *
     * <p>Written on the wire as a leading {@code =} on the value:
     * {@code filter=submitted_by:=bob}.
     */
    public record ExactMatch(Object value) {}

    /**
     * A "min..max" double-dot range filter, e.g. {@code filter=amount:10..100}.
     *
     * <p>Either bound may be null for an open-ended range; {@link #parseRange}
     * rejects a range with neither. Only orderable kinds (integer/bigint/decimal/
     * timestamp/reference) are accepted — a range on a string/boolean column is a
     * 400, not a silently-ignored predicate.
     */
    public record Range(Object min, Object max) {}

    /**
     * Parses the "min..max" syntax found in {@link #parseFilters}. {@code dotdot}
     * is the index of the delimiting "..".
     */
    private static Range parseRange(EntitySchema.Field f, String val, int dotdot) {
        SchemaManager.FieldSqlKind kind = SchemaManager.classifyFieldType(f.getType());
        boolean orderable = switch (kind) {
            case INTEGER, BIGINT, DECIMAL, TIMESTAMP, REFERENCE -> true;
            default -> false;
        };
        if (!orderable) {
            throw new FieldValidationException(f.getName(),
                    "range filters ('min..max') are only supported for numeric/date fields, not '" + f.getType()
                            + "'");
        }
        String lo = val.substring(0, dotdot).trim();
        String hi = val.substring(dotdot + 2).trim();
        Object loVal = lo.isEmpty() ? null : parseFilterValue(f, lo);
        Object hiVal = hi.isEmpty() ? null : parseFilterValue(f, hi);
        if ((!lo.isEmpty() && loVal == null) || (!hi.isEmpty() && hiVal == null)) {
            throw new FieldValidationException(f.getName(),
                    "invalid range value '" + val + "' for type '" + f.getType() + "'");
        }
        if (loVal == null && hiVal == null) {
            throw new FieldValidationException(f.getName(), "range filter '" + val + "' needs at least one bound");
        }
        return new Range(loVal, hiVal);
    }

    public long countOnly(EntitySchema schema, String q, Map<String, Object> filters) throws SQLException {
        StringBuilder where = new StringBuilder();
        List<Object> params = new ArrayList<>();
        buildWhere(schema, q, filters, where, params);
        String sql = "SELECT COUNT(*) FROM " + quote(SchemaManager.getPhysicalTableName(schema)) + where;
        try (Connection c = schemaConnection(schema);
                PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    /**
     * H6 hardening — return TRUE COUNT(*) per distinct value of {@code groupBy}
     * across the whole filtered result set (not just the current page).
     * Before H6 the /api/{entity} listing only bucketed the returned page in
     * Java, so counts were wrong the moment the data exceeded {@code limit}.
     *
     * Guards:
     *   - {@code groupBy} MUST match a real field on the schema (case-insensitive)
     *     — otherwise this returns an empty map, refusing to interpolate an
     *     unknown identifier into SQL. This is our SQL-injection guard.
     *   - Uses the same WHERE clause as the paged list, so counts are
     *     consistent with what the caller sees on page 1.
     *
     * Keys: the column value stringified; NULL becomes empty string "".
     * Returns a linked map preserving natural COUNT DESC order.
     */
    public Map<String, Long> countByGroup(EntitySchema schema,
                                          String groupBy,
                                          String q,
                                          Map<String, Object> filters) throws SQLException {
        if (groupBy == null || groupBy.isBlank()) return Map.of();
        // Resolve to the canonical field name from the schema — the whole
        // point of this lookup is to refuse to trust the raw query-string.
        // Review #7 (root cause) — getQueryableFields() folds in the 8 approval
        // columns (when the entity has the approval workflow), so groupBy=
        // approval_status now resolves instead of being silently rejected here.
        String canonical = null;
        for (EntitySchema.Field f : getQueryableFields(schema)) {
            if (f.getName().equalsIgnoreCase(groupBy)) {
                canonical = f.getName();
                break;
            }
        }
        if (canonical == null) {
            LOG.warn("[GROUP-BY] Rejecting unknown groupBy column '{}' for entity {}", groupBy, schema.getName());
            return Map.of();
        }

        StringBuilder where = new StringBuilder();
        List<Object> params = new ArrayList<>();
        buildWhere(schema, q, filters, where, params);
        String col = quote(canonical);
        String sql = "SELECT " + col + " AS grp_key, COUNT(*) AS grp_count"
                + " FROM " + quote(SchemaManager.getPhysicalTableName(schema))
                + where
                + " GROUP BY " + col
                + " ORDER BY grp_count DESC";
        Map<String, Long> out = new LinkedHashMap<>();
        try (Connection c = schemaConnection(schema);
                PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Object key = rs.getObject(1);
                    String keyStr = key == null ? "" : String.valueOf(key);
                    out.put(keyStr, rs.getLong(2));
                }
            }
        }
        return out;
    }

    public Map<String, Object> listAdvanced(EntitySchema schema,
            int limit,
            int offset,
            String q,
            String fieldsParam,
            String sortParam,
            Map<String, Object> filters) throws SQLException {
        // Projection (preserve order, remove duplicates while keeping first occurrence)
        //
        // Review #7 (root cause / D5, D7) — fieldMap is built from
        // getQueryableFields() so an explicit fields=approval_status resolves
        // instead of being silently stripped (D5). An unrecognized name in an
        // EXPLICIT fields= list is now a 400 (D7), matching filter='s fail-closed
        // behavior — but the DEFAULT (no fields= supplied) projection below
        // deliberately keeps iterating schema.getFields() directly, not
        // getQueryableFields(): every list response must keep excluding the 8
        // approval columns unless a caller explicitly opts in, or approval
        // metadata would leak into every existing caller's response.
        List<String> projection = new ArrayList<>();
        Set<String> seenProj = new HashSet<>();
        Map<String, EntitySchema.Field> fieldMap = new HashMap<>();
        for (EntitySchema.Field f : getQueryableFields(schema)) {
            fieldMap.put(f.getName().toLowerCase(Locale.ROOT), f);
        }

        if (fieldsParam != null && !fieldsParam.isBlank()) {
            for (String fn : fieldsParam.split(",")) {
                String trimmed = fn.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                EntitySchema.Field f = fieldMap.get(trimmed.toLowerCase(Locale.ROOT));
                if (f == null) {
                    throw new FieldValidationException(trimmed, "unknown field");
                }
                String canonical = f.getName();
                if (seenProj.add(canonical.toLowerCase(Locale.ROOT))) {
                    projection.add(canonical);
                }
            }
        }

        if (projection.isEmpty()) { // default all — deliberately schema.getFields(), see comment above
            for (EntitySchema.Field f : schema.getFields()) {
                String canonical = f.getName();
                if (seenProj.add(canonical.toLowerCase(Locale.ROOT))) {
                    projection.add(canonical);
                }
            }
        }

        // WHERE
        StringBuilder where = new StringBuilder();
        List<Object> params = new ArrayList<>();
        buildWhere(schema, q, filters, where, params);

        // Count
        String countSql = "SELECT COUNT(*) FROM " + quote(SchemaManager.getPhysicalTableName(schema)) + where;
        long total;

        // Sorting
        List<String> orderParts = new ArrayList<>();
        Set<String> seenSort = new HashSet<>();
        if (sortParam != null && !sortParam.isBlank()) {
            for (String token : sortParam.split(",")) {
                String t = token.trim();
                if (t.isEmpty()) {
                    continue;
                }
                boolean desc = t.startsWith("-");
                String name = desc ? t.substring(1) : (t.startsWith("+") ? t.substring(1) : t);
                EntitySchema.Field f = fieldMap.get(name.toLowerCase(Locale.ROOT));
                if (f == null) {
                    // Review #7 (D4/D7) — used to `continue` (silently drop the sort
                    // token), returning 200 with "sort":[] and arbitrary order instead
                    // of the caller's requested one. Same fail-open hazard filter=
                    // already closed; fieldMap here also now includes the approval
                    // columns via getQueryableFields(), so a legitimate sort=submitted_at
                    // resolves instead of hitting this branch at all.
                    throw new FieldValidationException(name, "unknown sort field");
                }
                String key = f.getName().toLowerCase(Locale.ROOT);
                if (seenSort.add(key)) {
                    orderParts.add(quote(f.getName()) + (desc ? " DESC" : " ASC"));
                }
            }
        }
        String orderClause = orderParts.isEmpty() ? "" : (" ORDER BY " + String.join(", ", orderParts));

        // Projection list with alias to preserve casing
        List<String> selectCols = new ArrayList<>();
        for (String col : projection) {
            selectCols.add(quote(col) + " AS \"" + col + "\"");
        }

        String dataSql = "SELECT " + String.join(",", selectCols) + " FROM "
                + quote(SchemaManager.getPhysicalTableName(schema)) + where
                + orderClause + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";

        try (Connection c = schemaConnection(schema)) {
            try (PreparedStatement cps = c.prepareStatement(countSql)) {
                for (int i = 0; i < params.size(); i++) {
                    cps.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = cps.executeQuery()) {
                    rs.next();
                    total = rs.getLong(1);
                }
            }

            List<Map<String, Object>> rows;
            try (PreparedStatement dps = c.prepareStatement(dataSql)) {
                int idx = 1;
                for (Object p : params) {
                    dps.setObject(idx++, p);
                }
                dps.setInt(idx++, offset);
                dps.setInt(idx, limit);
                try (ResultSet rs = dps.executeQuery()) {
                    rows = toList(rs);
                }
            }

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("rows", rows);
            out.put("total", total);
            out.put("limit", limit);
            out.put("offset", offset);
            if (q != null && !q.isBlank()) {
                out.put("query", q);
            }
            if (fieldsParam != null && !fieldsParam.isBlank()) {
                out.put("fields", projection);
            }
            if (sortParam != null && !sortParam.isBlank()) {
                out.put("sort", orderParts);
            }
            if (filters != null && !filters.isEmpty()) {
                out.put("filters", filters);
            }
            return out;
        }
    }

    public Map<String, Object> insertBatch(EntitySchema schema, List<Map<String, Object>> batch) throws SQLException {
        // C4.6 follow-up — shared with insertRecordLegacy so the approval columns cannot be
        // dropped on one insert path and honoured on the other. See writableFields().
        List<EntitySchema.Field> insertable = writableFields(schema, batch);

        String cols = String.join(",", insertable.stream().map(f -> quote(f.getName())).toList());
        String placeholders = String.join(",", Collections.nCopies(insertable.size(), "?"));
        String sql = "INSERT INTO " + quote(SchemaManager.getPhysicalTableName(schema))
                + (insertable.isEmpty() ? " DEFAULT VALUES" : (" (" + cols + ") VALUES (" + placeholders + ")"));

        List<Long> ids = new ArrayList<>();
        try (Connection c = schemaConnection(schema);
                PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            c.setAutoCommit(false);
            for (Map<String, Object> row : batch) {
                int idx = 1;
                for (EntitySchema.Field f : insertable) {
                    String fieldName = f.getName();
                    Object raw = row.get(fieldName);
                    // Audit field auto-injection — mirrors insertRecordLegacy. Without this,
                    // rows written through the batch insert path (e.g. AI-generated mock data)
                    // silently persisted NULL created_at/updated_at while the single-record
                    // insert path always populated them, a discrepancy invisible until a caller
                    // actually displayed those columns.
                    if (raw == null || (raw instanceof String s && s.isBlank())) {
                        if ("created_at".equalsIgnoreCase(fieldName) || "updated_at".equalsIgnoreCase(fieldName)) {
                            raw = new Timestamp(System.currentTimeMillis());
                        }
                    }
                    Object val = coerceAndValidate(f, raw);
                    ps.setObject(idx++, val);
                }
                ps.addBatch();
            }
            ps.executeBatch();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                while (rs.next()) {
                    ids.add(rs.getLong(1));
                }
            } catch (SQLException ignore) {
            }
            c.commit();
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("inserted", batch.size());
        if (!ids.isEmpty()) {
            out.put("ids", ids);
        }
        return out;
    }

    // -------------------- Helpers (extracted as-is) --------------------

    private static String quote(String id) {
        if (id == null) {
            return null;
        }
        // PostgreSQL identifiers are case-sensitive when quoted.
        // We always use UPPERCASE to match the database's consistent naming convention.
        return '"' + id.toUpperCase(Locale.ROOT) + '"';
    }

    private static Object parseId(String idStr, EntitySchema.Field pk) {
        if (idStr == null || "null".equalsIgnoreCase(idStr.trim())) {
            return null;
        }
        String t = pk != null && pk.getType() != null ? pk.getType().toLowerCase(Locale.ROOT) : "string";
        String val = idStr.trim();
        LOG.info("[PARSE_ID] Input: '{}', Field: {}, Type: {}", val, (pk != null ? pk.getName() : "null"), t);

        try {
            return switch (t) {
                case "int", "integer", "serial" -> Integer.valueOf(val);
                case "long", "bigint", "bigserial" -> Long.valueOf(val);
                case "uuid" -> java.util.UUID.fromString(val);
                default -> {
                    // Fallback: If it's a digit-only string but we didn't match the type, 
                    // try parsing as Long if it looks like one, to avoid "bigint = varchar" mismatch
                    if (val.matches("\\d+")) {
                        try {
                            yield Long.valueOf(val);
                        } catch (NumberFormatException ignored) {}
                    }
                    yield val;
                }
            };
        } catch (Exception e) {
            LOG.warn("[PARSE_ID] Failed to parse ID '{}' as {}: {}", val, t, e.getMessage());
            // Last resort: If the DB expects a numeric, this String return will likely trigger an error,
            // but we'll see the warning in the logs.
            return val;
        }
    }

    private static List<Map<String, Object>> toList(ResultSet rs) throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        ResultSetMetaData md = rs.getMetaData();
        int cols = md.getColumnCount();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= cols; i++) {
                String name = md.getColumnLabel(i);
                Object val = rs.getObject(i);
                // Convert CLOB to String for JSON serialization
                if (val instanceof java.sql.Clob clob) {
                    val = clob.getSubString(1, (int) clob.length());
                }
                row.put(name, val);
            }
            list.add(row);
        }
        return list;
    }

    /**
     * Sprint 3 post-review fix — the outer coerceAndValidate() wraps the raw
     * validator so every downstream {@code IllegalArgumentException("field
     * 'X' <reason>")} surfaces as a typed {@link FieldValidationException}.
     *
     * <p>Structured errors let {@link ErrorHandler#fieldValidationError} skip
     * its brittle regex parser. The inner {@link #coerceAndValidateRaw} keeps
     * the historic messages verbatim so any legacy caller unaware of the
     * typed contract still sees the same string.
     */
    private static Object coerceAndValidate(EntitySchema.Field f, Object raw) {
        try {
            return coerceAndValidateRaw(f, raw);
        } catch (FieldValidationException fve) {
            throw fve;
        } catch (IllegalArgumentException iae) {
            String msg = iae.getMessage() != null ? iae.getMessage() : "";
            String prefix = "field '" + f.getName() + "' ";
            String reason = msg.startsWith(prefix) ? msg.substring(prefix.length()) : msg;
            throw new FieldValidationException(f.getName(), reason);
        }
    }

    private static Object coerceAndValidateRaw(EntitySchema.Field f, Object raw) {
        String t = f.getType().toLowerCase(Locale.ROOT);
        // required
        if (raw == null || (raw instanceof String s && s.isBlank() && !t.equals("string") && !t.equals("text"))) {
            if (f.isRequired()) {
                throw new IllegalArgumentException("field '" + f.getName() + "' is required");
            }
            return null;
        }
        try {
            // Review #4 (round 4 of the field-type-coercion defect family) —
            // dispatch on SchemaManager.classifyFieldType(), the single shared
            // type->kind mapping also consulted by sqlType() and
            // parseFilterValue() below, instead of maintaining a third
            // independent alias list here. This is what closes the "datetime"
            // blocker: it was missing from this switch and parseFilterValue's,
            // even though sqlType() already mapped it to TIMESTAMP.
            return switch (SchemaManager.classifyFieldType(t)) {
                case INTEGER, REFERENCE -> {
                    if (raw instanceof Number) {
                        long lv = ((Number) raw).longValue();
                        if (f.getMin() != null && lv < f.getMin())
                            throw new IllegalArgumentException("field '" + f.getName() + "' below min");
                        
                        // Strict validation only if max > min (handles AI-generated 0/0 defaults)
                        if (f.getMax() != null && (f.getMin() == null || f.getMax() > f.getMin())) {
                            if (lv > f.getMax())
                                throw new IllegalArgumentException("field '" + f.getName() + "' above max");
                        }
                        
                        // Check for overflow if strict int
                        if (lv > Integer.MAX_VALUE || lv < Integer.MIN_VALUE) {
                            throw new IllegalArgumentException("field '" + f.getName() + "' value " + lv
                                    + " exceeds integer range. Use 'Long' or 'String' for phone numbers.");
                        }
                        yield (int) lv;
                    }
                    try {
                        int iv = Integer.parseInt(raw.toString());
                        if (f.getMin() != null && iv < f.getMin())
                            throw new IllegalArgumentException("field '" + f.getName() + "' below min");
                        
                        if (f.getMax() != null && (f.getMin() == null || f.getMax() > f.getMin())) {
                            if (iv > f.getMax())
                                throw new IllegalArgumentException("field '" + f.getName() + "' above max");
                        }
                        yield iv;
                    } catch (NumberFormatException e) {
                        // Try parsing as Long to give better error message
                        try {
                            long lv = Long.parseLong(raw.toString());
                            throw new IllegalArgumentException("field '" + f.getName() + "' value " + lv
                                    + " exceeds integer range. Use 'Long' or 'String' for phone numbers.");
                        } catch (NumberFormatException ignored) {
                        }
                        throw new IllegalArgumentException("field '" + f.getName() + "' invalid integer format");
                    }
                }
                case BIGINT -> {
                    if (raw instanceof Number) {
                        long lv = ((Number) raw).longValue();
                        if (f.getMin() != null && lv < f.getMin())
                            throw new IllegalArgumentException("field '" + f.getName() + "' below min");
                        // Strict validation only if max > min (handles AI-generated 0/0 defaults)
                        if (f.getMax() != null && (f.getMin() == null || f.getMax() > f.getMin())) {
                            if (lv > f.getMax())
                                throw new IllegalArgumentException("field '" + f.getName() + "' above max");
                        }
                        yield lv;
                    }
                    long lv = Long.parseLong(raw.toString());
                    if (f.getMin() != null && lv < f.getMin())
                        throw new IllegalArgumentException("field '" + f.getName() + "' below min");
                    if (f.getMax() != null && (f.getMin() == null || f.getMax() > f.getMin())) {
                        if (lv > f.getMax())
                            throw new IllegalArgumentException("field '" + f.getName() + "' above max");
                    }
                    yield lv;
                }
                case DECIMAL -> {
                    // Coerce to BigDecimal so Postgres NUMERIC columns accept
                    // the bind. Accepts Number (JSON number literal) and String
                    // (form input or JSON string). min/max are compared as
                    // Long -> BigDecimal.
                    java.math.BigDecimal bd;
                    if (raw instanceof java.math.BigDecimal existing) {
                        bd = existing;
                    } else if (raw instanceof Number n) {
                        // Use String constructor via toString() to avoid the
                        // double-precision noise of BigDecimal.valueOf(double).
                        bd = new java.math.BigDecimal(n.toString());
                    } else {
                        String rs = raw.toString().trim();
                        if (rs.isEmpty()) yield null;
                        bd = new java.math.BigDecimal(rs);
                    }
                    if (f.getMin() != null
                            && bd.compareTo(java.math.BigDecimal.valueOf(f.getMin())) < 0)
                        throw new IllegalArgumentException("field '" + f.getName() + "' below min");
                    // Strict validation only if max > min (handles AI-generated 0/0 defaults)
                    if (f.getMax() != null && (f.getMin() == null || f.getMax() > f.getMin())) {
                        if (bd.compareTo(java.math.BigDecimal.valueOf(f.getMax())) > 0)
                            throw new IllegalArgumentException("field '" + f.getName() + "' above max");
                    }
                    yield bd;
                }
                case BOOLEAN -> {
                    if (raw instanceof Boolean) {
                        yield raw;
                    }
                    String s = raw.toString().toLowerCase(Locale.ROOT);
                    if ("true".equals(s) || "1".equals(s))
                        yield true;
                    if ("false".equals(s) || "0".equals(s))
                        yield false;
                    throw new IllegalArgumentException("field '" + f.getName() + "' invalid boolean");
                }
                case TIMESTAMP -> {
                    // if already a date/timestamp, just return
                    if (raw instanceof java.util.Date) {
                        if (raw instanceof Timestamp) yield raw;
                        yield new Timestamp(((java.util.Date) raw).getTime());
                    }
                    // accept epoch millis or ISO-8601
                    if (raw instanceof Number) {
                        yield new Timestamp(((Number) raw).longValue());
                    }
                    String rs = raw.toString();
                    if (rs.isBlank()) yield null;
                    try {
                        // Must be Timestamp.from(...), not the bare Instant — the JDBC
                        // driver's setObject() can't infer a SQL type for java.time.Instant
                        // and 500s with "Can't infer the SQL type to use for an instance of
                        // java.time.Instant" on every save of an untouched ISO-8601 TIMESTAMP
                        // field (e.g. re-saving a record whose upload_date/created_at came
                        // back from a prior GET in offset form). See parseFilterValue()'s
                        // TIMESTAMP case just below in this file for the same conversion.
                        yield Timestamp.from(Instant.parse(rs));
                    } catch (Exception ex) {
                        try {
                            // Try yyyy/MM/dd or yyyy-MM-dd
                            String clean = rs.replace("/", "-");
                            if (clean.length() == 10) {
                                yield java.sql.Date.valueOf(clean);
                            }
                            String withSpace = clean.replace("T", " ");
                            // Native HTML5 <input type="datetime-local"> always submits
                            // "yyyy-MM-ddTHH:mm" — no seconds, no offset. Timestamp.valueOf
                            // requires seconds, so pad them in rather than 500ing every edit
                            // of a datetime field made through the runtime's edit forms.
                            if (withSpace.matches("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}$")) {
                                withSpace = withSpace + ":00";
                            }
                            yield Timestamp.valueOf(withSpace);
                        } catch (Exception ex2) {
                            try {
                                long millis = Long.parseLong(rs);
                                yield new Timestamp(millis);
                            } catch (NumberFormatException nfe) {
                                throw new IllegalArgumentException("field '" + f.getName() + "' invalid format");
                            }
                        }
                    }
                }
                case TEXT, FILE, STRING -> {
                    // Covers "text"/"string" plus every alias that classifies as
                    // STRING (email, phone, status, file, and anything
                    // unrecognized) — all just need length/pattern validation.
                    String str = raw.toString();
                    if (f.getLength() != null && str.length() > f.getLength())
                        throw new IllegalArgumentException(
                                "field '" + f.getName() + "' length exceeds " + f.getLength());
                    if (f.getPattern() != null && !f.getPattern().isEmpty() && !"null".equals(f.getPattern())) {
                        if (!Pattern.compile(f.getPattern()).matcher(str).matches())
                            throw new IllegalArgumentException("field '" + f.getName() + "' does not match pattern");
                    }
                    yield str;
                }
            };
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException("field '" + f.getName() + "' invalid format");
        }
    }

    private static Connection schemaConnection(EntitySchema schema) throws SQLException {
        return JdbcManager.getConnection(schema != null ? schema.getDatasourceName() : null);
    }

    /**
     * Review #5 (High B) — true for any field-type alias that classifies as a plain
     * character column ({@code STRING} or {@code TEXT}), i.e. eligible for free-text
     * search / substring LIKE matching / random-UUID PK generation. Deliberately
     * excludes {@code FILE} (stores an issued fileId, not free text) and every
     * non-character kind. This is the single place the three former hand-written
     * {@code equals("string")||equals("text")||equals("varchar")} lists now go through,
     * so "longtext"/"email"/"phone"/"status" (all STRING/TEXT-kind aliases) are no
     * longer silently excluded from search/LIKE just because they weren't spelled
     * "string"/"text"/"varchar" verbatim.
     */
    private static boolean isCharacterKind(String type) {
        SchemaManager.FieldSqlKind kind = SchemaManager.classifyFieldType(type);
        return kind == SchemaManager.FieldSqlKind.STRING || kind == SchemaManager.FieldSqlKind.TEXT;
    }

    private static Object parseFilterValue(EntitySchema.Field f, String v) {
        String t = f.getType().toLowerCase(Locale.ROOT);
        try {
            // Review #4 — same shared classifyFieldType() dispatch as
            // coerceAndValidateRaw()/sqlType(); see the comment there. This also
            // closes High-A for dates specifically: an unparseable
            // date/timestamp/datetime value used to fall back to the raw String
            // (`yield v`) instead of null, which was the one kind that DIDN'T get
            // treated as a parse failure — it just bound the literal string
            // against a TIMESTAMP column (500) or slipped past as a literal.
            // Every kind now fails the same way — null — which parseFilters()
            // turns into a 400 instead of a silently dropped predicate.
            return switch (SchemaManager.classifyFieldType(t)) {
                case INTEGER, REFERENCE -> Integer.parseInt(v);
                case BIGINT -> Long.parseLong(v);
                case DECIMAL -> new java.math.BigDecimal(v);
                case BOOLEAN -> ("true".equalsIgnoreCase(v) || "1".equals(v));
                case TIMESTAMP -> Timestamp.from(Instant.parse(v));
                case TEXT, FILE, STRING -> v;
            };
        } catch (Exception e) {
            // If parsing fails for a typed field (e.g. integer), return null to indicate
            // invalid filter
            // Do NOT return valid String 'v' because it will cause DB Type Mismatch (int
            // column = varchar param)
            return null;
        }
    }

    private static void buildWhere(EntitySchema schema,
            String q,
            Map<String, Object> filters,
            StringBuilder where,
            List<Object> params) {
        List<String> parts = new ArrayList<>();
        LOG.debug("[BUILD_WHERE] Building WHERE clause. q={}, filters={}", q, filters);

        if (q != null && !q.isBlank()) {
            String uq = q.trim().toUpperCase(Locale.ROOT);
            List<String> likeParts = new ArrayList<>();
            for (EntitySchema.Field f : schema.getFields()) {
                String t = f.getType().toLowerCase(Locale.ROOT);
                if (isCharacterKind(t)) {
                    likeParts.add("UPPER(" + quote(f.getName()) + ") LIKE ?");
                    params.add("%" + uq + "%");
                }
            }
            if (!likeParts.isEmpty()) {
                parts.add("(" + String.join(" OR ", likeParts) + ")");
            }
        }

        if (filters != null && !filters.isEmpty()) {
            Map<String, EntitySchema.Field> fieldMap = new HashMap<>();
            for (EntitySchema.Field f : getQueryableFields(schema)) {
                fieldMap.put(f.getName().toLowerCase(Locale.ROOT), f);
            }

            for (Map.Entry<String, Object> e : filters.entrySet()) {
                LOG.debug("[BUILD_WHERE] Processing filter entry: key={}, value={}", e.getKey(), e.getValue());
                EntitySchema.Field f = fieldMap.get(e.getKey().toLowerCase(Locale.ROOT));
                if (f == null) {
                    // Review #7 (root cause) — fieldMap above already folds in the 8
                    // approval columns as typed fields (getQueryableFields()), so this
                    // is a genuinely unknown column (or an approval column on an entity
                    // without the approval workflow enabled). parseFilters() is the
                    // fail-closed gate for a caller-supplied filter= string; anything
                    // that reaches buildWhere() with a key this map doesn't recognize
                    // was added by a different caller (e.g. applyApprovalStatusFilter)
                    // that is trusted to only use real column names, so skipping here —
                    // rather than throwing — is intentional defense-in-depth, not a
                    // second fail-open hole.
                    LOG.debug("[BUILD_WHERE] Field '{}' not found in schema, skipping", e.getKey());
                    continue;
                }

                // C3.9 — unwrap an explicit exact-match request before any of the
                // type handling below looks at the value.
                boolean exactRequested = false;
                Object filterValue = e.getValue();
                if (filterValue instanceof ExactMatch em) {
                    exactRequested = true;
                    filterValue = em.value();
                } else if (filterValue instanceof Range range) {
                    // A range is always a comparison, never a LIKE/exact match —
                    // handled entirely separately from the rest of this loop body.
                    String quotedRangeKey = quote(f.getName());
                    if (range.min() != null) {
                        parts.add(quotedRangeKey + " >= ?");
                        params.add(range.min());
                    }
                    if (range.max() != null) {
                        parts.add(quotedRangeKey + " <= ?");
                        params.add(range.max());
                    }
                    continue;
                }

                String t = f.getType().toLowerCase(Locale.ROOT);
                // Review #4 — the date/timestamp-specific Instant.parse pre-check
                // that used to live here is now dead code: parseFilters(), the
                // only normal producer of this map, already rejects an
                // unparseable date/timestamp/datetime value with a 400 before
                // buildWhere() ever runs. Removed rather than left stale.
                String quotedKey = quote(f.getName());

                // If filter value is NULL (parsing failed), we skip it to avoid DB errors
                if (filterValue == null) {
                    LOG.warn("[BUILD_WHERE] Filter value for '{}' is null (parsing failed?), skipping predicate.",
                            e.getKey());
                    continue;
                }

                // Coerce filter value to match column type if it's a string from JSON
                Object finalValue = filterValue;
                if (finalValue instanceof String sVal) {
                    Object parsed = parseFilterValue(f, sVal);
                    if (parsed == null) {
                        // C3.10 — parseFilterValue returns null when a typed column's
                        // value fails to parse (e.g. filter=amount:=abc against a decimal
                        // column). Falling back to the raw String here would bind a
                        // String against a typed SQL column and 500; skip the predicate
                        // instead, matching how the date/timestamp branch above already
                        // behaves.
                        LOG.debug("[BUILD_WHERE] Skipping filter for '{}' — value '{}' does not parse as type '{}'",
                                e.getKey(), sVal, t);
                        continue;
                    }
                    finalValue = parsed;
                }

                // Use LIKE with wildcards for string/text fields, exact match for
                // others — or when the caller explicitly asked for exact.
                if (!exactRequested && isCharacterKind(t)) {
                    // Case-insensitive LIKE match for string fields
                    LOG.debug("[BUILD_WHERE] Adding LIKE filter condition: UPPER({}) LIKE ? (param: %{}%)", quotedKey,
                            finalValue);
                    parts.add("UPPER(" + quotedKey + ") LIKE ?");
                    params.add("%" + String.valueOf(finalValue).toUpperCase(Locale.ROOT) + "%");
                } else {
                    LOG.debug("[BUILD_WHERE] Adding exact filter condition: {} = ? (param: {} [type: {}])", quotedKey,
                            finalValue, finalValue != null ? finalValue.getClass().getSimpleName() : "null");
                    parts.add(quotedKey + " = ?");
                    params.add(finalValue);
                }
            }
        }

        if (!parts.isEmpty()) {
            where.append(" WHERE ").append(String.join(" AND ", parts));
            LOG.debug("[BUILD_WHERE] Final WHERE clause: {}", where);
            LOG.debug("[BUILD_WHERE] Final params: {}", params);
        }
    }
}
