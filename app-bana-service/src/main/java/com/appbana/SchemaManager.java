package com.appbana;

import com.appbana.config.AppConfig;
import com.appbana.config.ConfigManager;
import com.appbana.config.DatasourceConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
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
            if ("postgres".equals(d)) {
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
                        existing.put(colName.toLowerCase(), new ColumnInfo(colName, typeName, size));
                    }
                }

                // Handle schema evolution for user-defined fields
                for (EntitySchema.Field f : schema.getFields()) {
                    String target = f.getName();
                    String targetLower = target.toLowerCase();
                    if (f.getExistingName() != null && !f.getExistingName().isEmpty()) {
                        String old = f.getExistingName();
                        if (existing.containsKey(old.toLowerCase()) && !existing.containsKey(targetLower)) {
                            String renameSql = "ALTER TABLE " + quote(table) + " ALTER COLUMN " + quote(old)
                                    + " RENAME TO " + quote(target);
                            try (Statement s = c.createStatement()) {
                                s.execute(renameSql);
                                recordMigration(c, schema.getName(), renameSql);
                                ColumnInfo info = existing.remove(old.toLowerCase());
                                existing.put(targetLower, new ColumnInfo(target, info.typeName, info.size));
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
                            String alterType = "postgres".equals(d)
                                    ? ("ALTER TABLE " + quote(table) + " ALTER COLUMN " + quote(info.name) + " TYPE "
                                            + sqlType(f, d, true))
                                    : ("ALTER TABLE " + quote(table) + " ALTER COLUMN " + quote(info.name)
                                            + " SET DATA TYPE " + sqlType(f, d, true));
                            try (Statement s = c.createStatement()) {
                                s.execute(alterType);
                                recordMigration(c, schema.getName(), alterType);
                            }
                        }
                    }
                }
            }
        }
    }

    private static void recordMigration(Connection c, String schemaName, String sql) {
        try (PreparedStatement ps = c
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

        // Always add tenant_id and app_id columns first for multi-tenant isolation
        // (required, no defaults)
        cols.add(quote("tenant_id") + " VARCHAR(50) NOT NULL");
        cols.add(quote("app_id") + " VARCHAR(50) NOT NULL");

        for (EntitySchema.Field f : schema.getFields()) {
            String col = quote(f.getName()) + " " + sqlType(f, dialect);
            if (f.isPrimaryKey())
                pk = quote(f.getName());
            cols.add(col);
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

            // Create composite index for efficient tenant/app filtering
            String indexSql = "CREATE INDEX IF NOT EXISTS idx_" + table + "_tenant_app ON "
                    + quote(table) + "(" + quote("tenant_id") + ", " + quote("app_id") + ")";
            s.execute(indexSql);
        LOG.debug("[TABLE-NAME] Generating physical table name for entity: {}, appId: {}, tenantId: {}", 
                  schema.getName(), schema.getAppId(), schema.getTenantId());
            recordMigration(c, schema.getName(), indexSql);
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
                     envPrefix.isEmpty() ? "NONE" : envPrefix.substring(0, envPrefix.length()-1), 
                     ctx != null ? ctx.getEnvironment() : "NOT_SET");
            LOG.debug("[TABLE-NAME] Final table name: app_{}{}_{}", envPrefix, safeTenantId + "_" + safeAppId, schema.getName());
                    if (!"DEV".equals(env)) {
                        envPrefix = env + "_";
                    }
                }
            } catch (Exception ignored) {
                // If TenantContext not set, use default table naming
            }

            return "app_" + envPrefix + safeTenantId + "_" + safeAppId + "_" + schema.getName();
        }
        return schema.getName();
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

    private static String sqlType(EntitySchema.Field f, String dialect) {
        return sqlType(f, dialect, false);
    }

    private static String sqlType(EntitySchema.Field f, String dialect, boolean forAlter) {
        String t = f.getType().toLowerCase();
        boolean aiPk = f.isPrimaryKey() && f.isAutoIncrement();
        
        // For ALTER statements, we can't use SERIAL/BIGSERIAL, must use INTEGER/BIGINT
        boolean useSerial = aiPk && !forAlter;
        
        if ("postgres".equals(dialect)) {
            switch (t) {
                case "string":
                case "varchar":
                    int len = (f.getLength() != null) ? f.getLength() : 255;
                    return "VARCHAR(" + len + ")";
                case "int":
                case "integer":
                    return useSerial ? "SERIAL" : "INTEGER";
                case "long":
                    return useSerial ? "BIGSERIAL" : "BIGINT";
                case "boolean":
                    return "BOOLEAN";
                case "date":
                case "timestamp":
                    return "TIMESTAMP";
                case "text":
                    return "TEXT";
                default:
                    return "VARCHAR(255)";
            }
        }
        switch (t) {
            case "string":
            case "varchar":
                int len = (f.getLength() != null) ? f.getLength() : 255;
                return "VARCHAR(" + len + ")" + (aiPk ? " AUTO_INCREMENT" : "");
            case "int":
            case "integer":
                return "INT" + (aiPk ? " AUTO_INCREMENT" : "");
            case "long":
                return "BIGINT" + (aiPk ? " AUTO_INCREMENT" : "");
            case "boolean":
                return "BOOLEAN";
            case "date":
            case "timestamp":
                return "TIMESTAMP";
            case "text":
                return "CLOB";
            default:
                return "VARCHAR(255)";
        }
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

    // --- NEW: list schema names with pagination and optional search q (substring
    // match) ---
    public static List<String> listSchemaNames(int page, int size, String q) {
        List<String> all = listSchemaNames();
        if (q != null && !q.isBlank()) {
            String lq = q.toLowerCase();
            all.removeIf(n -> !n.toLowerCase().contains(lq));
        }
        if (page < 1)
            page = 1;
        if (size <= 0)
            size = 10;
        int from = (page - 1) * size;
        if (from >= all.size())
            return List.of();
        int to = Math.min(from + size, all.size());
        return all.subList(from, to);
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
                    existing.put(colName.toLowerCase(), new ColumnInfo(colName, typeName, size));
                }
            }
            for (EntitySchema.Field f : schema.getFields()) {
                String target = f.getName();
                String targetLower = target.toLowerCase();
                if (f.getExistingName() != null && !f.getExistingName().isEmpty()) {
                    String old = f.getExistingName();
                    if (existing.containsKey(old.toLowerCase()) && !existing.containsKey(targetLower)) {
                        String renameSql = "ALTER TABLE " + quote(table) + " ALTER COLUMN " + quote(old) + " RENAME TO "
                                + quote(target);
                        plan.add(renameSql);
                        ColumnInfo info = existing.remove(old.toLowerCase());
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

    // --- NEW: list schema summaries (name + datasource) ---
    public static List<Map<String, Object>> listSchemaSummaries() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (DatasourceConfig ds : allDatasources()) {
            String dsName = ds.getName();
            try {
                JdbcManager.ensureMetaTableFor(dsName);
                try (Connection c = JdbcManager.getConnection(dsName);
                        PreparedStatement ps = c.prepareStatement("SELECT json FROM appbana_schemas")) {
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String json = rs.getString(1);
                            try {
                                EntitySchema schema = M.readValue(json, EntitySchema.class);
                                if (schema.getDatasourceName() == null || schema.getDatasourceName().isBlank())
                                    schema.setDatasourceName(dsName); // backfill
                                Map<String, Object> m = new LinkedHashMap<>();
                                m.put("name", schema.getName());
                                m.put("datasource", schema.getDatasourceName());
                                out.add(m);
                            } catch (Exception ignore) {
                            }
                        }
                    }
                }
            } catch (Exception ignore) {
            }
        }
        out.sort(Comparator.comparing(o -> (String) o.get("name")));
        return out;
    }
}
