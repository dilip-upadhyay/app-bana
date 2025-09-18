package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.model.EntitySchema;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SchemaManager {
    private static final ObjectMapper M = new ObjectMapper();

    public static void init() {
        JdbcManager.ensureMetaTable();
    }

    // Detect current dialect based on active datasource
    private static String dialect() {
        AppConfig cfg = ConfigManager.getConfig();
        String active = cfg.getActiveDatasource();
        String type = null; String url = null;
        if (cfg.getDatasources() != null) {
            for (DatasourceConfig ds : cfg.getDatasources()) {
                if (ds.getName() != null && ds.getName().equals(active)) {
                    type = ds.getType(); url = ds.getJdbcUrl(); break;
                }
            }
        }
        if ((type == null || type.isBlank()) && url == null) {
            type = cfg.getName(); // fallback (unlikely used)
            url = cfg.getJdbcUrl();
        }
        if (type != null && !type.isBlank()) return type.toLowerCase();
        if (url == null) return "h2";
        if (url.startsWith("jdbc:h2:")) return "h2";
        if (url.startsWith("jdbc:postgresql:")) return "postgres";
        if (url.startsWith("jdbc:mysql:")) return "mysql";
        if (url.startsWith("jdbc:mariadb:")) return "mariadb";
        if (url.startsWith("jdbc:sqlserver:")) return "mssql";
        if (url.startsWith("jdbc:oracle:")) return "oracle";
        if (url.startsWith("jdbc:sqlite:")) return "sqlite";
        return "h2";
    }

    public static void saveSchema(EntitySchema schema) {
        validateSchema(schema);
        // Ensure meta tables exist in the current (possibly new) active datasource
        JdbcManager.ensureMetaTable();
        try (Connection c = JdbcManager.getConnection()) {
            // store JSON
            String json = M.writeValueAsString(schema);
            String d = dialect();
            if ("postgres".equals(d)) {
                String upsert = "INSERT INTO appbana_schemas(name, json) VALUES (?, ?) ON CONFLICT (name) DO UPDATE SET json = EXCLUDED.json";
                try (PreparedStatement ps = c.prepareStatement(upsert)) {
                    ps.setString(1, schema.getName());
                    ps.setString(2, json);
                    ps.executeUpdate();
                }
            } else {
                String upsert = "MERGE INTO appbana_schemas (name, json) KEY(name) VALUES (?, ?)";
                try (PreparedStatement ps = c.prepareStatement(upsert)) {
                    ps.setString(1, schema.getName());
                    ps.setString(2, json);
                    ps.executeUpdate();
                } catch (SQLException e) {
                    // Fallback for engines without H2 MERGE support: try insert-then-update pattern
                    try (PreparedStatement ins = c.prepareStatement("INSERT INTO appbana_schemas(name, json) VALUES (?, ?)")) {
                        ins.setString(1, schema.getName());
                        ins.setString(2, json);
                        ins.executeUpdate();
                    } catch (SQLException dup) {
                        try (PreparedStatement upd = c.prepareStatement("UPDATE appbana_schemas SET json = ? WHERE name = ?")) {
                            upd.setString(1, json);
                            upd.setString(2, schema.getName());
                            upd.executeUpdate();
                        }
                    }
                }
            }
            // create or migrate table
            ensureTable(schema, c);
        } catch (SQLException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static EntitySchema loadSchema(String name) {
        // Ensure meta table exists in the active datasource before querying
        JdbcManager.ensureMetaTable();
        try (Connection c = JdbcManager.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT json FROM appbana_schemas WHERE name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String json = rs.getString(1);
                    return M.readValue(json, EntitySchema.class);
                }
            }
            return null;
        } catch (SQLException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void ensureTable(EntitySchema schema, Connection c) throws SQLException {
        String table = schema.getName();
        DatabaseMetaData md = c.getMetaData();
        String d = dialect();
        // check if table exists
        try (ResultSet tables = md.getTables(null, null, table.toUpperCase(), null)) {
            boolean exists = tables.next();
            if (!exists) {
                createTable(schema, c, d);
            } else {
                // table exists: check for missing columns and add them, detect renames and type changes
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
                    // handle rename: existingName provided
                    if (f.getExistingName() != null && !f.getExistingName().isEmpty()) {
                        String old = f.getExistingName();
                        if (existing.containsKey(old.toLowerCase()) && !existing.containsKey(targetLower)) {
                            String renameSql = "ALTER TABLE " + quote(table) + " ALTER COLUMN " + quote(old) + " RENAME TO " + quote(target);
                            try (Statement s = c.createStatement()) {
                                s.execute(renameSql);
                                recordMigration(c, schema.getName(), renameSql);
                                // update existing map
                                ColumnInfo info = existing.remove(old.toLowerCase());
                                existing.put(targetLower, new ColumnInfo(target, info.typeName, info.size));
                            }
                        }
                    }

                    if (!existing.containsKey(targetLower)) {
                        String alter = "ALTER TABLE " + quote(table) + " ADD " + quote(f.getName()) + " " + sqlType(f, d);
                        try (Statement s = c.createStatement()) {
                            s.execute(alter);
                            recordMigration(c, schema.getName(), alter);
                        }
                    } else {
                        // detect type change
                        ColumnInfo info = existing.get(targetLower);
                        String desiredType = normalizeSqlType(sqlType(f, d));
                        String currentType = normalizeSqlType(info.typeName + (info.size > 0 ? "(" + info.size + ")" : ""));
                        if (!typesEquivalent(currentType, desiredType)) {
                            String alterType;
                            if ("postgres".equals(d)) {
                                alterType = "ALTER TABLE " + quote(table) + " ALTER COLUMN " + quote(info.name) + " TYPE " + sqlType(f, d);
                            } else {
                                alterType = "ALTER TABLE " + quote(table) + " ALTER COLUMN " + quote(info.name) + " SET DATA TYPE " + sqlType(f, d);
                            }
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
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO appbana_migrations (schema_name, sql) VALUES (?, ?)" ) ) {
            ps.setString(1, schemaName);
            ps.setString(2, sql);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean typesEquivalent(String current, String desired) {
        // very simple equivalence: compare lower-case, ignore whitespace
        if (current == null || desired == null) return false;
        String c = current.replaceAll("\\s+", "").trim();
        String d = desired.replaceAll("\\s+", "").trim();
        return c.equalsIgnoreCase(d);
    }

    private static class ColumnInfo {
        String name;
        String typeName;
        int size;
        ColumnInfo(String name, String typeName, int size) { this.name = name; this.typeName = typeName; this.size = size; }
    }

    private static void createTable(EntitySchema schema, Connection c, String dialect) throws SQLException {
        String table = schema.getName();
        List<String> cols = new ArrayList<>();
        String pk = null;
        for (EntitySchema.Field f : schema.getFields()) {
            String col = quote(f.getName()) + " " + sqlType(f, dialect);
            if (f.isPrimaryKey()) {
                pk = quote(f.getName());
            }
            cols.add(col);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE IF NOT EXISTS ").append(quote(table)).append(" (");
        sb.append(String.join(", ", cols));
        if (pk != null) {
            sb.append(", PRIMARY KEY(").append(pk).append(")");
        }
        sb.append(")");
        try (Statement s = c.createStatement()) {
            s.execute(sb.toString());
            recordMigration(c, schema.getName(), sb.toString());
        }
    }

    private static void validateSchema(EntitySchema schema) {
        if (schema == null) throw new IllegalArgumentException("schema cannot be null");
        if (schema.getName() == null || schema.getName().trim().isEmpty()) throw new IllegalArgumentException("schema name required");
        if (schema.getFields() == null || schema.getFields().isEmpty()) throw new IllegalArgumentException("at least one field required");
        boolean hasPk = false;
        Set<String> names = new HashSet<>();
        for (EntitySchema.Field f : schema.getFields()) {
            if (f.getName() == null || f.getName().trim().isEmpty()) throw new IllegalArgumentException("field name required");
            String lname = f.getName().toLowerCase();
            if (names.contains(lname)) throw new IllegalArgumentException("duplicate field name: " + f.getName());
            names.add(lname);
            if (f.isPrimaryKey()) {
                if (hasPk) throw new IllegalArgumentException("only one primary key allowed");
                hasPk = true;
                if (f.isAutoIncrement()) {
                    String t = f.getType().toLowerCase();
                    if (!("int".equals(t) || "integer".equals(t) || "long".equals(t))) {
                        throw new IllegalArgumentException("autoIncrement primary key must be integer/long");
                    }
                }
            }
            if (f.getType() == null || f.getType().trim().isEmpty()) throw new IllegalArgumentException("field type required for " + f.getName());
            if (f.getLength() != null && f.getLength() <= 0) throw new IllegalArgumentException("length must be > 0 for " + f.getName());
            if (f.getMin() != null && f.getMax() != null && f.getMin() > f.getMax()) throw new IllegalArgumentException("min cannot be greater than max for " + f.getName());
        }
    }

    private static String sqlType(EntitySchema.Field f, String dialect) {
        String t = f.getType().toLowerCase();
        boolean aiPk = f.isPrimaryKey() && f.isAutoIncrement();
        if ("postgres".equals(dialect)) {
            switch (t) {
                case "string":
                case "varchar":
                    int len = (f.getLength() != null) ? f.getLength() : 255;
                    return "VARCHAR(" + len + ")";
                case "int":
                case "integer":
                    return aiPk ? "SERIAL" : "INTEGER";
                case "long":
                    return aiPk ? "BIGSERIAL" : "BIGINT";
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
        // default (H2/MySQL/etc.)
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
        if (s == null) return null;
        return s.replaceAll("\\s+", " ").trim();
    }

    private static String quote(String identifier) {
        if (identifier == null) return null;
        return "\"" + identifier.toUpperCase() + "\"";
    }

    // --- NEW: migration preview (generateMigrationPlan) ---
    public static List<String> generateMigrationPlan(EntitySchema schema) {
        validateSchema(schema);
        // Ensure meta tables exist in the active datasource (safe no-op if already created)
        JdbcManager.ensureMetaTable();
        List<String> plan = new ArrayList<>();
        try (Connection c = JdbcManager.getConnection()) {
            String table = schema.getName();
            DatabaseMetaData md = c.getMetaData();
            boolean exists = false;
            try (ResultSet tables = md.getTables(null, null, table.toUpperCase(), null)) {
                exists = tables.next();
            }
            if (!exists) {
                // build CREATE TABLE statement (same as createTable but do not execute)
                List<String> cols = new ArrayList<>();
                String pk = null;
                String d = dialect();
                for (EntitySchema.Field f : schema.getFields()) {
                    String col = quote(f.getName()) + " " + sqlType(f, d);
                    if (f.isPrimaryKey()) pk = quote(f.getName());
                    cols.add(col);
                }
                StringBuilder sb = new StringBuilder();
                sb.append("CREATE TABLE IF NOT EXISTS ").append(quote(table)).append(" (");
                sb.append(String.join(", ", cols));
                if (pk != null) sb.append(", PRIMARY KEY(").append(pk).append(")");
                sb.append(")");
                plan.add(sb.toString());
                return plan;
            }

            // table exists: inspect columns
            Map<String, ColumnInfo> existing = new HashMap<>();
            try (ResultSet cols = md.getColumns(null, null, table.toUpperCase(), null)) {
                while (cols.next()) {
                    String colName = cols.getString("COLUMN_NAME");
                    String typeName = cols.getString("TYPE_NAME");
                    int size = cols.getInt("COLUMN_SIZE");
                    existing.put(colName.toLowerCase(), new ColumnInfo(colName, typeName, size));
                }
            }

            String d = dialect();
            for (EntitySchema.Field f : schema.getFields()) {
                String target = f.getName();
                String targetLower = target.toLowerCase();
                if (f.getExistingName() != null && !f.getExistingName().isEmpty()) {
                    String old = f.getExistingName();
                    if (existing.containsKey(old.toLowerCase()) && !existing.containsKey(targetLower)) {
                        String renameSql = "ALTER TABLE " + quote(table) + " ALTER COLUMN " + quote(old) + " RENAME TO " + quote(target);
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
                                ? ("ALTER TABLE " + quote(table) + " ALTER COLUMN " + quote(info.name) + " TYPE " + sqlType(f, d))
                                : ("ALTER TABLE " + quote(table) + " ALTER COLUMN " + quote(info.name) + " SET DATA TYPE " + sqlType(f, d));
                        plan.add(alterType);
                    }
                }
            }
            return plan;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // --- NEW: list schema names (all) ---
    public static List<String> listSchemaNames() {
        // Ensure meta table exists in the active datasource before querying
        JdbcManager.ensureMetaTable();
        List<String> names = new ArrayList<>();
        try (Connection c = JdbcManager.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT name FROM appbana_schemas ORDER BY name")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) names.add(rs.getString(1));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return names;
    }

    // --- NEW: list schema names with pagination and optional search q (substring match) ---
    public static List<String> listSchemaNames(int page, int size, String q) {
        // Ensure meta table exists in the active datasource before querying
        JdbcManager.ensureMetaTable();
        List<String> names = new ArrayList<>();
        if (page < 1) page = 1;
        if (size <= 0) size = 10;
        int offset = (page - 1) * size;
        String sql;
        boolean useFilter = q != null && !q.trim().isEmpty();
        if (useFilter) {
            sql = "SELECT name FROM appbana_schemas WHERE LOWER(name) LIKE ? ORDER BY name LIMIT ? OFFSET ?";
        } else {
            sql = "SELECT name FROM appbana_schemas ORDER BY name LIMIT ? OFFSET ?";
        }
        try (Connection c = JdbcManager.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            int idx = 1;
            if (useFilter) ps.setString(idx++, "%" + q.toLowerCase() + "%");
            ps.setInt(idx++, size);
            ps.setInt(idx, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) names.add(rs.getString(1));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return names;
    }
}
