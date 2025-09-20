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

    private static Integer parseInteger(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return null; }
    }
    private static Long parseLong(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return null; }
    }
    private static Boolean parseBoolean(String s) {
        if (s == null || s.isBlank()) return null;
        String v = s.trim().toLowerCase();
        if ("true".equals(v) || "1".equals(v) || "yes".equals(v) || "y".equals(v)) return true;
        if ("false".equals(v) || "0".equals(v) || "no".equals(v) || "n".equals(v)) return false;
        return null;
    }

    private static String buildJdbcUrl(Map<String, String> data) {
        String type = Optional.ofNullable(data.get("type")).orElse("").toLowerCase();
        String params = Optional.ofNullable(data.get("params")).orElse("").trim();
        switch (type) {
            case "h2": {
                String mode = Optional.ofNullable(data.get("h2Mode")).orElse("file");
                if ("mem".equalsIgnoreCase(mode)) {
                    String name = Optional.ofNullable(data.get("h2MemName")).filter(s -> !s.isBlank()).orElse("test");
                    String url = "jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1";
                    if (!params.isEmpty()) url += ";" + params.replaceAll("[&?]+", ";");
                    return url;
                } else {
                    String file = Optional.ofNullable(data.get("h2File")).filter(s -> !s.isBlank()).orElse("./data/appbana");
                    String url = "jdbc:h2:" + file + ";AUTO_SERVER=TRUE";
                    if (!params.isEmpty()) url += ";" + params.replaceAll("[&?]+", ";");
                    return url;
                }
            }
            case "sqlite": {
                String file = Optional.ofNullable(data.get("sqliteFile")).filter(s -> !s.isBlank()).orElse("/path/to/file.db");
                String url = "jdbc:sqlite:" + file;
                if (!params.isEmpty()) url += (url.contains("?") ? "&" : "?") + params;
                return url;
            }
            case "postgres": {
                String host = Optional.ofNullable(data.get("host")).filter(s -> !s.isBlank()).orElse("localhost");
                String port = Optional.ofNullable(data.get("port")).filter(s -> !s.isBlank()).orElse("5432");
                String db = Optional.ofNullable(data.get("dbname")).filter(s -> !s.isBlank()).orElse("postgres");
                String url = "jdbc:postgresql://" + host + ":" + port + "/" + db;
                if (!params.isEmpty()) url += (url.contains("?") ? "&" : "?") + params;
                return url;
            }
            case "mysql": {
                String host = Optional.ofNullable(data.get("host")).filter(s -> !s.isBlank()).orElse("localhost");
                String port = Optional.ofNullable(data.get("port")).filter(s -> !s.isBlank()).orElse("3306");
                String db = Optional.ofNullable(data.get("dbname")).filter(s -> !s.isBlank()).orElse("test");
                String url = "jdbc:mysql://" + host + ":" + port + "/" + db;
                if (!params.isEmpty()) url += (url.contains("?") ? "&" : "?") + params;
                return url;
            }
            case "mariadb": {
                String host = Optional.ofNullable(data.get("host")).filter(s -> !s.isBlank()).orElse("localhost");
                String port = Optional.ofNullable(data.get("port")).filter(s -> !s.isBlank()).orElse("3306");
                String db = Optional.ofNullable(data.get("dbname")).filter(s -> !s.isBlank()).orElse("test");
                String url = "jdbc:mariadb://" + host + ":" + port + "/" + db;
                if (!params.isEmpty()) url += (url.contains("?") ? "&" : "?") + params;
                return url;
            }
            case "mssql": {
                String host = Optional.ofNullable(data.get("host")).filter(s -> !s.isBlank()).orElse("localhost");
                String port = Optional.ofNullable(data.get("port")).filter(s -> !s.isBlank()).orElse("1433");
                String db = Optional.ofNullable(data.get("dbname")).filter(s -> !s.isBlank()).orElse("master");
                String url = "jdbc:sqlserver://" + host + ":" + port + ";databaseName=" + db;
                if (!params.isEmpty()) url += ";" + params.replaceAll("[&?]+", ";");
                return url;
            }
            case "oracle": {
                String host = Optional.ofNullable(data.get("host")).filter(s -> !s.isBlank()).orElse("localhost");
                String port = Optional.ofNullable(data.get("port")).filter(s -> !s.isBlank()).orElse("1521");
                String svc = Optional.ofNullable(data.get("dbname")).filter(s -> !s.isBlank()).orElse("orcl");
                String url = "jdbc:oracle:thin:@" + host + ":" + port + "/" + svc;
                if (!params.isEmpty()) url += (url.contains("?") ? "&" : "?") + params;
                return url;
            }
            default:
                return null;
        }
    }

    private static String inferDriver(String type, String url, String provided) {
        if (provided != null && !provided.isBlank()) return provided;
        if (type != null) {
            String t = type.toLowerCase();
            switch (t) {
                case "h2": return "org.h2.Driver";
                case "postgres":
                case "postgresql": return "org.postgresql.Driver";
                case "mysql": return "com.mysql.cj.jdbc.Driver";
                case "mariadb": return "org.mariadb.jdbc.Driver";
                case "mssql":
                case "sqlserver": return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
                case "oracle": return "oracle.jdbc.OracleDriver";
                case "sqlite": return "org.sqlite.JDBC";
            }
        }
        if (url != null) {
            if (url.startsWith("jdbc:h2:")) return "org.h2.Driver";
            if (url.startsWith("jdbc:postgresql:")) return "org.postgresql.Driver";
            if (url.startsWith("jdbc:mysql:")) return "com.mysql.cj.jdbc.Driver";
            if (url.startsWith("jdbc:mariadb:")) return "org.mariadb.jdbc.Driver";
            if (url.startsWith("jdbc:sqlserver:")) return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
            if (url.startsWith("jdbc:oracle:")) return "oracle.jdbc.OracleDriver";
            if (url.startsWith("jdbc:sqlite:")) return "org.sqlite.JDBC";
        }
        return null;
    }

    public static void start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api", new EntityHandler());

        // Router integration for utility, schema, and datasource JSON endpoints
        org.example.api.Router router = new org.example.api.Router();
        router.get("/health", (req, res) -> {
            res.json(200, Map.of("status", "UP"));
        });
        router.get("/ready", (req, res) -> {
            long start = System.currentTimeMillis();
            try (Connection c = JdbcManager.getConnection()) {
                DatabaseMetaData md = c.getMetaData();
                long elapsed = System.currentTimeMillis() - start;
                AppConfig cfg = ConfigManager.getConfig();
                String active = cfg.getActiveDatasource();
                Map<String,Object> out = new LinkedHashMap<>();
                out.put("ok", true);
                out.put("activeDatasource", active);
                out.put("dbProduct", md.getDatabaseProductName());
                out.put("dbVersion", md.getDatabaseProductVersion());
                out.put("elapsedMs", elapsed);
                res.json(200, out);
            } catch (Exception ce) {
                long elapsed = System.currentTimeMillis() - start;
                res.json(503, Map.of(
                        "ok", false,
                        "error", ce.getMessage(),
                        "elapsedMs", elapsed
                ));
            }
        });
        router.get("/api/endpoints", (req, res) -> {
            try {
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
                res.json(200, out);
            } catch (Exception e) {
                LOG.error("Failed to build /api/endpoints response", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        });
        router.get("/openapi.json", (req, res) -> {
            try {
                List<String> names = SchemaManager.listSchemaNames();
                List<org.example.model.EntitySchema> schemas = new ArrayList<>();
                for (String n : names) {
                    org.example.model.EntitySchema s = SchemaManager.loadSchema(n);
                    if (s != null) schemas.add(s);
                }
                String spec = org.example.OpenApiGenerator.generate(schemas);
                res.text(200, spec, "application/json; charset=utf-8");
            } catch (Exception e) {
                LOG.error("Failed to serve OpenAPI spec", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        });
        router.get("/schema", (req, res) -> {
            String pageS = req.query("page");
            String sizeS = req.query("size");
            String q = req.query("q");
            if (pageS != null || sizeS != null || q != null) {
                int page = 1; int size = 10;
                try { if (pageS != null) page = Integer.parseInt(pageS); } catch (Exception ignored) {}
                try { if (sizeS != null) size = Integer.parseInt(sizeS); } catch (Exception ignored) {}
                List<String> names = SchemaManager.listSchemaNames(page, size, q);
                res.json(200, names);
            } else {
                List<String> names = SchemaManager.listSchemaNames();
                res.json(200, names);
            }
        });
        router.get("/schema/{name}", (req, res) -> {
            String name = req.pathParam("name");
            EntitySchema schema = SchemaManager.loadSchema(name);
            if (schema == null) { res.json(404, Map.of("error","not found")); return; }
            res.json(200, schema);
        });
        router.post("/schema", (req, res) -> {
            boolean preview = "true".equalsIgnoreCase(Optional.ofNullable(req.query("preview")).orElse("false"));
            EntitySchema schema = req.readJson(new TypeReference<EntitySchema>(){});
            if (schema.getName() == null || schema.getName().isEmpty()) { res.json(400, Map.of("error","missing schema name")); return; }
            if (preview) {
                List<String> plan = SchemaManager.generateMigrationPlan(schema);
                res.json(200, plan);
            } else {
                SchemaManager.saveSchema(schema);
                res.json(201, Map.of("status","ok"));
            }
        });
        router.get("/ui/datasource/config", (req, res) -> {
            AppConfig cfg = ConfigManager.getConfig();
            String active = cfg.getActiveDatasource();
            Map<String, Object> out = new LinkedHashMap<>();
            for (DatasourceConfig ds : cfg.getDatasources()) {
                if (ds.getName() != null && ds.getName().equals(active)) {
                    out.put("name", ds.getName());
                    out.put("jdbcUrl", ds.getJdbcUrl());
                    out.put("username", ds.getUsername());
                    out.put("driver", ds.getDriver());
                    out.put("type", ds.getType());
                    out.put("maxPoolSize", ds.getMaxPoolSize());
                    out.put("minIdle", ds.getMinIdle());
                    out.put("connectionTimeoutMs", ds.getConnectionTimeoutMs());
                    out.put("idleTimeoutMs", ds.getIdleTimeoutMs());
                    out.put("maxLifetimeMs", ds.getMaxLifetimeMs());
                    out.put("autoCommit", ds.getAutoCommit());
                    out.put("poolName", ds.getPoolName());
                    break;
                }
            }
            if (out.isEmpty()) {
                out.put("name", cfg.getName());
                out.put("jdbcUrl", cfg.getJdbcUrl());
                out.put("username", cfg.getUsername());
                out.put("driver", cfg.getDriver());
                out.put("type", null);
            }
            res.json(200, out);
        });
        router.get("/ui/datasource/list", (req, res) -> {
            AppConfig cfg = ConfigManager.getConfig();
            String active = cfg.getActiveDatasource();
            List<Map<String, Object>> list = new ArrayList<>();
            for (DatasourceConfig ds : cfg.getDatasources()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", ds.getName());
                m.put("jdbcUrl", ds.getJdbcUrl());
                m.put("username", ds.getUsername());
                m.put("driver", ds.getDriver());
                m.put("type", ds.getType());
                m.put("maxPoolSize", ds.getMaxPoolSize());
                m.put("minIdle", ds.getMinIdle());
                m.put("connectionTimeoutMs", ds.getConnectionTimeoutMs());
                m.put("idleTimeoutMs", ds.getIdleTimeoutMs());
                m.put("maxLifetimeMs", ds.getMaxLifetimeMs());
                m.put("autoCommit", ds.getAutoCommit());
                m.put("poolName", ds.getPoolName());
                m.put("active", ds.getName() != null && ds.getName().equals(active));
                list.add(m);
            }
            res.json(200, list);
        });
        router.post("/ui/datasource/save", (req, res) -> {
            AppConfig cfg = ConfigManager.getConfig();
            Map<String, String> data = req.readJson(new TypeReference<Map<String, String>>() {});
            String name = data.get("name");
            if (name == null || name.isBlank()) { res.json(400, Map.of("error","name required")); return; }
            String url = data.get("url");
            String user = data.get("username");
            String pw = data.get("password");
            String drv = data.get("driver");
            String type = data.get("type");
            if (url == null || url.isBlank()) {
                String built = buildJdbcUrl(data);
                if (built != null && !built.isBlank()) url = built;
            }
            Integer maxPoolSize = parseInteger(data.get("maxPoolSize"));
            Integer minIdle = parseInteger(data.get("minIdle"));
            Long connectionTimeoutMs = parseLong(data.get("connectionTimeoutMs"));
            Long idleTimeoutMs = parseLong(data.get("idleTimeoutMs"));
            Long maxLifetimeMs = parseLong(data.get("maxLifetimeMs"));
            Boolean autoCommit = parseBoolean(data.get("autoCommit"));
            String poolName = data.get("poolName");

            boolean found = false;
            for (DatasourceConfig ds : cfg.getDatasources()) {
                if (name.equals(ds.getName())) {
                    if (url != null) ds.setJdbcUrl(url);
                    if (user != null) ds.setUsername(user);
                    if (drv != null) ds.setDriver(drv);
                    if (type != null) ds.setType(type);
                    if (pw != null && !pw.isBlank()) ds.setPassword(pw);
                    if (maxPoolSize != null) ds.setMaxPoolSize(maxPoolSize);
                    if (minIdle != null) ds.setMinIdle(minIdle);
                    if (connectionTimeoutMs != null) ds.setConnectionTimeoutMs(connectionTimeoutMs);
                    if (idleTimeoutMs != null) ds.setIdleTimeoutMs(idleTimeoutMs);
                    if (maxLifetimeMs != null) ds.setMaxLifetimeMs(maxLifetimeMs);
                    if (autoCommit != null) ds.setAutoCommit(autoCommit);
                    if (poolName != null) ds.setPoolName(poolName);
                    found = true; break;
                }
            }
            if (!found) {
                DatasourceConfig ds = new DatasourceConfig();
                ds.setName(name);
                ds.setJdbcUrl(url);
                ds.setUsername(user);
                if (pw != null && !pw.isBlank()) ds.setPassword(pw);
                ds.setDriver(drv);
                ds.setType(type);
                ds.setMaxPoolSize(maxPoolSize);
                ds.setMinIdle(minIdle);
                ds.setConnectionTimeoutMs(connectionTimeoutMs);
                ds.setIdleTimeoutMs(idleTimeoutMs);
                ds.setMaxLifetimeMs(maxLifetimeMs);
                ds.setAutoCommit(autoCommit);
                ds.setPoolName(poolName);
                cfg.getDatasources().add(ds);
            }
            cfg.setActiveDatasource(name);
            ConfigManager.saveConfig(cfg);
            res.text(200, "Datasource configuration saved.", "application/json");
        });
        router.post("/ui/datasource/test", (req, res) -> {
            Map<String, String> data = req.readJson(new TypeReference<Map<String, String>>() {});
            String name = data.get("name");
            String url = data.get("url");
            String user = data.get("username");
            String pw = data.get("password");
            String drv = data.get("driver");
            String type = data.get("type");
            if (url == null || url.isBlank()) {
                String built = buildJdbcUrl(data);
                if (built != null && !built.isBlank()) url = built;
            }
            if ((url == null || url.isBlank()) && name != null && !name.isBlank()) {
                AppConfig cfg = ConfigManager.getConfig();
                for (DatasourceConfig ds : cfg.getDatasources()) {
                    if (name.equals(ds.getName())) {
                        url = ds.getJdbcUrl();
                        if (user == null) user = ds.getUsername();
                        if (pw == null || pw.isBlank()) pw = ds.getPassword();
                        if (drv == null) drv = ds.getDriver();
                        if (type == null) type = ds.getType();
                        break;
                    }
                }
            }
            if (url == null || url.isBlank()) { res.json(200, Map.of("ok", false, "error", "jdbc url is required or constructible from fields")); return; }
            String driver = inferDriver(type, url, drv);
            try { if (driver != null) Class.forName(driver); } catch (Throwable t) { res.json(200, Map.of("ok", false, "error", "driver not found: "+driver)); return; }
            long start = System.currentTimeMillis();
            java.sql.DriverManager.setLoginTimeout(5);
            Properties props = new Properties();
            if (user != null) props.setProperty("user", user);
            if (pw != null) props.setProperty("password", pw);
            try (Connection c = (props.isEmpty()? java.sql.DriverManager.getConnection(url) : java.sql.DriverManager.getConnection(url, props))) {
                DatabaseMetaData md = c.getMetaData();
                long elapsed = System.currentTimeMillis() - start;
                Map<String,Object> out = new LinkedHashMap<>();
                out.put("ok", true);
                out.put("message", "Connected");
                out.put("url", url);
                out.put("dbProduct", md.getDatabaseProductName());
                out.put("dbVersion", md.getDatabaseProductVersion());
                out.put("elapsedMs", elapsed);
                res.json(200, out);
            } catch (Exception ce) {
                long elapsed = System.currentTimeMillis() - start;
                res.json(200, Map.of("ok", false, "error", ce.getMessage(), "url", url, "elapsedMs", elapsed));
            }
        });
        router.post("/ui/datasource/activate", (req, res) -> {
            Map<String, String> data = req.readJson(new TypeReference<Map<String, String>>() {});
            String name = data.get("name");
            if (name == null || name.isBlank()) { res.json(400, Map.of("error","name required")); return; }
            AppConfig cfg = ConfigManager.getConfig();
            boolean exists = cfg.getDatasources().stream().anyMatch(d -> name.equals(d.getName()));
            if (!exists) { res.json(404, Map.of("error","not found")); return; }
            cfg.setActiveDatasource(name);
            ConfigManager.saveConfig(cfg);
            res.text(200, "Activated", "application/json");
        });
        router.post("/ui/datasource/delete", (req, res) -> {
            Map<String, String> data = req.readJson(new TypeReference<Map<String, String>>() {});
            String name = data.get("name");
            if (name == null || name.isBlank()) { res.json(400, Map.of("error","name required")); return; }
            AppConfig cfg = ConfigManager.getConfig();
            cfg.getDatasources().removeIf(d -> name.equals(d.getName()));
            if (name.equals(cfg.getActiveDatasource())) {
                if (!cfg.getDatasources().isEmpty()) cfg.setActiveDatasource(cfg.getDatasources().get(0).getName());
                else cfg.setActiveDatasource(null);
            }
            ConfigManager.saveConfig(cfg);
            res.text(200, "Deleted", "application/json");
        });

        server.createContext("/", exchange -> {
            try { router.handle(exchange); } catch (IOException ioe) { LOG.error("Router handle failed", ioe); }
        });

        // serve the UI builder static page (HTML)
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

        // serve the datasource config UI (HTML)
        server.createContext("/ui/datasource", exchange -> {
            try (InputStream is = ApiServer.class.getResourceAsStream("/ui/datasource.html")) {
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
                LOG.error("Failed to serve datasource UI", e);
                try { send(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}"); } catch (IOException ex) { LOG.error("Failed to send error response", ex); }
            }
        });

        // Add Swagger UI page (HTML)
        server.createContext("/ui/swagger", exchange -> {
            try (InputStream is = ApiServer.class.getResourceAsStream("/ui/swagger.html")) {
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
                LOG.error("Failed to serve swagger UI", e);
                try { send(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}"); } catch (IOException ex) { LOG.error("Failed to send error response", ex); }
            }
        });

        // Use virtual threads for handling requests (Java 21+ feature)
        server.setExecutor(r -> Thread.ofVirtual().start(r));
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
            if (id == null) return null;
            return '"' + id.toUpperCase() + '"';
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
