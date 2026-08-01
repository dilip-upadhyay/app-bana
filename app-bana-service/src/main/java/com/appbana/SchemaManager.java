package com.appbana;

import com.appbana.config.AppConfig;
import com.appbana.config.ConfigManager;
import com.appbana.config.DatasourceConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.appbana.approval.ApprovalColumns;
import com.appbana.model.EntitySchema;

import java.io.IOException;
import java.sql.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SchemaManager {
    private static final Logger LOG = LoggerFactory.getLogger(SchemaManager.class);
    private static final ObjectMapper M = new ObjectMapper();

    public static void init() {
        // ensure meta table for active datasource only (others lazy)
        JdbcManager.ensureMetaTable();
    }

    private static List<DatasourceConfig> allDatasources() {
        AppConfig cfg = ConfigManager.getConfig();
        if (cfg.getDatasources() != null && !cfg.getDatasources().isEmpty())
            return cfg.getDatasources();
        // synthesize single datasource from root config for backwards compatibility
        DatasourceConfig ds = new DatasourceConfig();
        ds.setName(cfg.getName());
        ds.setJdbcUrl(cfg.getJdbcUrl());
        ds.setUsername(cfg.getUsername());
        ds.setPassword(cfg.getPassword());
        ds.setDriver(cfg.getDriver());
        return List.of(ds);
    }

    private static DatasourceConfig resolveTarget(EntitySchema schema) {
        String target = schema.getDatasourceName();
        AppConfig cfg = ConfigManager.getConfig();
        if (target == null || target.isBlank()) {
            // fallback active datasource
            String active = cfg.getActiveDatasource();
            if (cfg.getDatasources() != null) {
                for (DatasourceConfig ds : cfg.getDatasources()) {
                    if (ds.getName() != null && ds.getName().equals(active))
                        return ds;
                }
            }
            // legacy single
            if (cfg.getDatasources() != null && !cfg.getDatasources().isEmpty())
                return cfg.getDatasources().get(0);
            DatasourceConfig ds = new DatasourceConfig();
            ds.setName(cfg.getName());
            ds.setJdbcUrl(cfg.getJdbcUrl());
            ds.setUsername(cfg.getUsername());
            ds.setPassword(cfg.getPassword());
            ds.setDriver(cfg.getDriver());
            return ds;
        }
        // explicit name
        if (cfg.getDatasources() != null) {
            for (DatasourceConfig ds : cfg.getDatasources()) {
                if (target.equals(ds.getName()))
                    return ds;
            }
        }
        // Legacy single-datasource fallback: config.json may declare a top-level `name`
        // + connection fields (with `datasources: []`). Match that too instead of throwing —
        // otherwise schemas saved with datasourceName="default" can never be re-saved.
        if (target.equals(cfg.getName())) {
            DatasourceConfig ds = new DatasourceConfig();
            ds.setName(cfg.getName());
            ds.setJdbcUrl(cfg.getJdbcUrl());
            ds.setUsername(cfg.getUsername());
            ds.setPassword(cfg.getPassword());
            ds.setDriver(cfg.getDriver());
            return ds;
        }
        throw new IllegalArgumentException("datasource not found: " + target);
    }

    // Detect current dialect based on datasource (previously active only)
    private static String dialect(DatasourceConfig ds) {
        if (ds == null)
            return "h2";
        String type = ds.getType();
        String url = ds.getJdbcUrl();
        if (type != null && !type.isBlank())
            return type.toLowerCase();
        if (url == null)
            return "h2";
        if (url.startsWith("jdbc:postgresql:"))
            return "postgres";
        if (url.startsWith("jdbc:mysql:"))
            return "mysql";
        if (url.startsWith("jdbc:mariadb:"))
            return "mariadb";
        if (url.startsWith("jdbc:sqlserver:"))
            return "mssql";
        if (url.startsWith("jdbc:oracle:"))
            return "oracle";
        if (url.startsWith("jdbc:sqlite:"))
            return "sqlite";
        return "h2";
    }

    public static void saveSchema(EntitySchema schema) {
        validateSchema(schema);
        LOG.info("[SAVE-SCHEMA] Saving schema: name={}, appId={}, tenantId={}",
                schema.getName(), schema.getAppId(), schema.getTenantId());
        LOG.debug("[SAVE-SCHEMA] Schema fields: {}", schema.getFields().size());
        DatasourceConfig ds = resolveTarget(schema);
        String dsName = ds.getName();
        JdbcManager.ensureMetaTableFor(dsName);
        try (Connection c = JdbcManager.getConnection(dsName)) {
            String json = M.writeValueAsString(schema);
            String d = dialect(ds);

            // Extract tenant_id and app_id (required by V10 migration NOT NULL constraint)
            String tenantId = (schema.getTenantId() != null && !schema.getTenantId().isBlank())
                    ? schema.getTenantId()
                    : "default";
            String appId = (schema.getAppId() != null && !schema.getAppId().isBlank())
                    ? schema.getAppId()
                    : "default";

            // upsert by name, tenant_id, app_id; NOTE: name must be unique per tenant/app
            if ("postgres".equals(d) || "postgresql".equals(d)) {
                String upsert = "INSERT INTO appbana_schemas(name, json, tenant_id, app_id) VALUES (?, ?, ?, ?) " +
                        "ON CONFLICT (name) DO UPDATE SET json = EXCLUDED.json, tenant_id = EXCLUDED.tenant_id, app_id = EXCLUDED.app_id";
                try (PreparedStatement ps = c.prepareStatement(upsert)) {
                    ps.setString(1, getUniqueSchemaKey(schema));
                    ps.setString(2, json);
                    ps.setString(3, tenantId);
                    ps.setString(4, appId);
                    ps.executeUpdate();
                }
            } else {
                // Use PostgreSQL's INSERT ... ON CONFLICT syntax (upsert)
                String upsert = "INSERT INTO appbana_schemas (name, json, tenant_id, app_id) " +
                        "VALUES (?, ?, ?, ?) " +
                        "ON CONFLICT (name) DO UPDATE SET " +
                        "json = EXCLUDED.json, " +
                        "tenant_id = EXCLUDED.tenant_id, " +
                        "app_id = EXCLUDED.app_id";
                try (PreparedStatement ps = c.prepareStatement(upsert)) {
                    ps.setString(1, getUniqueSchemaKey(schema));
                    ps.setString(2, json);
                    ps.setString(3, tenantId);
                    ps.setString(4, appId);
                    ps.executeUpdate();
                } catch (SQLException e) {
                    try (PreparedStatement ins = c
                            .prepareStatement(
                                    "INSERT INTO appbana_schemas(name, json, tenant_id, app_id) VALUES (?, ?, ?, ?)")) {
                        ins.setString(1, getUniqueSchemaKey(schema));
                        ins.setString(2, json);
                        ins.setString(3, tenantId);
                        ins.setString(4, appId);
                        ins.executeUpdate();
                    } catch (SQLException dup) {
                        try (PreparedStatement upd = c
                                .prepareStatement(
                                        "UPDATE appbana_schemas SET json = ?, tenant_id = ?, app_id = ? WHERE name = ?")) {
                            upd.setString(1, json);
                            upd.setString(2, tenantId);
                            upd.setString(3, appId);
                            upd.setString(4, getUniqueSchemaKey(schema));
                            upd.executeUpdate();
                            LOG.info("[SAVE-SCHEMA] Calling ensureTable for schema: {}", schema.getName());
                        }
                    }
                }
            }
            ensureTable(schema, c, ds);
        } catch (SQLException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String getUniqueSchemaKey(EntitySchema schema) {
        if (schema.getAppId() != null && !schema.getAppId().isBlank()) {
            String tenantPart = (schema.getTenantId() != null && !schema.getTenantId().isBlank())
                    ? schema.getTenantId()
                    : "default";
            return tenantPart + "_" + schema.getAppId() + "_" + schema.getName();
        }
        return schema.getName();
    }

    public static EntitySchema loadSchema(String appId, String entityName, String tenantId) {
        String effectiveTenantId = (tenantId != null && !tenantId.isBlank()) ? tenantId : "default";
        String key = (appId != null && !appId.isBlank())
                ? (effectiveTenantId + "_" + appId + "_" + entityName)
                : entityName;
        return loadSchema(key);
    }

    public static EntitySchema loadSchema(String name) {
        // search all datasources until found
        for (DatasourceConfig ds : allDatasources()) {
            String dsName = ds.getName();
            JdbcManager.ensureMetaTableFor(dsName);
            try (Connection c = JdbcManager.getConnection(dsName)) {
                // Query by name only (for backward compatibility with non-tenant schemas)
                try (PreparedStatement ps = c
                        .prepareStatement("SELECT json, tenant_id, app_id FROM appbana_schemas WHERE name = ?")) {
                    ps.setString(1, name);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            String json = rs.getString(1);
                            EntitySchema schema = M.readValue(json, EntitySchema.class);
                            if (schema.getDatasourceName() == null || schema.getDatasourceName().isBlank())
                                schema.setDatasourceName(dsName); // backfill
                            // Backfill tenant_id and app_id from database columns if not in JSON
                            if (schema.getTenantId() == null || schema.getTenantId().isBlank()) {
                                schema.setTenantId(rs.getString(2));
                            }
                            if (schema.getAppId() == null || schema.getAppId().isBlank()) {
                                schema.setAppId(rs.getString(3));
                            }
                            // One-shot self-heal: ensure DB columns match schema on first load per JVM.
                            // Handles drift where legacy tables have differently-cased/spaced columns.
                            if (ENSURED_ONCE.add(name)) {
                                try {
                                    ensureTable(schema, c, ds);
                                } catch (SQLException healEx) {
                                    LOG.warn("[LOAD-SCHEMA] Self-heal ensureTable failed for {}: {}",
                                            name, healEx.getMessage());
                                }
                            }
                            return schema;
                        }
                    }
                }
            } catch (SQLException | IOException e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    /** Schema keys already ensured this JVM lifetime; keeps loadSchema cheap after first access. */
    private static final java.util.Set<String> ENSURED_ONCE = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static void ensureTable(EntitySchema schema, Connection c, DatasourceConfig dsCfg) throws SQLException {
        String table = getPhysicalTableName(schema);
        DatabaseMetaData md = c.getMetaData();
        LOG.info("[ENSURE-TABLE] Table does not exist, creating: {}", table);
        String d = dialect(dsCfg);
        try (ResultSet tables = md.getTables(null, null, table.toUpperCase(), null)) {
            LOG.info("[ENSURE-TABLE] Table exists, checking for schema updates: {}", table);
            boolean exists = tables.next();
            if (!exists) {
                createTable(schema, c, d);
            } else {
                Map<String, ColumnInfo> existing = new HashMap<>();
                try (ResultSet cols = md.getColumns(null, null, table.toUpperCase(), null)) {
                    while (cols.next()) {
                        String colName = cols.getString("COLUMN_NAME");
                        String typeName = cols.getString("TYPE_NAME");
                        int size = cols.getInt("COLUMN_SIZE");
                        existing.put(colKey(colName), new ColumnInfo(colName, typeName, size));
                    }
                }

                // Handle schema evolution for user-defined fields
                for (EntitySchema.Field f : schema.getFields()) {
                    String target = f.getName();
                    String targetLower = colKey(target);
                    if (f.getExistingName() != null && !f.getExistingName().isEmpty()) {
                        String old = f.getExistingName();
                        if (existing.containsKey(colKey(old)) && !existing.containsKey(targetLower)) {
                            String renameSql = "ALTER TABLE " + quote(table) + " ALTER COLUMN " + quote(old)
                                    + " RENAME TO " + quote(target);
                            try (Statement s = c.createStatement()) {
                                s.execute(renameSql);
                                recordMigration(c, schema.getName(), renameSql);
                                ColumnInfo info = existing.remove(colKey(old));
                                existing.put(targetLower, new ColumnInfo(target, info.typeName, info.size));
                            }
                        }
                    }
                    // Self-healing rename: DB column matches case/space-insensitively but its
                    // actual name differs from the schema field (e.g. legacy "START DATE" vs
                    // current "START_DATE"). Rename so quoted SELECTs resolve in Postgres.
                    if (existing.containsKey(targetLower)) {
                        ColumnInfo info = existing.get(targetLower);
                        if (!info.name.equals(target)) {
                            String renameCaseSql = "ALTER TABLE " + quote(table) + " RENAME COLUMN "
                                    + quote(info.name) + " TO " + quote(target);
                            try (Statement s = c.createStatement()) {
                                s.execute(renameCaseSql);
                                recordMigration(c, schema.getName(), renameCaseSql);
                                existing.put(targetLower, new ColumnInfo(target, info.typeName, info.size));
                                LOG.info("[ENSURE-TABLE] Renamed column for casing: {} -> {}", info.name, target);
                            } catch (SQLException caseEx) {
                                LOG.warn("[ENSURE-TABLE] Casing rename failed for {}.{} -> {}: {}",
                                        table, info.name, target, caseEx.getMessage());
                            }
                        }
                    }
                    if (!existing.containsKey(targetLower)) {
                        String alter = "ALTER TABLE " + quote(table) + " ADD " + quote(f.getName()) + " "
                                + sqlType(f, d);
                        try (Statement s = c.createStatement()) {
                            s.execute(alter);
                            recordMigration(c, schema.getName(), alter);
                        }
                    } else {
                        ColumnInfo info = existing.get(targetLower);
                        String desiredType = normalizeSqlType(sqlType(f, d));
                        String currentType = normalizeSqlType(
                                info.typeName + (info.size > 0 ? "(" + info.size + ")" : ""));
                        if (!typesEquivalent(currentType, desiredType)) {
                            String targetSqlType = sqlType(f, d, true);
                            String alterType;

                            if ("postgres".equals(d) || "postgresql".equals(d)) {
                                // Add USING clause for explicit casting
                                alterType = "ALTER TABLE " + quote(table) + " ALTER COLUMN " + quote(info.name)
                                        + " TYPE "
                                        + targetSqlType + " USING " + quote(info.name) + "::" + targetSqlType
                                                .replaceAll("SERIAL", "INTEGER").replaceAll("BIGSERIAL", "BIGINT");
                            } else {
                                alterType = "ALTER TABLE " + quote(table) + " ALTER COLUMN " + quote(info.name)
                                        + " SET DATA TYPE " + targetSqlType;
                            }

                            try (Statement s = c.createStatement()) {
                                s.execute(alterType);
                                recordMigration(c, schema.getName(), alterType);
                            }
                        }
                    }
                }

                // C4.6 — reconcile the approval columns on an EXISTING table.
                syncApprovalColumns(schema, c, d, table, existing);
            }
        }
        // H4 hardening — enforce declared FK relationships at the DB level.
        // Run after column reconciliation so target columns are guaranteed to
        // exist. Non-fatal on failure: a missing parent table just means the
        // FK will be added on the next ensureTable for the child.
        syncForeignKeys(schema, c, d);

        // Non-fatal: an indexing failure is a performance issue, never a
        // correctness one, so it must not block the schema save.
        syncIndexes(schema, c, d, table);
    }

    /**
     * Creates a supporting index for every indexable field so the runtime's
     * per-column filter/sort UI and the approval queues stay index-backed as
     * tables grow. Both families are {@code IF NOT EXISTS}, so this is cheap to
     * re-run on every {@code ensureTable} call, including the self-heal path.
     *
     * <p>The B-tree serves {@code =}, {@link EntityCrudService.Range} bounds and
     * {@code ORDER BY}. The Postgres-only trigram GIN index is the part that
     * matters at scale: the default filter is a case-insensitive substring
     * {@code ILIKE '%value%'}, and no B-tree can serve a leading-wildcard LIKE.
     *
     * <p>Deliberately not {@code CREATE INDEX CONCURRENTLY} — that must run
     * outside any transaction, which does not fit the {@code Statement}-per-DDL
     * pattern the rest of {@code ensureTable} uses. Revisit if a tenant table is
     * ever altered while already holding a very large amount of data.
     *
     * @see #indexName for why index names carry a hash suffix
     */
    private static void syncIndexes(EntitySchema schema, Connection c, String dialect, String table) {
        boolean isPg = "postgres".equals(dialect) || "postgresql".equals(dialect);

        List<EntitySchema.Field> fields = new ArrayList<>(schema.getFields());
        if (schema.isApprovalRequired()) {
            fields.addAll(ApprovalColumns.asFields());
        }

        boolean trgmReady = false;
        if (isPg) {
            try (Statement s = c.createStatement()) {
                s.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm");
                trgmReady = true;
            } catch (SQLException e) {
                LOG.warn("[INDEX] Could not enable pg_trgm extension ({}); skipping trigram indexes for {}",
                        e.getMessage(), table);
            }
        }

        for (EntitySchema.Field f : fields) {
            if (f.isPrimaryKey()) {
                continue; // already uniquely indexed
            }
            FieldSqlKind kind = classifyFieldType(f.getType());
            if (kind == FieldSqlKind.FILE) {
                continue; // opaque fileId, never filtered/sorted on
            }
            String col = f.getName();

            String btreeSql = "CREATE INDEX IF NOT EXISTS " + quote(indexName(table, col, "btree"))
                    + " ON " + quote(table) + " (" + quote(col) + ")";
            execIndexDdl(c, schema, table, btreeSql);

            if (trgmReady && (kind == FieldSqlKind.STRING || kind == FieldSqlKind.TEXT)) {
                String trgmSql = "CREATE INDEX IF NOT EXISTS " + quote(indexName(table, col, "trgm"))
                        + " ON " + quote(table) + " USING gin (" + quote(col) + " gin_trgm_ops)";
                execIndexDdl(c, schema, table, trgmSql);
            }
        }
    }

    private static void execIndexDdl(Connection c, EntitySchema schema, String table, String sql) {
        try (Statement s = c.createStatement()) {
            s.execute(sql);
            recordMigration(c, schema.getName(), sql);
        } catch (SQLException e) {
            // An index is a performance concern, never a correctness one, so a
            // failure here must not fail the schema save.
            LOG.warn("[INDEX] Failed to create index on {} ({}): {}", table, sql, e.getMessage());
        }
    }

    /**
     * Builds a collision-safe, <=63-char index identifier.
     *
     * <p>Physical table names already run near Postgres's 63-char identifier
     * limit, so two columns' naively-truncated index names can collide — and
     * {@code CREATE INDEX IF NOT EXISTS} treats an existing name as success
     * regardless of what it actually indexes, silently leaving one column
     * unindexed. Appending a hash of table+column+kind keeps them distinct.
     */
    private static String indexName(String table, String col, String kind) {
        String digest = shortHash(table + "." + col + "." + kind);
        String label = "IDX_" + col + "_" + kind;
        int budget = 63 - 1 - digest.length(); // 1 for the joining underscore
        if (label.length() > budget) {
            label = label.substring(0, Math.max(0, budget));
        }
        return label + "_" + digest;
    }

    /** First 10 hex chars of a SHA-256 digest — collision-safe here, not security-sensitive. */
    private static String shortHash(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 5; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            // SHA-256 is a mandatory JDK algorithm; this is unreachable in practice.
            return Integer.toHexString(input.hashCode());
        }
    }

    /**
     * Materialises the eight approval-workflow columns (see {@link ApprovalColumns})
     * onto a table whose schema has {@code approvalRequired() == true}, if they are
     * not already present. This is the single chokepoint for that injection; before
     * it existed here, a table created through any writer other than one specific path
     * ended up with physical columns that did
     * not contain the string "approval" at all — the only code that materialised them
     * was {@code SchemaEnricher} in the separate ai-builder process, reachable from
     * exactly one of the four writers of the flag. Everything else — {@code create_entity},
     * {@code batch_update_entities}, Studio, scripts, tests — could set the flag and get
     * a table with no approval columns. That entity accepts an insert (the forced
     * DRAFT/revision/submitted_by values are silently dropped, because
     * {@code insertRecordLegacy} iterates {@code schema.getFields()}) and then 500s on
     * the first submit/approve/pending-queue call.
     *
     * <p>Injection belongs here because this is the single chokepoint every writer of a
     * schema passes through, so the flag alone is sufficient for present and future
     * callers.
     *
     * <p>Deliberately <b>add-only</b>, and deliberately NOT folded into the user-field
     * evolution loop above. That loop also migrates column types, and tables created by
     * the old enricher have {@code approval_parent_id} as VARCHAR(255) where
     * {@code ApprovalColumns} declares {@code integer} — routing these through it would
     * emit {@code ALTER COLUMN ... TYPE INTEGER USING col::INTEGER} against live data,
     * which fails outright on any non-numeric value already stored. These are system
     * columns whose contents only this server writes; a missing one is a bug to repair,
     * a differing one is not ours to rewrite underneath a running app.
     *
     * <p>These columns are physical-only and never join {@code schema.getFields()} —
     * see {@code ApprovalColumns.asFields()} and {@code EntityCrudService.getQueryableFields()}
     * for the read-path merge, and note that the default list projection depends on them
     * being absent from {@code getFields()} in order to keep excluding them.
     */
    private static void syncApprovalColumns(EntitySchema schema, Connection c, String dialect, String table,
            Map<String, ColumnInfo> existing) throws SQLException {
        if (!schema.isApprovalRequired()) {
            return;
        }
        for (EntitySchema.Field f : ApprovalColumns.asFields()) {
            if (existing.containsKey(colKey(f.getName()))) {
                continue;
            }
            String alter = "ALTER TABLE " + quote(table) + " ADD " + quote(f.getName()) + " "
                    + sqlType(f, dialect, true);
            LOG.info("[APPROVAL-COLUMNS] Adding missing approval column to {}: {}", table, f.getName());
            try (Statement s = c.createStatement()) {
                s.execute(alter);
                recordMigration(c, schema.getName(), alter);
            }
            existing.put(colKey(f.getName()), new ColumnInfo(f.getName(), null, 0));
        }
    }

    /**
     * H4 hardening — turn `EntityField.referenceEntity` + `EntityField.onDelete`
     * into a real `FOREIGN KEY ... ON DELETE ...` constraint on the child
     * table. Before this method existed, the onDelete metadata was pure
     * documentation — nothing enforced it and a `DELETE FROM parent` would
     * silently orphan child rows.
     *
     * Idempotent: skips fields whose FK already exists (matched by column
     * name, not constraint name, so pre-existing hand-written FKs are
     * respected). Tolerates missing parents (logs WARN, skips) — the FK
     * gets added on a later ensureTable when the parent lands.
     *
     * The constraint is added with `ON DELETE {policy}`:
     *   - `cascade`  → deleting parent also deletes children
     *   - `setNull`  → deleting parent NULLs the FK column (requires nullable)
     *   - anything else (including null / blank / "restrict") → RESTRICT,
     *     which blocks the parent delete if children exist.
     */
    private static void syncForeignKeys(EntitySchema schema, Connection c, String dialect) {
        String childTable = getPhysicalTableName(schema);
        for (EntitySchema.Field f : schema.getFields()) {
            if (!"reference".equalsIgnoreCase(f.getType())) continue;
            String parentEntity = f.getReferenceEntity();
            if (parentEntity == null || parentEntity.isBlank()) continue;

            String parentTable = resolveParentPhysicalTable(schema, parentEntity);
            String policy = mapOnDeleteToSql(f.getOnDelete());
            String colName = f.getName();

            try {
                if (foreignKeyExists(c, childTable, colName)) continue;
                if (!tableExists(c, parentTable)) {
                    LOG.warn("[FK-SYNC] Skipping FK on {}.{} → {}: parent table not found yet",
                            childTable, colName, parentTable);
                    continue;
                }
                String constraintName = truncateIdentifier(("fk_" + childTable + "_" + colName).toLowerCase(Locale.ROOT));
                String ddl = "ALTER TABLE " + quote(childTable)
                        + " ADD CONSTRAINT " + quote(constraintName)
                        + " FOREIGN KEY (" + quote(colName) + ")"
                        + " REFERENCES " + quote(parentTable) + " (" + quote("ID") + ")"
                        + " ON DELETE " + policy;
                try (Statement s = c.createStatement()) {
                    s.execute(ddl);
                    recordMigration(c, schema.getName(), ddl);
                    LOG.info("[FK-SYNC] Added {} ({} → {}, ON DELETE {})",
                            constraintName, colName, parentTable, policy);
                }
            } catch (SQLException e) {
                // FK add can fail for legitimate reasons (existing data violates
                // the constraint, PK column name differs, etc). Log and move on
                // rather than blocking the whole schema save.
                LOG.warn("[FK-SYNC] Could not add FK on {}.{} → {}: {}",
                        childTable, colName, parentTable, e.getMessage());
            }
        }
    }

    private static String resolveParentPhysicalTable(EntitySchema child, String parentEntityName) {
        EntitySchema stub = new EntitySchema(parentEntityName, java.util.Collections.emptyList());
        stub.setTenantId(child.getTenantId());
        stub.setAppId(child.getAppId());
        return getPhysicalTableName(stub);
    }

    private static String mapOnDeleteToSql(String policy) {
        if (policy == null) return "RESTRICT";
        String p = policy.trim().toLowerCase(Locale.ROOT);
        switch (p) {
            case "cascade":  return "CASCADE";
            case "setnull":
            case "set_null":
            case "set null": return "SET NULL";
            case "restrict":
            case "":         return "RESTRICT";
            default:
                LOG.warn("[FK-SYNC] Unknown onDelete policy '{}', defaulting to RESTRICT", policy);
                return "RESTRICT";
        }
    }

    private static boolean tableExists(Connection c, String table) throws SQLException {
        try (ResultSet rs = c.getMetaData().getTables(null, null, table.toUpperCase(Locale.ROOT), null)) {
            return rs.next();
        }
    }

    /** Returns true iff any existing FK on `childTable` covers the single column `colName`. */
    private static boolean foreignKeyExists(Connection c, String childTable, String colName) throws SQLException {
        try (ResultSet rs = c.getMetaData().getImportedKeys(null, null, childTable.toUpperCase(Locale.ROOT))) {
            while (rs.next()) {
                String fkCol = rs.getString("FKCOLUMN_NAME");
                if (fkCol != null && fkCol.equalsIgnoreCase(colName)) return true;
            }
        }
        return false;
    }

    /**
     * Normalize a column name for matching between DB and schema metadata:
     * lowercase and collapse spaces/dashes to underscores. Handles legacy DB
     * columns like "START DATE" mapping to schema field "START_DATE".
     */
    private static String colKey(String s) {
        if (s == null) return "";
        return s.toLowerCase(java.util.Locale.ROOT).replace(' ', '_').replace('-', '_');
    }

    private static void recordMigration(Connection c, String schemaName, String sql) {        try (PreparedStatement ps = c
                .prepareStatement("INSERT INTO appbana_migrations (schema_name, sql) VALUES (?, ?)")) {
            ps.setString(1, schemaName);
            ps.setString(2, sql);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean typesEquivalent(String current, String desired) {
        if (current == null || desired == null)
            return false;
        // Normalize whitespace and remove auto-increment / identity decorations so we
        // don't try to ALTER just to add AI (H2 disallows this form)
        current = normalizeForCompare(current);
        desired = normalizeForCompare(desired);
        return current.equalsIgnoreCase(desired);
    }

    private static String normalizeForCompare(String s) {
        s = s
                .replaceAll("(?i)AUTO_INCREMENT", "")
                .replaceAll("(?i)IDENTITY", "")
                .replaceAll("(?i)SERIAL", "")
                .replaceAll("\\s+", " ")
                .trim();
        // Treat numeric width for integer types as non-significant (H2 reports
        // BIGINT(64), etc.)
        s = s.replaceAll("(?i)BIGINT\\(\\d+\\)", "BIGINT");
        s = s.replaceAll("(?i)INT\\(\\d+\\)", "INTEGER"); // Normalize INT(x) -> INTEGER
        s = s.replaceAll("(?i)INTEGER\\(\\d+\\)", "INTEGER");

        // Normalize basic types
        if ("INT".equalsIgnoreCase(s))
            return "INTEGER";
        return s;
    }

    private static class ColumnInfo {
        String name;
        String typeName;
        int size;

        ColumnInfo(String name, String typeName, int size) {
            this.name = name;
            this.typeName = typeName;
            this.size = size;
        }
    }

    private static void createTable(EntitySchema schema, Connection c, String dialect) throws SQLException {
        String table = getPhysicalTableName(schema);
        List<String> cols = new ArrayList<>();
        String pk = null;

        // Physical table name provides tenant/app isolation - no need for columns
        for (EntitySchema.Field f : schema.getFields()) {
            String col = quote(f.getName()) + " " + sqlType(f, dialect);
            if (f.isPrimaryKey())
                pk = quote(f.getName());
            cols.add(col);
        }
        // C4.6 — approvalRequired implies the eight system columns. See
        // syncApprovalColumns() for why this lives here and not in ai-builder.
        if (schema.isApprovalRequired()) {
            Set<String> declared = new HashSet<>();
            for (EntitySchema.Field f : schema.getFields()) {
                declared.add(colKey(f.getName()));
            }
            for (EntitySchema.Field f : ApprovalColumns.asFields()) {
                if (declared.add(colKey(f.getName()))) {
                    cols.add(quote(f.getName()) + " " + sqlType(f, dialect));
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE IF NOT EXISTS ").append(quote(table)).append(" (");
        sb.append(String.join(", ", cols));
        if (pk != null)
            sb.append(", PRIMARY KEY(").append(pk).append(")");
        sb.append(")");

        String createTableSql = sb.toString();
        LOG.info("[CREATE-TABLE] Executing SQL: {}", createTableSql);

        try (Statement s = c.createStatement()) {
            s.execute(createTableSql);
            LOG.info("[CREATE-TABLE] Successfully created table: {}", table);
            recordMigration(c, schema.getName(), createTableSql);
            LOG.debug("[CREATE-TABLE] Migration recorded for table creation");

            // Physical table name provides isolation - no tenant_id/app_id columns, no
            // index needed
        }
    }

    public static String getPhysicalTableName(EntitySchema schema) {
        if (schema.getAppId() != null && !schema.getAppId().isBlank()) {
            // Sanitize appId to be safe for SQL identifier
            String safeAppId = schema.getAppId().replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
            String safeTenantId = (schema.getTenantId() != null ? schema.getTenantId() : "default")
                    .replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();

            // Include environment prefix from TenantContext if available (for SIT/PROD
            // isolation)
            String envPrefix = "";
            try {
                com.appbana.model.TenantContext ctx = com.appbana.model.TenantContext.getOrNull();
                if (ctx != null && ctx.getEnvironment() != null) {
                    String env = ctx.getEnvironment().toUpperCase();
                    // Only prefix for non-DEV environments to keep backward compatibility
                    LOG.info("[TABLE-NAME] Using environment prefix: '{}' for table (env from TenantContext: {})",
                            envPrefix.isEmpty() ? "NONE" : envPrefix.substring(0, envPrefix.length() - 1),
                            ctx != null ? ctx.getEnvironment() : "NOT_SET");
                    LOG.debug("[TABLE-NAME] Final table name: app_{}{}_{}", envPrefix, safeTenantId + "_" + safeAppId,
                            schema.getName());
                    if (!"DEV".equals(env)) {
                        envPrefix = env + "_";
                    }
                }
            } catch (Exception ignored) {
                // If TenantContext not set, use default table naming
            }

            return truncateIdentifier(
                    ("app_" + envPrefix + safeTenantId + "_" + safeAppId + "_" + schema.getName()).toUpperCase(Locale.ROOT));
        }
        return schema.getName();
    }

    /**
     * Postgres silently truncates unquoted identifiers to 63 chars (NAMEDATALEN-1).
     * Match that behavior explicitly so DatabaseMetaData.getTables() lookups agree
     * with actual pg_class.relname. Truncating here also keeps existing tables
     * (created before this check existed) reachable.
     */
    private static String truncateIdentifier(String name) {
        if (name == null || name.length() <= 63) return name;
        return name.substring(0, 63);
    }

    private static void validateSchema(EntitySchema schema) {
        if (schema == null)
            throw new IllegalArgumentException("schema cannot be null");
        if (schema.getName() == null || schema.getName().trim().isEmpty())
            throw new IllegalArgumentException("schema name required");
        if (schema.getFields() == null || schema.getFields().isEmpty())
            throw new IllegalArgumentException("at least one field required");
        boolean hasPk = false;
        Set<String> names = new HashSet<>();
        for (EntitySchema.Field f : schema.getFields()) {
            if (f.getName() == null || f.getName().trim().isEmpty())
                throw new IllegalArgumentException("field name required");
            String lname = f.getName().toLowerCase();
            if (names.contains(lname))
                throw new IllegalArgumentException("duplicate field name: " + f.getName());
            names.add(lname);
            if (f.isPrimaryKey()) {
                if (hasPk)
                    throw new IllegalArgumentException("only one primary key allowed");
                hasPk = true;
                if (f.isAutoIncrement()) {
                    String t = f.getType().toLowerCase();
                    if (!("int".equals(t) || "integer".equals(t) || "long".equals(t)))
                        throw new IllegalArgumentException("autoIncrement primary key must be integer/long");
                }
            }
            if (f.getType() == null || f.getType().trim().isEmpty())
                throw new IllegalArgumentException("field type required for " + f.getName());
            if (f.getLength() != null && f.getLength() <= 0)
                throw new IllegalArgumentException("length must be > 0 for " + f.getName());
            if (f.getMin() != null && f.getMax() != null && f.getMin() > f.getMax())
                throw new IllegalArgumentException("min cannot be greater than max for " + f.getName());
        }
    }

    /**
     * Review #4 (round 4 of the field-type-coercion defect family) — the DDL
     * mapping here ({@link #sqlType}) and the value-coercion switches in
     * {@code EntityCrudService} ({@code coerceAndValidateRaw},
     * {@code parseFilterValue}) had drifted into three independent hand-maintained
     * switch statements on the same raw type string. Round 3 added
     * "serial"/"bigserial"/"money"/"numeric" to the coercion switches but not
     * here, and none of the three switches recognized "datetime" consistently.
     * This enum + {@link #classifyFieldType} is the single source of truth for
     * "which SQL-ish kind does this schema type name belong to" — sqlType() and
     * every EntityCrudService coercion switch now consult it instead of keeping
     * their own alias lists, so a type added to one can no longer silently miss
     * the other two.
     */
    public enum FieldSqlKind {
        STRING, INTEGER, BIGINT, BOOLEAN, TIMESTAMP, DECIMAL, TEXT, FILE, REFERENCE
    }

    /**
     * Case-insensitive; unrecognized/null type names classify as {@code STRING} (VARCHAR), matching prior behavior.
     *
     * <p>Review #5 (blocker) — "money", "numeric", "serial" and "bigserial" are deliberately
     * classified as {@code STRING} here, NOT as their numeric-sounding SQL kind. Before this
     * classifier existed, {@code sqlType()} had no case for any of the four, so they fell
     * through its {@code default} to {@code VARCHAR(255)}; that is what every existing tenant
     * column of these types physically is. {@code SchemaManager} auto-issues
     * {@code ALTER TABLE ... TYPE ... USING col::TYPE} whenever a schema save finds
     * desired != current, so classifying them as DECIMAL/INTEGER/BIGINT would silently try to
     * cast existing free-text data (the documented behavior in the "money"/"currency"/"float"
     * warning in copilot-instructions.md §11) to a numeric column and break schema saves for
     * any tenant that has one. None of these four is in the AI Builder's allowed type list, so
     * there is no upside to reclassifying them today. If they should become real numeric/serial
     * columns, that needs an explicit migration plan and a corpus check first, not a side effect
     * of this shared-classifier refactor.
     */
    /** Known aliases that are *intentionally* STRING-kind — excluded from the unrecognized-type WARN log. */
    private static final Set<String> STRING_ALIASES = Set.of(
            "string", "varchar", "email", "phone", "status", "uuid", "money", "numeric", "serial", "bigserial");

    public static FieldSqlKind classifyFieldType(String rawType) {
        String t = rawType == null ? "" : rawType.toLowerCase(Locale.ROOT);
        return switch (t) {
            case "int", "integer", "number" -> FieldSqlKind.INTEGER;
            case "long", "bigint" -> FieldSqlKind.BIGINT;
            case "boolean" -> FieldSqlKind.BOOLEAN;
            case "date", "timestamp", "datetime" -> FieldSqlKind.TIMESTAMP;
            case "decimal", "double", "float", "currency" -> FieldSqlKind.DECIMAL;
            case "text", "longtext" -> FieldSqlKind.TEXT;
            case "file" -> FieldSqlKind.FILE;
            case "reference" -> FieldSqlKind.REFERENCE;
            // "serial"/"bigserial"/"money"/"numeric" intentionally NOT listed above — see javadoc.
            default -> {
                if (!t.isEmpty() && !STRING_ALIASES.contains(t)) {
                    LOG.warn("[SCHEMA] Unrecognized field type '{}' — classifying as STRING/VARCHAR(255). "
                            + "If this is a typo, fix the schema; if it's intentional, add it explicitly "
                            + "to classifyFieldType() so future readers don't have to guess.", rawType);
                }
                yield FieldSqlKind.STRING;
            }
        };
    }

    private static String sqlType(EntitySchema.Field f, String dialect) {
        return sqlType(f, dialect, false);
    }

    private static String sqlType(EntitySchema.Field f, String dialect, boolean forAlter) {
        String t = f.getType().toLowerCase();
        boolean aiPk = f.isPrimaryKey() && f.isAutoIncrement();

        // For ALTER statements, we can't use SERIAL/BIGSERIAL, must use INTEGER/BIGINT
        boolean useSerial = aiPk && !forAlter;
        boolean isPg = "postgres".equals(dialect) || "postgresql".equals(dialect);

        return switch (classifyFieldType(t)) {
            case STRING -> {
                // "string"/"varchar" respect an explicit length; every other alias
                // that classifies as STRING (email, phone, status, and anything
                // unrecognized) keeps the fixed VARCHAR(255) this always returned,
                // to avoid silently resizing existing columns for schemas nobody
                // asked to widen/narrow.
                if (t.equals("string") || t.equals("varchar")) {
                    int len = (f.getLength() != null) ? f.getLength() : 255;
                    yield "VARCHAR(" + len + ")" + (!isPg && aiPk ? " AUTO_INCREMENT" : "");
                }
                yield "VARCHAR(255)";
            }
            case INTEGER -> isPg ? (useSerial ? "SERIAL" : "INTEGER") : ("INT" + (aiPk ? " AUTO_INCREMENT" : ""));
            case BIGINT -> isPg ? (useSerial ? "BIGSERIAL" : "BIGINT") : ("BIGINT" + (aiPk ? " AUTO_INCREMENT" : ""));
            case BOOLEAN -> "BOOLEAN";
            case TIMESTAMP -> "TIMESTAMP";
            case DECIMAL -> isPg ? "NUMERIC(19,4)" : "DECIMAL(19,4)";
            case TEXT -> isPg ? "TEXT" : "CLOB";
            // Phase B3 — stores the fileId issued by /api/files/upload (UUID w/o dashes = 32 chars).
            case FILE -> "VARCHAR(64)";
            // H4 hardening — reference columns must match the parent's PK type
            // (SERIAL/INTEGER) so a real FOREIGN KEY constraint can be added.
            case REFERENCE -> isPg ? "INTEGER" : "INT";
        };
    }

    private static String normalizeSqlType(String s) {
        if (s == null)
            return null;
        return s.replaceAll("\\s+", " ").trim();
    }

    private static String quote(String identifier) {
        if (identifier == null)
            return null;
        return "\"" + identifier.toUpperCase() + "\"";
    }

    // --- NEW: list schema names (all) ---
    public static List<String> listSchemaNames() {
        Set<String> names = new TreeSet<>();
        for (DatasourceConfig ds : allDatasources()) {
            String dsName = ds.getName();
            try {
                JdbcManager.ensureMetaTableFor(dsName);
                try (Connection c = JdbcManager.getConnection(dsName);
                        PreparedStatement ps = c.prepareStatement("SELECT name FROM appbana_schemas")) {
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next())
                            names.add(rs.getString(1));
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return new ArrayList<>(names);
    }

    // S1.15 (H1): tenant-scoped variant, queried by the tenant_id column (not name-string
    // parsing) so no tenant id can accidentally prefix-match another tenant's.
    public static List<String> listSchemaNames(String tenantId) {
        Set<String> names = new TreeSet<>();
        for (DatasourceConfig ds : allDatasources()) {
            String dsName = ds.getName();
            try {
                JdbcManager.ensureMetaTableFor(dsName);
                try (Connection c = JdbcManager.getConnection(dsName);
                        PreparedStatement ps = c
                                .prepareStatement("SELECT name FROM appbana_schemas WHERE tenant_id = ?")) {
                    ps.setString(1, tenantId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next())
                            names.add(rs.getString(1));
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return new ArrayList<>(names);
    }

    // --- NEW: list schema names with pagination and optional search q (substring
    // match) ---
    public static List<String> listSchemaNames(int page, int size, String q) {
        return paginateSchemaNames(listSchemaNames(), page, size, q);
    }

    // S1.15 (H1): tenant-scoped counterpart of listSchemaNames(int, int, String).
    public static List<String> listSchemaNames(String tenantId, int page, int size, String q) {
        return paginateSchemaNames(listSchemaNames(tenantId), page, size, q);
    }

    private static List<String> paginateSchemaNames(List<String> all, int page, int size, String q) {
        List<String> filtered = new ArrayList<>(all);
        if (q != null && !q.isBlank()) {
            String lq = q.toLowerCase();
            filtered.removeIf(n -> !n.toLowerCase().contains(lq));
        }
        if (page < 1)
            page = 1;
        if (size <= 0)
            size = 10;
        int from = (page - 1) * size;
        if (from >= filtered.size())
            return List.of();
        int to = Math.min(from + size, filtered.size());
        return filtered.subList(from, to);
    }

    // --- NEW: migration preview (generateMigrationPlan) ---
    public static List<String> generateMigrationPlan(EntitySchema schema) {
        // kept simple: use target datasource only
        validateSchema(schema);
        DatasourceConfig ds = resolveTarget(schema);
        String dsName = ds.getName();
        JdbcManager.ensureMetaTableFor(dsName);
        List<String> plan = new ArrayList<>();
        try (Connection c = JdbcManager.getConnection(dsName)) {
            String table = schema.getName();
            DatabaseMetaData md = c.getMetaData();
            boolean exists;
            try (ResultSet tables = md.getTables(null, null, table.toUpperCase(), null)) {
                exists = tables.next();
            }
            String d = dialect(ds);
            if (!exists) {
                List<String> cols = new ArrayList<>();
                String pk = null;
                for (EntitySchema.Field f : schema.getFields()) {
                    String col = quote(f.getName()) + " " + sqlType(f, d);
                    if (f.isPrimaryKey())
                        pk = quote(f.getName());
                    cols.add(col);
                }
                // C4.6 — mirror createTable()'s approval-column injection.
                if (schema.isApprovalRequired()) {
                    Set<String> declared = new HashSet<>();
                    for (EntitySchema.Field f : schema.getFields()) {
                        declared.add(colKey(f.getName()));
                    }
                    for (EntitySchema.Field f : ApprovalColumns.asFields()) {
                        if (declared.add(colKey(f.getName()))) {
                            cols.add(quote(f.getName()) + " " + sqlType(f, d));
                        }
                    }
                }
                StringBuilder sb = new StringBuilder();
                sb.append("CREATE TABLE IF NOT EXISTS ").append(quote(table)).append(" (");
                sb.append(String.join(", ", cols));
                if (pk != null)
                    sb.append(", PRIMARY KEY(").append(pk).append(")");
                sb.append(")");
                plan.add(sb.toString());
                return plan;
            }
            Map<String, ColumnInfo> existing = new HashMap<>();
            try (ResultSet cols = md.getColumns(null, null, table.toUpperCase(), null)) {
                while (cols.next()) {
                    String colName = cols.getString("COLUMN_NAME");
                    String typeName = cols.getString("TYPE_NAME");
                    int size = cols.getInt("COLUMN_SIZE");
                    existing.put(colKey(colName), new ColumnInfo(colName, typeName, size));
                }
            }
            for (EntitySchema.Field f : schema.getFields()) {
                String target = f.getName();
                String targetLower = colKey(target);
                if (f.getExistingName() != null && !f.getExistingName().isEmpty()) {
                    String old = f.getExistingName();
                    if (existing.containsKey(colKey(old)) && !existing.containsKey(targetLower)) {
                        String renameSql = "ALTER TABLE " + quote(table) + " ALTER COLUMN " + quote(old) + " RENAME TO "
                                + quote(target);
                        plan.add(renameSql);
                        ColumnInfo info = existing.remove(colKey(old));
                        existing.put(targetLower, new ColumnInfo(target, info.typeName, info.size));
                    }
                }
                // Preview self-healing casing rename (see ensureTable for the executed counterpart).
                if (existing.containsKey(targetLower)) {
                    ColumnInfo info = existing.get(targetLower);
                    if (!info.name.equals(target)) {
                        String renameCaseSql = "ALTER TABLE " + quote(table) + " RENAME COLUMN "
                                + quote(info.name) + " TO " + quote(target);
                        plan.add(renameCaseSql);
                        existing.put(targetLower, new ColumnInfo(target, info.typeName, info.size));
                    }
                }
                if (!existing.containsKey(targetLower)) {
                    String alter = "ALTER TABLE " + quote(table) + " ADD " + quote(f.getName()) + " " + sqlType(f, d);
                    plan.add(alter);
                } else {
                    ColumnInfo info = existing.get(targetLower);
                    String desiredType = normalizeSqlType(sqlType(f, d));
                    String currentType = normalizeSqlType(info.typeName + (info.size > 0 ? "(" + info.size + ")" : ""));
                    if (!typesEquivalent(currentType, desiredType)) {
                        String alterType = "postgres".equals(d)
                                ? ("ALTER TABLE " + quote(table) + " ALTER COLUMN " + quote(info.name) + " TYPE "
                                        + sqlType(f, d))
                                : ("ALTER TABLE " + quote(table) + " ALTER COLUMN " + quote(info.name)
                                        + " SET DATA TYPE " + sqlType(f, d));
                        plan.add(alterType);
                    }
                }
            }
            // C4.6 — mirror syncApprovalColumns() so a preview shows the same plan the
            // save will execute. Add-only, for the same reason documented there.
            if (schema.isApprovalRequired()) {
                for (EntitySchema.Field f : ApprovalColumns.asFields()) {
                    if (!existing.containsKey(colKey(f.getName()))) {
                        plan.add("ALTER TABLE " + quote(table) + " ADD " + quote(f.getName()) + " "
                                + sqlType(f, d, true));
                    }
                }
            }
            return plan;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<Map<String, Object>> listMigrations(String schemaName) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (DatasourceConfig ds : allDatasources()) {
            String dsName = ds.getName();
            try {
                JdbcManager.ensureMetaTableFor(dsName);
                try (Connection c = JdbcManager.getConnection(dsName)) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "SELECT executed_at, sql FROM appbana_migrations WHERE schema_name = ? ORDER BY executed_at, id")) {
                        ps.setString(1, schemaName);
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                Map<String, Object> m = new LinkedHashMap<>();
                                m.put("datasource", dsName);
                                m.put("executedAt", rs.getObject(1));
                                m.put("sql", rs.getString(2));
                                out.add(m);
                            }
                        }
                    } catch (SQLException ignore) {
                    }
                }
            } catch (Exception ignore) {
            }
        }
        return out;
    }

    public static boolean deleteSchema(String name, boolean dropTable) {
        // find schema & datasource
        EntitySchema schema = loadSchema(name);
        if (schema == null)
            return false;
        String dsName = schema.getDatasourceName();
        if (dsName == null || dsName.isBlank()) {
            // fallback to active datasource (legacy) - attempt deletion
            AppConfig cfg = ConfigManager.getConfig();
            dsName = cfg.getActiveDatasource();
        }
        try (Connection c = JdbcManager.getConnection(dsName)) {
            // remove row from meta table
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM appbana_schemas WHERE name = ?")) {
                ps.setString(1, name);
                ps.executeUpdate();
            }
            if (dropTable) {
                String sql = "DROP TABLE IF EXISTS \"" + name.toUpperCase() + "\"";
                try (Statement s = c.createStatement()) {
                    s.execute(sql);
                }
                // record migration note
                try (PreparedStatement ps = c
                        .prepareStatement("INSERT INTO appbana_migrations (schema_name, sql) VALUES (?, ?)");) {
                    ps.setString(1, name);
                    ps.setString(2, sql);
                    ps.executeUpdate();
                } catch (SQLException ignore) {
                }
            }
            return true;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
