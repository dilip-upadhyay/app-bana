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

    public static void saveSchema(EntitySchema schema) {
        validateSchema(schema);
        try (Connection c = JdbcManager.getConnection()) {
            // store JSON
            String json = M.writeValueAsString(schema);
            String upsert = "MERGE INTO appbana_schemas (name, json) KEY(name) VALUES (?, ?)";
            try (PreparedStatement ps = c.prepareStatement(upsert)) {
                ps.setString(1, schema.getName());
                ps.setString(2, json);
                ps.executeUpdate();
            }
            // create or migrate table
            ensureTable(schema, c);
        } catch (SQLException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static EntitySchema loadSchema(String name) {
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
        // check if table exists
        try (ResultSet tables = md.getTables(null, null, table.toUpperCase(), null)) {
            boolean exists = tables.next();
            if (!exists) {
                createTable(schema, c);
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
                        String alter = "ALTER TABLE " + quote(table) + " ADD " + quote(f.getName()) + " " + sqlType(f);
                        try (Statement s = c.createStatement()) {
                            s.execute(alter);
                            recordMigration(c, schema.getName(), alter);
                        }
                    } else {
                        // detect type change
                        ColumnInfo info = existing.get(targetLower);
                        String desiredType = normalizeSqlType(sqlType(f));
                        String currentType = normalizeSqlType(info.typeName + (info.size > 0 ? "(" + info.size + ")" : ""));
                        if (!typesEquivalent(currentType, desiredType)) {
                            String alterType = "ALTER TABLE " + quote(table) + " ALTER COLUMN " + quote(info.name) + " SET DATA TYPE " + sqlType(f);
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
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO appbana_migrations (schema_name, sql) VALUES (?, ?)") ) {
            ps.setString(1, schemaName);
            ps.setString(2, sql);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean typesEquivalent(String current, String desired) {
        // very simple equivalence: compare lower-case, ignore whitespace
        if (current == null) return false;
        return current.replaceAll("\\s+", "").toLowerCase().equals(desired.replaceAll("\\s+", "").toLowerCase());
    }

    private static class ColumnInfo {
        String name;
        String typeName;
        int size;
        ColumnInfo(String name, String typeName, int size) { this.name = name; this.typeName = typeName; this.size = size; }
    }

    private static void createTable(EntitySchema schema, Connection c) throws SQLException {
        String table = schema.getName();
        List<String> cols = new ArrayList<>();
        String pk = null;
        for (EntitySchema.Field f : schema.getFields()) {
            String col = quote(f.getName()) + " " + sqlType(f);
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

    private static String sqlType(EntitySchema.Field f) {
        String t = f.getType().toLowerCase();
        switch (t) {
            case "string":
            case "varchar":
                int len = (f.getLength() != null) ? f.getLength() : 255;
                return "VARCHAR(" + len + ")" + (f.isPrimaryKey() && f.isAutoIncrement() ? " AUTO_INCREMENT" : "");
            case "int":
            case "integer":
                return "INT" + (f.isPrimaryKey() && f.isAutoIncrement() ? " AUTO_INCREMENT" : "");
            case "long":
                return "BIGINT" + (f.isPrimaryKey() && f.isAutoIncrement() ? " AUTO_INCREMENT" : "");
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
        return "\"" + identifier + "\"";
    }

    // --- NEW: migration preview (generateMigrationPlan) ---
    public static List<String> generateMigrationPlan(EntitySchema schema) {
        validateSchema(schema);
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
                for (EntitySchema.Field f : schema.getFields()) {
                    String col = quote(f.getName()) + " " + sqlType(f);
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
                    String alter = "ALTER TABLE " + quote(table) + " ADD " + quote(f.getName()) + " " + sqlType(f);
                    plan.add(alter);
                } else {
                    ColumnInfo info = existing.get(targetLower);
                    String desiredType = normalizeSqlType(sqlType(f));
                    String currentType = normalizeSqlType(info.typeName + (info.size > 0 ? "(" + info.size + ")" : ""));
                    if (!typesEquivalent(currentType, desiredType)) {
                        String alterType = "ALTER TABLE " + quote(table) + " ALTER COLUMN " + quote(info.name) + " SET DATA TYPE " + sqlType(f);
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
