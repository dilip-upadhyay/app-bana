package org.example;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.example.model.EntitySchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

public class ApiServer {
    private static final ObjectMapper M = new ObjectMapper();
    private static final Logger LOG = LoggerFactory.getLogger(ApiServer.class);

    // Shared utilities so both handlers can send responses
    public static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] b = body.getBytes();
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, b.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(b);
        }
    }

    public static void sendJson(HttpExchange exchange, int status, Object obj) throws IOException {
        byte[] b = M.writeValueAsBytes(obj);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, b.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(b);
        }
    }

    private static Map<String,String> parseQuery(String query){
        Map<String,String> map = new HashMap<>();
        if(query==null||query.isEmpty()) return map;
        for(String part: query.split("&")){
            int i = part.indexOf('=');
            if(i>0) map.put(part.substring(0,i), part.substring(i+1));
            else map.put(part, "");
        }
        return map;
    }

    public static void start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/schema", new SchemaHandler());
        server.createContext("/api", new EntityHandler());

        // Return a machine-readable list of API endpoints generated from saved entities
        server.createContext("/api/endpoints", exchange -> {
            try {
                if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    send(exchange, 405, "{\"error\":\"method not allowed\"}");
                    return;
                }
                List<String> names = SchemaManager.listSchemaNames();
                List<Map<String,Object>> out = new ArrayList<>();
                for (String n : names) {
                    Map<String,Object> m = new HashMap<>();
                    m.put("entity", n);
                    List<String> eps = new ArrayList<>();
                    eps.add("POST /api/" + n);
                    eps.add("GET /api/" + n);
                    eps.add("GET /api/" + n + "/{id}");
                    eps.add("PUT /api/" + n + "/{id}");
                    eps.add("DELETE /api/" + n + "/{id}");
                    m.put("endpoints", eps);
                    out.add(m);
                }
                sendJson(exchange, 200, out);
            } catch (Exception e) {
                LOG.error("Failed to build /api/endpoints response", e);
                try { send(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}"); } catch (IOException ex) { LOG.error("Failed to send error response", ex); }
            }
        });

        // serve the UI builder static page
        server.createContext("/ui/builder", exchange -> {
            try (InputStream is = ApiServer.class.getResourceAsStream("/ui/builder.html")) {
                if (is == null) {
                    byte[] b = "Not found".getBytes();
                    exchange.sendResponseHeaders(404, b.length);
                    try (OutputStream os = exchange.getResponseBody()) { os.write(b); }
                    return;
                }
                byte[] b = is.readAllBytes();
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                exchange.sendResponseHeaders(200, b.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(b); }
            } catch (Exception e) {
                LOG.error("Failed to serve UI builder", e);
                try { send(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}"); } catch (IOException ex) { LOG.error("Failed to send error response", ex); }
            }
        });

        server.setExecutor(null);
        server.start();
        LOG.info("Server started on port {}", port);
    }

    static class SchemaHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            URI uri = exchange.getRequestURI();
            String path = uri.getPath(); // /schema or /schema/{name}
            String[] parts = path.split("/");
            Map<String,String> qmap = parseQuery(uri.getQuery());

            try {
                // POST /schema with optional preview=true
                if ("POST".equalsIgnoreCase(method) && parts.length == 2) {
                    boolean preview = "true".equalsIgnoreCase(qmap.getOrDefault("preview", "false"));
                    try (InputStream is = exchange.getRequestBody()) {
                        EntitySchema schema = M.readValue(is, EntitySchema.class);
                        if (schema.getName() == null || schema.getName().isEmpty()) {
                            send(exchange, 400, "{\"error\":\"missing schema name\"}");
                            return;
                        }
                        if (preview) {
                            List<String> plan = SchemaManager.generateMigrationPlan(schema);
                            sendJson(exchange, 200, plan);
                            return;
                        } else {
                            SchemaManager.saveSchema(schema);
                            send(exchange, 201, "{\"status\":\"ok\"}");
                            return;
                        }
                    }
                }

                // GET /schema - list (optionally paginated/search via query params)
                if ("GET".equalsIgnoreCase(method) && parts.length == 2) {
                    String pageS = qmap.get("page");
                    String sizeS = qmap.get("size");
                    String q = qmap.get("q");
                    if (pageS != null || sizeS != null || q != null) {
                        int page = 1; int size = 10;
                        try { if (pageS != null) page = Integer.parseInt(pageS); } catch (Exception ignored) {}
                        try { if (sizeS != null) size = Integer.parseInt(sizeS); } catch (Exception ignored) {}
                        List<String> names = SchemaManager.listSchemaNames(page, size, q);
                        sendJson(exchange, 200, names);
                        return;
                    } else {
                        List<String> names = SchemaManager.listSchemaNames();
                        sendJson(exchange, 200, names);
                        return;
                    }
                }

                // GET /schema/{name}
                if ("GET".equalsIgnoreCase(method) && parts.length == 3) {
                    String name = parts[2];
                    EntitySchema schema = SchemaManager.loadSchema(name);
                    if (schema == null) {
                        send(exchange, 404, "{\"error\":\"not found\"}");
                        return;
                    }
                    sendJson(exchange, 200, schema);
                    return;
                }

                send(exchange, 404, "{\"error\":\"unsupported\"}");
            } catch (Exception e) {
                LOG.error("SchemaHandler failed", e);
                try { send(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}"); } catch (IOException ex) { LOG.error("Failed to send error response", ex); }
            }
        }
    }

    static class EntityHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            URI uri = exchange.getRequestURI();
            String path = uri.getPath(); // /api or /api/{entity} or /api/{entity}/{id}
            String[] parts = path.split("/");
            if (parts.length < 3) {
                send(exchange, 400, "{\"error\":\"entity required\"}");
                return;
            }
            String entity = parts[2];
            EntitySchema schema = SchemaManager.loadSchema(entity);
            if (schema == null) {
                send(exchange, 404, "{\"error\":\"unknown entity\"}");
                return;
            }

            try {
                if ("POST".equalsIgnoreCase(method) && parts.length == 3) {
                    // create record
                    Map<String, Object> data = M.readValue(exchange.getRequestBody(), new TypeReference<>() {});
                    long id = insertRecord(schema, data);
                    Map<String, Object> resp = new HashMap<>();
                    resp.put("id", id);
                    sendJson(exchange, 201, resp);
                    return;
                }
                if ("GET".equalsIgnoreCase(method) && parts.length == 3) {
                    // list all
                    List<Map<String, Object>> rows = listAll(schema);
                    sendJson(exchange, 200, rows);
                    return;
                }
                if (parts.length == 4) {
                    String idStr = parts[3];
                    EntitySchema.Field pk = schema.getFields().stream().filter(EntitySchema.Field::isPrimaryKey).findFirst().orElse(null);
                    if (pk == null) {
                        send(exchange, 400, "{\"error\":\"no primary key defined\"}");
                        return;
                    }
                    if ("GET".equalsIgnoreCase(method)) {
                        Map<String, Object> row = getById(schema, idStr);
                        if (row == null) {
                            send(exchange, 404, "{\"error\":\"not found\"}");
                        } else {
                            sendJson(exchange, 200, row);
                        }
                        return;
                    }
                    if ("PUT".equalsIgnoreCase(method)) {
                        Map<String, Object> data = M.readValue(exchange.getRequestBody(), new TypeReference<>() {});
                        int updated = updateById(schema, idStr, data);
                        sendJson(exchange, 200, Collections.singletonMap("updated", updated));
                        return;
                    }
                    if ("DELETE".equalsIgnoreCase(method)) {
                        int deleted = deleteById(schema, idStr);
                        sendJson(exchange, 200, Collections.singletonMap("deleted", deleted));
                        return;
                    }
                }
                send(exchange, 405, "{\"error\":\"method not allowed\"}");
            } catch (Exception e) {
                LOG.error("EntityHandler error while processing request", e);
                if (e instanceof IllegalArgumentException) {
                    send(exchange, 400, "{\"error\":\"" + e.getMessage() + "\"}");
                } else {
                    send(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
                }
            }
        }

        private long insertRecord(EntitySchema schema, Map<String, Object> data) throws SQLException {
            List<EntitySchema.Field> fields = schema.getFields();
            List<String> cols = new ArrayList<>();
            List<String> placeholders = new ArrayList<>();
            List<Object> values = new ArrayList<>();
            for (EntitySchema.Field f : fields) {
                if (f.isPrimaryKey() && f.isAutoIncrement()) {
                    continue; // skip if auto
                }
                cols.add(quote(f.getName()));
                placeholders.add("?");
                Object raw = data.get(f.getName());
                Object val = coerceAndValidate(f, raw);
                values.add(val);
            }
            String sql = "INSERT INTO " + quote(schema.getName()) + " (" + String.join(",", cols) + ") VALUES (" + String.join(",", placeholders) + ")";
            try (Connection c = JdbcManager.getConnection(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                for (int i = 0; i < values.size(); i++) {
                    ps.setObject(i + 1, values.get(i));
                }
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) return rs.getLong(1);
                }
            }
            return -1;
        }

        private List<Map<String, Object>> listAll(EntitySchema schema) throws SQLException {
            String sql = "SELECT * FROM " + quote(schema.getName());
            try (Connection c = JdbcManager.getConnection(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
                return toList(rs);
            }
        }

        private Map<String, Object> getById(EntitySchema schema, String id) throws SQLException {
            EntitySchema.Field pk = schema.getFields().stream().filter(EntitySchema.Field::isPrimaryKey).findFirst().orElse(null);
            if (pk == null) return null;
            String sql = "SELECT * FROM " + quote(schema.getName()) + " WHERE " + quote(pk.getName()) + " = ?";
            try (Connection c = JdbcManager.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setObject(1, parseId(id, pk));
                try (ResultSet rs = ps.executeQuery()) {
                    List<Map<String, Object>> list = toList(rs);
                    return list.isEmpty() ? null : list.get(0);
                }
            }
        }

        private int updateById(EntitySchema schema, String id, Map<String, Object> data) throws SQLException {
            EntitySchema.Field pk = schema.getFields().stream().filter(EntitySchema.Field::isPrimaryKey).findFirst().orElse(null);
            if (pk == null) return 0;
            List<String> set = new ArrayList<>();
            List<Object> vals = new ArrayList<>();
            for (EntitySchema.Field f : schema.getFields()) {
                if (f.isPrimaryKey()) continue;
                if (data.containsKey(f.getName())) {
                    Object raw = data.get(f.getName());
                    Object val = coerceAndValidate(f, raw);
                    set.add(quote(f.getName()) + " = ?");
                    vals.add(val);
                }
            }
            if (set.isEmpty()) return 0;
            String sql = "UPDATE " + quote(schema.getName()) + " SET " + String.join(",", set) + " WHERE " + quote(pk.getName()) + " = ?";
            try (Connection c = JdbcManager.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                int i = 1;
                for (Object v : vals) ps.setObject(i++, v);
                ps.setObject(i, parseId(id, pk));
                return ps.executeUpdate();
            }
        }

        private int deleteById(EntitySchema schema, String id) throws SQLException {
            EntitySchema.Field pk = schema.getFields().stream().filter(EntitySchema.Field::isPrimaryKey).findFirst().orElse(null);
            if (pk == null) return 0;
            String sql = "DELETE FROM " + quote(schema.getName()) + " WHERE " + quote(pk.getName()) + " = ?";
            try (Connection c = JdbcManager.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setObject(1, parseId(id, pk));
                return ps.executeUpdate();
            }
        }

        private Object parseId(String idStr, EntitySchema.Field pk) {
            String t = pk.getType().toLowerCase();
            try {
                switch (t) {
                    case "int":
                    case "integer":
                        return Integer.parseInt(idStr);
                    case "long":
                        return Long.parseLong(idStr);
                    default:
                        return idStr;
                }
            } catch (Exception e) {
                return idStr;
            }
        }

        private List<Map<String, Object>> toList(ResultSet rs) throws SQLException {
            List<Map<String, Object>> list = new ArrayList<>();
            ResultSetMetaData md = rs.getMetaData();
            int cols = md.getColumnCount();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= cols; i++) {
                    String name = md.getColumnLabel(i);
                    Object val = rs.getObject(i);
                    row.put(name, val);
                }
                list.add(row);
            }
            return list;
        }

        private String quote(String id) {
            return "\"" + id + "\"";
        }

        private Object coerceAndValidate(EntitySchema.Field f, Object raw) {
            // required
            if (raw == null) {
                if (f.isRequired()) throw new IllegalArgumentException("field '" + f.getName() + "' is required");
                return null;
            }
            String t = f.getType().toLowerCase();
            try {
                switch (t) {
                    case "int":
                    case "integer":
                        if (raw instanceof Number) {
                            long lv = ((Number) raw).longValue();
                            if (f.getMin() != null && lv < f.getMin()) throw new IllegalArgumentException("field '" + f.getName() + "' below min");
                            if (f.getMax() != null && lv > f.getMax()) throw new IllegalArgumentException("field '" + f.getName() + "' above max");
                            return (int) lv;
                        }
                        int iv = Integer.parseInt(raw.toString());
                        if (f.getMin() != null && iv < f.getMin()) throw new IllegalArgumentException("field '" + f.getName() + "' below min");
                        if (f.getMax() != null && iv > f.getMax()) throw new IllegalArgumentException("field '" + f.getName() + "' above max");
                        return iv;
                    case "long":
                        if (raw instanceof Number) {
                            long lv = ((Number) raw).longValue();
                            if (f.getMin() != null && lv < f.getMin()) throw new IllegalArgumentException("field '" + f.getName() + "' below min");
                            if (f.getMax() != null && lv > f.getMax()) throw new IllegalArgumentException("field '" + f.getName() + "' above max");
                            return lv;
                        }
                        long lv = Long.parseLong(raw.toString());
                        if (f.getMin() != null && lv < f.getMin()) throw new IllegalArgumentException("field '" + f.getName() + "' below min");
                        if (f.getMax() != null && lv > f.getMax()) throw new IllegalArgumentException("field '" + f.getName() + "' above max");
                        return lv;
                    case "boolean":
                        if (raw instanceof Boolean) return raw;
                        String s = raw.toString().toLowerCase();
                        if ("true".equals(s) || "1".equals(s)) return true;
                        if ("false".equals(s) || "0".equals(s)) return false;
                        throw new IllegalArgumentException("field '" + f.getName() + "' invalid boolean");
                    case "date":
                    case "timestamp":
                        // accept epoch millis or ISO-8601
                        if (raw instanceof Number) {
                            return new Timestamp(((Number) raw).longValue());
                        }
                        String rs = raw.toString();
                        try {
                            Instant inst = Instant.parse(rs);
                            return Timestamp.from(inst);
                        } catch (Exception ex) {
                            // try parse long
                            long millis = Long.parseLong(rs);
                            return new Timestamp(millis);
                        }
                    case "text":
                    case "string":
                    default:
                        String str = raw.toString();
                        if (f.getLength() != null && str.length() > f.getLength()) throw new IllegalArgumentException("field '" + f.getName() + "' length exceeds " + f.getLength());
                        if (f.getPattern() != null && !f.getPattern().isEmpty()) {
                            if (!Pattern.compile(f.getPattern()).matcher(str).matches()) throw new IllegalArgumentException("field '" + f.getName() + "' does not match pattern");
                        }
                        return str;
                }
            } catch (NumberFormatException nfe) {
                throw new IllegalArgumentException("field '" + f.getName() + "' invalid format");
            }
        }

    }
}
