package com.appbana.service;

import com.appbana.JdbcManager;
import com.appbana.SchemaManager;
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

    private Object insertRecordLegacy(EntitySchema schema, Map<String, Object> data) throws SQLException {
        List<EntitySchema.Field> fields = schema.getFields();
        List<String> cols = new ArrayList<>();
        List<String> placeholders = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        for (EntitySchema.Field field : fields) {
            if (field.isPrimaryKey()) {
                if (field.isAutoIncrement()) {
                    continue; // skip if auto
                }
                // Generate UUID if missing and type is compatible
                if (!data.containsKey(field.getName())) {
                    String type = field.getType().toLowerCase(Locale.ROOT);
                    if (type.equals("string") || type.equals("text") || type.equals("uuid") || type.equals("varchar")) {
                        data.put(field.getName(), java.util.UUID.randomUUID().toString());
                    }
                }
            }
            cols.add(quote(field.getName()));
            placeholders.add("?");
            Object raw = data.get(field.getName());
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
                    return rs.getObject(1);
                }
            }

            // If no generated key returned (e.g. client provided UUID), return PK
            EntitySchema.Field pk = schema.getFields().stream().filter(EntitySchema.Field::isPrimaryKey).findFirst()
                    .orElse(null);
            if (pk != null && data.containsKey(pk.getName())) {
                return data.get(pk.getName());
            }
        }
        return -1L;
    }

    public List<Map<String, Object>> listAll(EntitySchema schema) throws SQLException {
        String sql = "SELECT * FROM " + quote(SchemaManager.getPhysicalTableName(schema));
        try (Connection c = schemaConnection(schema);
                PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            return toList(rs);
        }
    }

    public Map<String, Object> getById(EntitySchema schema, String id) throws SQLException {
        EntitySchema.Field pk = schema.getFields().stream().filter(EntitySchema.Field::isPrimaryKey).findFirst()
                .orElse(null);
        if (pk == null) {
            return null;
        }
        String sql = "SELECT * FROM " + quote(SchemaManager.getPhysicalTableName(schema)) + " WHERE "
                + quote(pk.getName()) + " = ?";
        try (Connection c = schemaConnection(schema);
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, parseId(id, pk));
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> list = toList(rs);
                return list.isEmpty() ? null : list.getFirst();
            }
        }
    }

    public int updateById(EntitySchema schema, String id, Map<String, Object> data) throws SQLException {
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

    public Map<String, Object> parseFilters(String raw, EntitySchema schema) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            LOG.info("[FILTER] No filter string provided");
            return map;
        }
        // URL-decode the filter string to handle spaces encoded as + or %20
        try {
            raw = java.net.URLDecoder.decode(raw, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOG.warn("[FILTER] Failed to URL-decode filter string, using as-is: {}", raw);
        }
        LOG.info("[FILTER] Parsing filter string: {}", raw);
        String[] pairs = raw.split(",");
        Map<String, EntitySchema.Field> fieldMap = new HashMap<>();
        for (EntitySchema.Field f : schema.getFields()) {
            fieldMap.put(f.getName().toLowerCase(Locale.ROOT), f);
            LOG.info("[FILTER] Schema field: {} (lowercased: {})", f.getName(), f.getName().toLowerCase(Locale.ROOT));
        }
        for (String p : pairs) {
            int idx = p.indexOf(":");
            if (idx <= 0) {
                LOG.info("[FILTER] Skipping invalid pair (no colon or at start): {}", p);
                continue;
            }
            String name = p.substring(0, idx).trim();
            String val = p.substring(idx + 1).trim();
            if (name.isEmpty()) {
                LOG.info("[FILTER] Skipping pair with empty name");
                continue;
            }
            LOG.info("[FILTER] Looking up field '{}' (lowercased: '{}') in schema", name,
                    name.toLowerCase(Locale.ROOT));
            EntitySchema.Field f = fieldMap.get(name.toLowerCase(Locale.ROOT));
            if (f == null) {
                LOG.info("[FILTER] Field '{}' not found in schema, skipping", name);
                continue; // ignore unknown
            }
            Object parsed = parseFilterValue(f, val);
            LOG.info("[FILTER] Parsed filter: {}={} (type: {}, parsed value: {})", f.getName(), val, f.getType(),
                    parsed);
            map.put(f.getName(), parsed); // canonical
        }
        LOG.info("[FILTER] Final filter map: {}", map);
        return map;
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

    public Map<String, Object> listAdvanced(EntitySchema schema,
            int limit,
            int offset,
            String q,
            String fieldsParam,
            String sortParam,
            Map<String, Object> filters) throws SQLException {
        // Projection (preserve order, remove duplicates while keeping first occurrence)
        List<String> projection = new ArrayList<>();
        Set<String> seenProj = new HashSet<>();
        Map<String, EntitySchema.Field> fieldMap = new HashMap<>();
        for (EntitySchema.Field f : schema.getFields()) {
            fieldMap.put(f.getName().toLowerCase(Locale.ROOT), f);
        }

        if (fieldsParam != null && !fieldsParam.isBlank()) {
            for (String fn : fieldsParam.split(",")) {
                String trimmed = fn.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                EntitySchema.Field f = fieldMap.get(trimmed.toLowerCase(Locale.ROOT));
                if (f != null) {
                    String canonical = f.getName();
                    if (seenProj.add(canonical.toLowerCase(Locale.ROOT))) {
                        projection.add(canonical);
                    }
                }
            }
        }

        if (projection.isEmpty()) { // default all
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
                    continue;
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
        List<EntitySchema.Field> fields = schema.getFields();
        List<EntitySchema.Field> insertable = new ArrayList<>();
        for (EntitySchema.Field f : fields) {
            if (f.isPrimaryKey() && f.isAutoIncrement()) {
                continue;
            }
            insertable.add(f);
        }

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
                    Object raw = row.get(f.getName());
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
        // We preserve the case as-is to match the actual table/column names.
        return '"' + id + '"';
    }

    private static Object parseId(String idStr, EntitySchema.Field pk) {
        if (idStr == null) {
            return null;
        }
        String t = pk.getType().toLowerCase(Locale.ROOT);
        try {
            return switch (t) {
                case "int", "integer" -> Integer.valueOf(idStr.trim());
                case "long" -> Long.valueOf(idStr.trim());
                case "uuid" -> java.util.UUID.fromString(idStr.trim());
                default -> idStr;
            };
        } catch (Exception e) {
            LOG.warn("[PARSE_ID] Failed to parse ID '{}' as {}: {}", idStr, t, e.getMessage());
            return idStr;
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

    private static Object coerceAndValidate(EntitySchema.Field f, Object raw) {
        // required
        if (raw == null) {
            if (f.isRequired()) {
                throw new IllegalArgumentException("field '" + f.getName() + "' is required");
            }
            return null;
        }
        String t = f.getType().toLowerCase(Locale.ROOT);
        try {
            return switch (t) {
                case "int", "integer" -> {
                    if (raw instanceof Number) {
                        long lv = ((Number) raw).longValue();
                        if (f.getMin() != null && lv < f.getMin())
                            throw new IllegalArgumentException("field '" + f.getName() + "' below min");
                        if (f.getMax() != null && lv > f.getMax())
                            throw new IllegalArgumentException("field '" + f.getName() + "' above max");
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
                        if (f.getMax() != null && iv > f.getMax())
                            throw new IllegalArgumentException("field '" + f.getName() + "' above max");
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
                case "long" -> {
                    if (raw instanceof Number) {
                        long lv = ((Number) raw).longValue();
                        if (f.getMin() != null && lv < f.getMin())
                            throw new IllegalArgumentException("field '" + f.getName() + "' below min");
                        if (f.getMax() != null && lv > f.getMax())
                            throw new IllegalArgumentException("field '" + f.getName() + "' above max");
                        yield lv;
                    }
                    long lv = Long.parseLong(raw.toString());
                    if (f.getMin() != null && lv < f.getMin())
                        throw new IllegalArgumentException("field '" + f.getName() + "' below min");
                    if (f.getMax() != null && lv > f.getMax())
                        throw new IllegalArgumentException("field '" + f.getName() + "' above max");
                    yield lv;
                }
                case "boolean" -> {
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
                case "date", "timestamp" -> {
                    // accept epoch millis or ISO-8601
                    if (raw instanceof Number) {
                        yield new Timestamp(((Number) raw).longValue());
                    }
                    String rs = raw.toString();
                    try {
                        Instant inst = Instant.parse(rs);
                        yield Timestamp.from(inst);
                    } catch (Exception ex) {
                        long millis = Long.parseLong(rs);
                        yield new Timestamp(millis);
                    }
                }
                case "text", "string" -> {
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
                default -> {
                    // string/text or other unhandled types
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

    private static Object parseFilterValue(EntitySchema.Field f, String v) {
        String t = f.getType().toLowerCase(Locale.ROOT);
        try {
            return switch (t) {
                case "int", "integer" -> Integer.parseInt(v);
                case "long" -> Long.parseLong(v);
                case "boolean" -> ("true".equalsIgnoreCase(v) || "1".equals(v));
                case "date", "timestamp" -> {
                    // Accept only valid ISO-8601 instant strings; if parsing fails treat as raw
                    try {
                        yield Timestamp.from(Instant.parse(v));
                    } catch (Exception ignored) {
                        yield v;
                    }
                }
                default -> v;
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
        LOG.info("[BUILD_WHERE] Building WHERE clause. q={}, filters={}", q, filters);

        if (q != null && !q.isBlank()) {
            String uq = q.trim().toUpperCase(Locale.ROOT);
            List<String> likeParts = new ArrayList<>();
            for (EntitySchema.Field f : schema.getFields()) {
                String t = f.getType().toLowerCase(Locale.ROOT);
                if (t.equals("string") || t.equals("text") || t.equals("varchar")) {
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
            for (EntitySchema.Field f : schema.getFields()) {
                fieldMap.put(f.getName().toLowerCase(Locale.ROOT), f);
            }

            for (Map.Entry<String, Object> e : filters.entrySet()) {
                LOG.info("[BUILD_WHERE] Processing filter entry: key={}, value={}", e.getKey(), e.getValue());
                EntitySchema.Field f = fieldMap.get(e.getKey().toLowerCase(Locale.ROOT));
                if (f == null) {
                    LOG.info("[BUILD_WHERE] Field '{}' not found in schema, skipping", e.getKey());
                    continue; // unknown
                }

                String t = f.getType().toLowerCase(Locale.ROOT);
                if ((t.equalsIgnoreCase("date") || t.equalsIgnoreCase("timestamp"))
                        && e.getValue() instanceof String sVal) {
                    boolean valid = false;
                    try {
                        Instant.parse(sVal);
                        valid = true;
                    } catch (Exception ignored) {
                    }
                    if (!valid) {
                        LOG.info("[BUILD_WHERE] Skipping invalid date/timestamp filter: {}", sVal);
                        continue; // skip predicate, prevents DB parse error
                    }
                }

                String quotedKey = quote(f.getName());

                // If filter value is NULL (parsing failed), we skip it to avoid DB errors
                if (e.getValue() == null) {
                    LOG.warn("[BUILD_WHERE] Filter value for '{}' is null (parsing failed?), skipping predicate.",
                            e.getKey());
                    continue;
                }

                // Coerce filter value to match column type if it's a string from JSON
                Object finalValue = e.getValue();
                if (finalValue instanceof String sVal) {
                    Object parsed = parseFilterValue(f, sVal);
                    if (parsed != null) {
                        finalValue = parsed;
                    }
                }

                // Use LIKE with wildcards for string/text fields, exact match for others
                if (t.equals("string") || t.equals("text") || t.equals("varchar")) {
                    // Case-insensitive LIKE match for string fields
                    LOG.info("[BUILD_WHERE] Adding LIKE filter condition: UPPER({}) LIKE ? (param: %{}%)", quotedKey,
                            finalValue);
                    parts.add("UPPER(" + quotedKey + ") LIKE ?");
                    params.add("%" + String.valueOf(finalValue).toUpperCase(Locale.ROOT) + "%");
                } else {
                    LOG.info("[BUILD_WHERE] Adding exact filter condition: {} = ? (param: {} [type: {}])", quotedKey,
                            finalValue, finalValue != null ? finalValue.getClass().getSimpleName() : "null");
                    parts.add(quotedKey + " = ?");
                    params.add(finalValue);
                }
            }
        }

        if (!parts.isEmpty()) {
            where.append(" WHERE ").append(String.join(" AND ", parts));
            LOG.info("[BUILD_WHERE] Final WHERE clause: {}", where);
            LOG.info("[BUILD_WHERE] Final params: {}", params);
        }
    }
}
