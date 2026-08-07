package com.appbana;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * Minimal audit log service (CRUD only) capturing before/after JSON and field level changes.
 */
public class AuditLogService {
    private static final ObjectMapper M = new ObjectMapper();
    private static final Logger LOG = LoggerFactory.getLogger(AuditLogService.class);
    private static volatile String LAST_ERROR = null;

    public static String getLastError() { return LAST_ERROR; }

    /**
     * S4.6 — {@code tenantId}/{@code appId} are recorded so a cross-tenant incident is provable
     * after the fact from the audit trail alone, not merely reproducible live. Every call site in
     * {@code GenericEntityRoutes.java} sources both from the already-loaded {@code EntitySchema}
     * (populated by {@code SchemaManager.loadSchema()} from either the schema's own JSON or the
     * {@code appbana_schemas} table columns), not from client-supplied request data.
     */
    public static void log(String op, String entity, String pk, String actor, String tenantId, String appId, Map<String,Object> before, Map<String,Object> after) {
        try (Connection c = JdbcManager.getConnection(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO appbana_audit(op, entity, pk, actor, tenant_id, app_id, before_json, after_json, changes_json) VALUES (?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, op);
            ps.setString(2, entity);
            ps.setString(3, pk);
            ps.setString(4, actor);
            ps.setString(5, tenantId);
            ps.setString(6, appId);
            ps.setString(7, before==null? null : M.writeValueAsString(before));
            ps.setString(8, after==null? null : M.writeValueAsString(after));
            ps.setString(9, buildChanges(before, after));
            ps.executeUpdate();
        } catch (Exception e) {
            java.io.StringWriter sw = new java.io.StringWriter();
            e.printStackTrace(new java.io.PrintWriter(sw));
            LAST_ERROR = e.getClass().getSimpleName()+": "+e.getMessage()+"\n"+sw;
            LOG.warn("Audit log failed op={} entity={} pk={}: {}", op, entity, pk, e.toString());
        }
    }

    private static String buildChanges(Map<String,Object> before, Map<String,Object> after) throws Exception {
        if (before == null && after == null) return null;
        Map<String, List<Object>> diff = new LinkedHashMap<>();
        Set<String> keys = new TreeSet<>();
        if (before != null) keys.addAll(before.keySet());
        if (after != null) keys.addAll(after.keySet());
        for (String k : keys) {
            Object b = before!=null? before.get(k): null;
            Object a = after!=null? after.get(k): null;
            if (Objects.equals(b, a)) continue; // unchanged
            diff.put(k, java.util.Arrays.asList(b, a));
        }
        if (diff.isEmpty()) return null;
        return M.writeValueAsString(diff);
    }

    public static Map<String,Object> query(String entity, String pk, int limit, int offset) throws Exception {
        StringBuilder sql = new StringBuilder("SELECT id, ts, op, entity, pk, actor, tenant_id, app_id, before_json, after_json, changes_json FROM appbana_audit");
        List<Object> params = new ArrayList<>();
        List<String> where = new ArrayList<>();
        if (entity != null && !entity.isBlank()) { where.add("entity = ?"); params.add(entity); }
        if (pk != null && !pk.isBlank()) { where.add("pk = ?"); params.add(pk); }
        if (!where.isEmpty()) sql.append(" WHERE ").append(String.join(" AND ", where));
        sql.append(" ORDER BY id ASC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
        try (Connection c = JdbcManager.getConnection()) {
            long total = countTotal(c, where, params);
            List<Map<String,Object>> rows = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
                int i=1;
                for (Object p : params) ps.setObject(i++, p);
                ps.setInt(i++, offset);
                ps.setInt(i, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String,Object> row = new LinkedHashMap<>();
                        row.put("id", rs.getLong(1));
                        Timestamp ts = rs.getTimestamp(2);
                        row.put("ts", ts!=null? ts.toInstant().toString(): null);
                        row.put("op", rs.getString(3));
                        row.put("entity", rs.getString(4));
                        row.put("pk", rs.getString(5));
                        row.put("actor", rs.getString(6));
                        row.put("tenantId", rs.getString(7));
                        row.put("appId", rs.getString(8));
                        row.put("before", parseJson(rs.getString(9)));
                        row.put("after", parseJson(rs.getString(10)));
                        row.put("changes", parseJson(rs.getString(11)));
                        rows.add(row);
                    }
                }
            }
            Map<String,Object> out = new LinkedHashMap<>();
            out.put("rows", rows);
            out.put("limit", limit);
            out.put("offset", offset);
            out.put("total", total);
            if (entity != null && !entity.isBlank()) out.put("entity", entity);
            if (pk != null && !pk.isBlank()) out.put("pk", pk);
            if (LAST_ERROR != null) out.put("lastError", LAST_ERROR);
            return out;
        }
    }

    private static long countTotal(Connection c, List<String> where, List<Object> params) throws SQLException {
        StringBuilder sb = new StringBuilder("SELECT COUNT(*) FROM appbana_audit");
        if (!where.isEmpty()) sb.append(" WHERE ").append(String.join(" AND ", where));
        try (PreparedStatement ps = c.prepareStatement(sb.toString())) {
            int i=1; for (Object p : params) ps.setObject(i++, p);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getLong(1); }
        }
    }

    private static Object parseJson(String json) {
        if (json == null || json.isBlank()) return null;
        try { return M.readValue(json, new TypeReference<Map<String,Object>>(){}); } catch (Exception e) { return null; }
    }
}
