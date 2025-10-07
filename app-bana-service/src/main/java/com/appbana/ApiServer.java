package com.appbana;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.*;
import com.appbana.model.EntitySchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.KeyStore;
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

    public static Map<String,String> parseQuery(String query){
        Map<String,String> map = new HashMap<>();
        if(query==null||query.isEmpty()) return map;
        for(String part: query.split("&")){
            int i = part.indexOf('=');
            if(i>0) map.put(part.substring(0,i), part.substring(i+1));
            else map.put(part, "");
        }
        return map;
    }

    public static Integer parseInteger(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return null; }
    }
    public static Long parseLong(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return null; }
    }
    public static Boolean parseBoolean(String s) {
        if (s == null || s.isBlank()) return null;
        String v = s.trim().toLowerCase();
        if ("true".equals(v) || "1".equals(v) || "yes".equals(v) || "y".equals(v)) return true;
        if ("false".equals(v) || "0".equals(v) || "no".equals(v) || "n".equals(v)) return false;
        return null;
    }

    public static String buildJdbcUrl(Map<String, String> data) {
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
                if (!params.isEmpty()) url += (url.contains("?" ) ? "&" : "?") + params;
                return url;
            }
            case "postgres": {
                String host = Optional.ofNullable(data.get("host")).filter(s -> !s.isBlank()).orElse("localhost");
                String port = Optional.ofNullable(data.get("port")).filter(s -> !s.isBlank()).orElse("5432");
                String db = Optional.ofNullable(data.get("dbname")).filter(s -> !s.isBlank()).orElse("postgres");
                String url = "jdbc:postgresql://" + host + ":" + port + "/" + db;
                if (!params.isEmpty()) url += (url.contains("?" ) ? "&" : "?") + params;
                return url;
            }
            case "mysql": {
                String host = Optional.ofNullable(data.get("host")).filter(s -> !s.isBlank()).orElse("localhost");
                String port = Optional.ofNullable(data.get("port")).filter(s -> !s.isBlank()).orElse("3306");
                String db = Optional.ofNullable(data.get("dbname")).filter(s -> !s.isBlank()).orElse("test");
                String url = "jdbc:mysql://" + host + ":" + port + "/" + db;
                if (!params.isEmpty()) url += (url.contains("?" ) ? "&" : "?") + params;
                return url;
            }
            case "mariadb": {
                String host = Optional.ofNullable(data.get("host")).filter(s -> !s.isBlank()).orElse("localhost");
                String port = Optional.ofNullable(data.get("port")).filter(s -> !s.isBlank()).orElse("3306");
                String db = Optional.ofNullable(data.get("dbname")).filter(s -> !s.isBlank()).orElse("test");
                String url = "jdbc:mariadb://" + host + ":" + port + "/" + db;
                if (!params.isEmpty()) url += (url.contains("?" ) ? "&" : "?") + params;
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
                String url = "jdbc:oracle:thin@" + host + ":" + port + "/" + svc;
                if (!params.isEmpty()) url += (url.contains("?" ) ? "&" : "?") + params;
                return url;
            }
            default:
                return null;
        }
    }

    public static String sanitizeUrl(String url) {
        if (url == null) return null;
        String u = url;
        // Mask common password keys in query or ;key=value formats
        String[] keys = {"password", "pwd", "pass"};
        for (String k : keys) {
            // mask in ?k=...& or &k=...& patterns
            u = u.replaceAll("(?i)([?&]" + k + ")=([^&;]+)", "$1=***");
            // mask in ;k=...; patterns (SQL Server / H2)
            u = u.replaceAll("(?i)(;" + k + ")=([^;&]+)", "$1=***");
        }
        return u;
    }

    public static Map<String, Object> errorDetails(Throwable ce) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", false);
        m.put("error", ce.getMessage());
        if (ce instanceof SQLException) {
            SQLException se = (SQLException) ce;
            m.put("sqlState", se.getSQLState());
            m.put("errorCode", se.getErrorCode());
        }
        return m;
    }

    public static boolean authEnabled(AppConfig cfg) {
        return cfg.getAdminToken() != null && !cfg.getAdminToken().isBlank() || cfg.getReadToken() != null && !cfg.getReadToken().isBlank();
    }
    public static String extractToken(com.appbana.api.Router.HttpRequest req) {
        String tok = req.header("X-AppBana-Token");
        if (tok == null || tok.isBlank()) {
            String auth = req.header("Authorization");
            if (auth != null && auth.toLowerCase(Locale.ROOT).startsWith("bearer ")) tok = auth.substring(7).trim();
        }
        return tok;
    }
    public static boolean hasAdmin(String token, AppConfig cfg) {
        String at = cfg.getAdminToken();
        return at != null && !at.isBlank() && at.equals(token);
    }
    public static boolean hasRead(String token, AppConfig cfg) {
        if (hasAdmin(token, cfg)) return true;
        String rt = cfg.getReadToken();
        return rt != null && !rt.isBlank() && rt.equals(token);
    }

    public static void startJdk(int port) throws IOException {
        AppConfig cfg = ConfigManager.getConfig();
        // Always start the HTTP server (can redirect to HTTPS if configured)
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        configureServer(httpServer);
        boolean httpsStarted = false;

        // Optionally start HTTPS server
        if (Boolean.TRUE.equals(cfg.getHttpsEnabled())) {
            Integer httpsPort = cfg.getHttpsPort() != null ? cfg.getHttpsPort() : 8443;
            String ksPath = cfg.getKeystorePath();
            String ksPass = cfg.getKeystorePassword();
            String keyPass = cfg.getKeyPassword() != null ? cfg.getKeyPassword() : ksPass;
            if (ksPath == null || ksPath.isBlank() || ksPass == null) {
                LOG.error("HTTPS enabled but keystorePath/keystorePassword not provided; skipping HTTPS startup");
            } else {
                try {
                    char[] kp = keyPass != null ? keyPass.toCharArray() : ksPass.toCharArray();
                    char[] ksp = ksPass.toCharArray();
                    KeyStore ks = KeyStore.getInstance(ksPath.toLowerCase().endsWith(".p12") || ksPath.toLowerCase().endsWith(".pkcs12") ? "PKCS12" : "JKS");
                    try (FileInputStream fis = new FileInputStream(ksPath)) { ks.load(fis, ksp); }
                    KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                    kmf.init(ks, kp);
                    SSLContext sslContext = SSLContext.getInstance("TLS");
                    sslContext.init(kmf.getKeyManagers(), null, null);

                    HttpsServer httpsServer = HttpsServer.create(new InetSocketAddress(httpsPort), 0);
                    httpsServer.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
                        @Override public void configure(HttpsParameters params) {
                            try {
                                SSLContext c = getSSLContext();
                                SSLEngine engine = c.createSSLEngine();
                                // Build SSLParameters and apply to avoid deprecated HttpsParameters setters
                                SSLParameters sslParams = c.getDefaultSSLParameters();
                                sslParams.setNeedClientAuth(false);
                                sslParams.setCipherSuites(engine.getEnabledCipherSuites());
                                sslParams.setProtocols(engine.getEnabledProtocols());
                                params.setSSLParameters(sslParams);
                            } catch (Exception ex) {
                                LOG.error("Failed to configure HTTPS parameters", ex);
                            }
                        }
                    });
                    configureServer(httpsServer);
                    httpsServer.start();
                    httpsStarted = true;
                    LOG.info("HTTPS server started on port {}", httpsPort);

                    // If redirect is enabled, overwrite HTTP server handler to redirect
                    if (Boolean.TRUE.equals(cfg.getRedirectHttpToHttps())) {
                        // Replace default handler with a redirect handler
                        httpServer.removeContext("/");
                        httpServer.createContext("/", ex -> {
                            try {
                                String host = Optional.ofNullable(ex.getRequestHeaders().getFirst("Host")).orElse("localhost");
                                // strip port from Host if present
                                String hostOnly = host;
                                int idx = host.indexOf(":");
                                if (idx >= 0) hostOnly = host.substring(0, idx);
                                URI uri = ex.getRequestURI();
                                String loc = "https://" + hostOnly + ":" + httpsPort + uri.toString();
                                Headers h = ex.getResponseHeaders();
                                h.set("Location", loc);
                                ex.sendResponseHeaders(308, -1);
                            } finally {
                                ex.close();
                            }
                        });
                        LOG.info("HTTP requests will be redirected to HTTPS port {}", httpsPort);
                    }
                } catch (Exception e) {
                    LOG.error("Failed to start HTTPS server: {}", e.getMessage(), e);
                }
            }
        }

        // Use virtual threads for handling requests (Java 21+)
        httpServer.setExecutor(r -> Thread.ofVirtual().start(r));
        httpServer.start();
        LOG.info("HTTP server started on port {}{}", port, httpsStarted ? " (HTTPS also enabled)" : "");
    }

    public static com.appbana.api.Router buildRouter() {
        com.appbana.api.Router router = new com.appbana.api.Router();
        router.get("/health", (req, res) -> res.json(200, Map.of("status", "UP")));
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
                Map<String,Object> out = errorDetails(ce);
                out.put("elapsedMs", elapsed);
                res.json(503, out);
            }
        });
        router.get("/api/endpoints", (req, res) -> {
            AppConfig cfg = ConfigManager.getConfig();
            if (authEnabled(cfg)) {
                String tok = extractToken(req);
                if (!hasRead(tok, cfg)) { res.json(401, Map.of("error","unauthorized")); return; }
            }
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
            AppConfig cfg = ConfigManager.getConfig();
            if (authEnabled(cfg)) {
                String tok = extractToken(req);
                if (!hasRead(tok, cfg)) { res.json(401, Map.of("error","unauthorized")); return; }
            }
            try {
                List<String> names = SchemaManager.listSchemaNames();
                List<com.appbana.model.EntitySchema> schemas = new ArrayList<>();
                for (String n : names) {
                    com.appbana.model.EntitySchema s = SchemaManager.loadSchema(n);
                    if (s != null) schemas.add(s);
                }
                String spec = com.appbana.OpenApiGenerator.generate(schemas);
                res.text(200, spec, "application/json; charset=utf-8");
            } catch (Exception e) {
                LOG.error("Failed to serve OpenAPI spec", e);
                res.json(500, Map.of("error", e.getMessage()));
            }
        });
        router.get("/schema", (req, res) -> {
            AppConfig cfg = ConfigManager.getConfig();
            if (authEnabled(cfg)) {
                String tok = extractToken(req);
                if (!hasRead(tok, cfg)) { res.json(401, Map.of("error","unauthorized")); return; }
            }
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
            AppConfig cfg = ConfigManager.getConfig();
            if (authEnabled(cfg)) {
                String tok = extractToken(req);
                if (!hasRead(tok, cfg)) { res.json(401, Map.of("error","unauthorized")); return; }
            }
            String name = req.pathParam("name");
            EntitySchema schema = SchemaManager.loadSchema(name);
            if (schema == null) { res.json(404, Map.of("error","not found")); return; }
            res.json(200, schema);
        });
        router.post("/schema", (req, res) -> {
            AppConfig cfg = ConfigManager.getConfig();
            if (authEnabled(cfg)) {
                String tok = extractToken(req);
                if (!hasAdmin(tok, cfg)) { res.json(401, Map.of("error","unauthorized")); return; }
            }
            boolean preview = "true".equalsIgnoreCase(Optional.ofNullable(req.query("preview")).orElse("false"));
            EntitySchema schema = req.readJson(new TypeReference<>(){});
            if (schema.getName() == null || schema.getName().isEmpty()) { res.json(400, Map.of("error","missing schema name")); return; }
            if (preview) {
                List<String> plan = SchemaManager.generateMigrationPlan(schema);
                res.json(200, plan);
            } else {
                SchemaManager.saveSchema(schema);
                res.json(201, Map.of("status","ok"));
            }
        });
        // --- NEW: schema summaries (name + datasource) ---
        router.get("/schema/summaries", (req, res) -> {
            AppConfig cfg = ConfigManager.getConfig();
            if (authEnabled(cfg)) {
                String tok = extractToken(req);
                if (!hasRead(tok, cfg)) { res.json(401, Map.of("error","unauthorized")); return; }
            }
            try {
                res.json(200, SchemaManager.listSchemaSummaries());
            } catch (Exception e) {
                res.json(500, Map.of("error", e.getMessage()));
            }
        });
        // --- NEW: schema migration history ---
        router.get("/schema/{name}/migrations", (req, res) -> {
            AppConfig cfg = ConfigManager.getConfig();
            if (authEnabled(cfg)) {
                String tok = extractToken(req);
                if (!hasRead(tok, cfg)) { res.json(401, Map.of("error","unauthorized")); return; }
            }
            String name = req.pathParam("name");
            try {
                List<Map<String,Object>> hist = SchemaManager.listMigrations(name);
                res.json(200, hist);
            } catch (Exception e) {
                res.json(500, Map.of("error", e.getMessage()));
            }
        });
        // --- NEW: schema delete (optional dropTable) ---
        router.delete("/schema/{name}", (req, res) -> {
            AppConfig cfg = ConfigManager.getConfig();
            if (authEnabled(cfg)) {
                String tok = extractToken(req);
                if (!hasAdmin(tok, cfg)) { res.json(401, Map.of("error","unauthorized")); return; }
            }
            String name = req.pathParam("name");
            boolean drop = "true".equalsIgnoreCase(Optional.ofNullable(req.query("dropTable")).orElse("false"));
            try {
                boolean ok = SchemaManager.deleteSchema(name, drop);
                if (!ok) { res.json(404, Map.of("error","not found")); return; }
                res.json(200, Map.of("status","deleted","dropTable", drop));
            } catch (Exception e) {
                res.json(500, Map.of("error", e.getMessage()));
            }
        });
        router.get("/ui/datasource/config", (req, res) -> {
            AppConfig cfg = ConfigManager.getConfig();
            if (authEnabled(cfg)) {
                String tok = extractToken(req);
                if (!hasRead(tok, cfg)) { res.json(401, Map.of("error","unauthorized")); return; }
            }
            AppConfig cfgNow = ConfigManager.getConfig();
            String active = cfgNow.getActiveDatasource();
            Map<String, Object> out = new LinkedHashMap<>();
            for (DatasourceConfig ds : cfgNow.getDatasources()) {
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
                out.put("name", cfgNow.getName());
                out.put("jdbcUrl", cfgNow.getJdbcUrl());
                out.put("username", cfgNow.getUsername());
                out.put("driver", cfgNow.getDriver());
                out.put("type", null);
            }
            res.json(200, out);
        });
        router.get("/ui/datasource/list", (req, res) -> {
            AppConfig cfg = ConfigManager.getConfig();
            if (authEnabled(cfg)) {
                String tok = extractToken(req);
                if (!hasRead(tok, cfg)) { res.json(401, Map.of("error","unauthorized")); return; }
            }
            AppConfig cfgNow = ConfigManager.getConfig();
            String active = cfgNow.getActiveDatasource();
            List<Map<String, Object>> list = new ArrayList<>();
            for (DatasourceConfig ds : cfgNow.getDatasources()) {
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
                m.put("lastTestOk", ds.getLastTestOk());
                m.put("lastTestAtEpochMs", ds.getLastTestAtEpochMs());
                m.put("lastTestMessage", ds.getLastTestMessage());
                m.put("lastTestDbProduct", ds.getLastTestDbProduct());
                m.put("lastTestDbVersion", ds.getLastTestDbVersion());
                m.put("lastTestElapsedMs", ds.getLastTestElapsedMs());
                m.put("active", ds.getName() != null && ds.getName().equals(active));
                list.add(m);
            }
            res.json(200, list);
        });
        router.post("/ui/datasource/save", (req, res) -> {
            AppConfig cfgAuth = ConfigManager.getConfig();
            if (authEnabled(cfgAuth)) {
                String tok = extractToken(req);
                if (!hasAdmin(tok, cfgAuth)) { res.json(401, Map.of("error","unauthorized")); return; }
            }
            Map<String, String> data = req.readJson(new TypeReference<>() {});
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

            AppConfig cfgNow = ConfigManager.getConfig();
            boolean found = false;
            for (DatasourceConfig ds : cfgNow.getDatasources()) {
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
                cfgNow.getDatasources().add(ds);
            }
            cfgNow.setActiveDatasource(name);
            ConfigManager.saveConfig(cfgNow);
            res.text(200, "Datasource configuration saved.", "application/json");
        });
        router.post("/ui/datasource/test", (req, res) -> {
            AppConfig cfgAuth = ConfigManager.getConfig();
            if (authEnabled(cfgAuth)) {
                String tok = extractToken(req);
                if (!hasAdmin(tok, cfgAuth)) { res.json(401, Map.of("error","unauthorized")); return; }
            }
            Map<String, String> data = req.readJson(new TypeReference<>() {});
            String name = data.get("name");
            String url = data.get("url");
            String user = data.get("username");
            String pw = data.get("password");
            String drv = data.get("driver");
            String type = data.get("type");
            Integer timeoutSec = parseInteger(data.get("timeoutSec"));
            if (timeoutSec == null || timeoutSec <= 0) timeoutSec = 5;
            if (timeoutSec > 60) timeoutSec = 60; // cap
            if (url == null || url.isBlank()) {
                String built = buildJdbcUrl(data);
                if (built != null && !built.isBlank()) url = built;
            }
            DatasourceConfig toPersist = null;
            if ((url == null || url.isBlank()) && name != null && !name.isBlank()) {
                AppConfig cfgNow = ConfigManager.getConfig();
                for (DatasourceConfig ds : cfgNow.getDatasources()) {
                    if (name.equals(ds.getName())) {
                        url = ds.getJdbcUrl();
                        if (user == null) user = ds.getUsername();
                        if (pw == null || pw.isBlank()) pw = ds.getPassword();
                        if (drv == null) drv = ds.getDriver();
                        if (type == null) type = ds.getType();
                        toPersist = ds;
                        break;
                    }
                }
            }
            if (url == null || url.isBlank()) { res.json(200, Map.of("ok", false, "error", "jdbc url is required or constructible from fields")); return; }
            String driver = DriverUtil.inferDriver(type, url, drv);
            try { if (driver != null) Class.forName(driver); } catch (Throwable t) { res.json(200, Map.of("ok", false, "error", "driver not found: "+driver)); return; }
            long start = System.currentTimeMillis();
            java.sql.DriverManager.setLoginTimeout(timeoutSec);
            Properties props = new Properties();
            if (user != null) props.setProperty("user", user);
            if (pw != null && !pw.isEmpty()) props.setProperty("password", pw);
            try (Connection c = (props.isEmpty()? java.sql.DriverManager.getConnection(url) : java.sql.DriverManager.getConnection(url, props))) {
                DatabaseMetaData md = c.getMetaData();
                long elapsed = System.currentTimeMillis() - start;
                Map<String,Object> out = new LinkedHashMap<>();
                out.put("ok", true);
                out.put("message", "Connected");
                out.put("url", sanitizeUrl(url));
                out.put("dbProduct", md.getDatabaseProductName());
                out.put("dbVersion", md.getDatabaseProductVersion());
                out.put("elapsedMs", elapsed);
                // persist last test result if testing a named datasource
                if (toPersist != null) {
                    toPersist.setLastTestOk(true);
                    toPersist.setLastTestAtEpochMs(System.currentTimeMillis());
                    toPersist.setLastTestMessage("Connected");
                    toPersist.setLastTestDbProduct(md.getDatabaseProductName());
                    toPersist.setLastTestDbVersion(md.getDatabaseProductVersion());
                    toPersist.setLastTestElapsedMs(elapsed);
                    ConfigManager.saveConfig(ConfigManager.getConfig());
                }
                res.json(200, out);
            } catch (Exception ce) {
                long elapsed = System.currentTimeMillis() - start;
                Map<String, Object> out = errorDetails(ce);
                out.put("url", sanitizeUrl(url));
                out.put("elapsedMs", elapsed);
                if (toPersist != null) {
                    toPersist.setLastTestOk(false);
                    toPersist.setLastTestAtEpochMs(System.currentTimeMillis());
                    toPersist.setLastTestMessage(ce.getMessage());
                    toPersist.setLastTestDbProduct(null);
                    toPersist.setLastTestDbVersion(null);
                    toPersist.setLastTestElapsedMs(elapsed);
                    ConfigManager.saveConfig(ConfigManager.getConfig());
                }
                res.json(200, out);
            }
        });
        router.post("/ui/datasource/activate", (req, res) -> {
            AppConfig cfg = ConfigManager.getConfig();
            if (authEnabled(cfg)) {
                String tok = extractToken(req);
                if (!hasAdmin(tok, cfg)) { res.json(401, Map.of("error","unauthorized")); return; }
            }
            Map<String, String> data = req.readJson(new TypeReference<>() {});
            String name = data.get("name");
            if (name == null || name.isBlank()) { res.json(400, Map.of("error","name required")); return; }
            AppConfig cfgNow = ConfigManager.getConfig();
            boolean exists = cfgNow.getDatasources().stream().anyMatch(d -> name.equals(d.getName()));
            if (!exists) { res.json(404, Map.of("error","not found")); return; }
            cfgNow.setActiveDatasource(name);
            ConfigManager.saveConfig(cfgNow);
            res.text(200, "Activated", "application/json");
        });
        router.post("/ui/datasource/delete", (req, res) -> {
            AppConfig cfg = ConfigManager.getConfig();
            if (authEnabled(cfg)) {
                String tok = extractToken(req);
                if (!hasAdmin(tok, cfg)) { res.json(401, Map.of("error","unauthorized")); return; }
            }
            Map<String, String> data = req.readJson(new TypeReference<>() {});
            String name = data.get("name");
            if (name == null || name.isBlank()) { res.json(400, Map.of("error","name required")); return; }
            AppConfig cfgNow = ConfigManager.getConfig();
            cfgNow.getDatasources().removeIf(d -> name.equals(d.getName()));
            if (name.equals(cfgNow.getActiveDatasource())) {
                if (!cfgNow.getDatasources().isEmpty()) cfgNow.setActiveDatasource(cfgNow.getDatasources().getFirst().getName());
                else cfgNow.setActiveDatasource(null);
            }
            ConfigManager.saveConfig(cfgNow);
            res.text(200, "Deleted", "application/json");
        });
        router.get("/ui/datasource/health", (req, res) -> {
            AppConfig cfg = ConfigManager.getConfig();
            if (authEnabled(cfg)) {
                String tok = extractToken(req);
                if (!hasRead(tok, cfg)) { res.json(401, Map.of("error","unauthorized")); return; }
            }
            String name = req.query("name");
            Integer timeoutSec = parseInteger(req.query("timeoutSec"));
            if (timeoutSec == null || timeoutSec <= 0) timeoutSec = 3;
            if (timeoutSec > 60) timeoutSec = 60;
            AppConfig cfgNow = ConfigManager.getConfig();
            DatasourceConfig target = null;
            if (name != null && !name.isBlank()) {
                for (DatasourceConfig d : cfgNow.getDatasources()) {
                    if (name.equals(d.getName())) { target = d; break; }
                }
                if (target == null) { res.json(404, Map.of("ok", false, "error", "datasource not found")); return; }
            } else {
                String active = cfgNow.getActiveDatasource();
                for (DatasourceConfig d : cfgNow.getDatasources()) {
                    if (active != null && active.equals(d.getName())) { target = d; break; }
                }
                if (target == null && !cfgNow.getDatasources().isEmpty()) target = cfgNow.getDatasources().getFirst();
            }
            String url = target.getJdbcUrl();
            String user = target.getUsername();
            String pw = target.getPassword();
            String driver = target.getDriver();
            String type = target.getType();
            driver = DriverUtil.inferDriver(type, url, driver);
            try { if (driver != null) Class.forName(driver); } catch (Throwable t) { res.json(200, Map.of("ok", false, "error", "driver not found: "+driver)); return; }
            long start = System.currentTimeMillis();
            java.sql.DriverManager.setLoginTimeout(timeoutSec);
            Properties props = new Properties();
            if (user != null) props.setProperty("user", user);
            if (pw != null && !pw.isEmpty()) props.setProperty("password", pw);
            try (Connection c = (props.isEmpty()? java.sql.DriverManager.getConnection(url) : java.sql.DriverManager.getConnection(url, props))) {
                DatabaseMetaData md = c.getMetaData();
                long elapsed = System.currentTimeMillis() - start;
                Map<String,Object> out = new LinkedHashMap<>();
                out.put("ok", true);
                out.put("name", target.getName());
                out.put("url", sanitizeUrl(url));
                out.put("dbProduct", md.getDatabaseProductName());
                out.put("dbVersion", md.getDatabaseProductVersion());
                out.put("elapsedMs", elapsed);
                res.json(200, out);
            } catch (Exception ce) {
                long elapsed = System.currentTimeMillis() - start;
                Map<String, Object> out = errorDetails(ce);
                out.put("name", target.getName());
                out.put("url", sanitizeUrl(url));
                out.put("elapsedMs", elapsed);
                res.json(200, out);
            }
        });

        router.get("/audit", (req, res) -> {
            AppConfig cfg = ConfigManager.getConfig();
            if (authEnabled(cfg)) {
                String tok = extractToken(req);
                if (!hasRead(tok, cfg)) { res.json(401, Map.of("error","unauthorized")); return; }
            }
            String entity = req.query("entity");
            String pk = req.query("pk");
            int limit = 50; int offset = 0;
            try { String ls = req.query("limit"); if (ls!=null) limit = Integer.parseInt(ls); } catch (Exception ignored) {}
            try { String os = req.query("offset"); if (os!=null) offset = Integer.parseInt(os); } catch (Exception ignored) {}
            if (limit <=0) limit = 50; if (limit>500) limit = 500; if (offset<0) offset=0;
            try {
                Map<String,Object> out = AuditLogService.query(entity, pk, limit, offset);
                res.json(200, out);
            } catch (Exception e) {
                res.json(500, Map.of("error", e.getMessage()));
            }
        });

        // CRUD endpoints via router
        router.post("/api/{entity}", (req, res) -> {
            AppConfig cfg = ConfigManager.getConfig();
            String actor = "anonymous"; if (authEnabled(cfg)) { String tok = extractToken(req); actor = (tok!=null&&!tok.isBlank())?tok:"anonymous"; if (!hasAdmin(tok, cfg)) { res.json(401, Map.of("error","unauthorized")); return; } }
            String entity = req.pathParam("entity");
            EntitySchema schema = SchemaManager.loadSchema(entity);
            if (schema == null) { res.json(404, Map.of("error","unknown entity")); return; }
            Map<String, Object> data = req.readJson(new TypeReference<>() {});
            try {
                long id = insertRecord(schema, data);
                // after image
                Map<String,Object> after = getById(schema, String.valueOf(id));
                AuditLogService.log("INSERT", schema.getName(), String.valueOf(id), actor, null, after);
                res.json(201, Map.of("id", id));
            } catch (SQLException e) {
                LOG.error("Insert failed for entity {}", entity, e);
                res.json(500, errorDetails(e));
            }
        });
        router.post("/api/{entity}/batch", (req, res) -> {
            AppConfig cfg = ConfigManager.getConfig();
            String actor = "anonymous"; if (authEnabled(cfg)) { String tok = extractToken(req); actor = (tok!=null&&!tok.isBlank())?tok:"anonymous"; if (!hasAdmin(tok, cfg)) { res.json(401, Map.of("error","unauthorized")); return; } }
            String entity = req.pathParam("entity");
            EntitySchema schema = SchemaManager.loadSchema(entity);
            if (schema == null) { res.json(404, Map.of("error","unknown entity")); return; }
            List<Map<String,Object>> payload;
            try { payload = req.readJson(new TypeReference<>(){}); } catch (Exception e){ res.json(400, Map.of("error","invalid json array")); return; }
            if (payload == null) { res.json(400, Map.of("error","array required")); return; }
            int max = 1000; // safety cap
            if (payload.size() > max) { res.json(400, Map.of("error","batch too large","max",max)); return; }
            try {
                Map<String,Object> out = insertBatch(schema, payload);
                Object idsObj = out.get("ids");
                if (idsObj instanceof List<?> idList) {
                    for (Object idVal : idList) {
                        if (idVal == null) continue;
                        try {
                            Map<String,Object> after = getById(schema, String.valueOf(idVal));
                            AuditLogService.log("INSERT", schema.getName(), String.valueOf(idVal), actor, null, after);
                        } catch (Exception ignore) { }
                    }
                }
                res.json(201, out);
            } catch (Exception e) {
                LOG.error("Batch insert failed for {}", entity, e);
                res.json(500, errorDetails(e));
            }
        });
        router.get("/api/{entity}", (req, res) -> {
            AppConfig cfg = ConfigManager.getConfig();
            if (authEnabled(cfg)) {
                String tok = extractToken(req);
                if (!hasRead(tok, cfg)) { res.json(401, Map.of("error","unauthorized")); return; }
            }
            String entity = req.pathParam("entity");
            EntitySchema schema = SchemaManager.loadSchema(entity);
            if (schema == null) { res.json(404, Map.of("error","unknown entity")); return; }
            String limitS = req.query("limit");
            String offsetS = req.query("offset");
            String q = req.query("q");
            String fieldsParam = req.query("fields");
            String sortParam = req.query("sort");
            String filterParam = req.query("filter");
            String countFlag = req.query("count");
            Map<String,Object> filters = parseFilters(filterParam, schema);
            boolean countOnly = "true".equalsIgnoreCase(countFlag) || (countFlag != null && countFlag.equals("1"));
            Integer limit = null; Integer offset = null;
            boolean anyAdv = countOnly || q!=null || fieldsParam!=null || sortParam!=null || filterParam!=null || limitS!=null || offsetS!=null;
            if (limitS != null || offsetS != null || q!=null || fieldsParam!=null || sortParam!=null || filterParam!=null) {
                try { limit = limitS != null ? Integer.parseInt(limitS) : 50; } catch (Exception ignore) { limit = 50; }
                try { offset = offsetS != null ? Integer.parseInt(offsetS) : 0; } catch (Exception ignore) { offset = 0; }
                if (limit <= 0) limit = 50; if (limit > 500) limit = 500; if (offset < 0) offset = 0;
            }
            try {
                if (!anyAdv) {
                    List<Map<String,Object>> rows = listAll(schema);
                    res.json(200, rows);
                } else {
                    if (countOnly) {
                        long total = countOnly(schema, q, filters);
                        Map<String,Object> out = new LinkedHashMap<>();
                        out.put("total", total);
                        if (q != null && !q.isBlank()) out.put("query", q);
                        if (!filters.isEmpty()) out.put("filters", filters);
                        res.json(200, out);
                    } else {
                        Map<String,Object> out = listAdvanced(schema, limit, offset, q, fieldsParam, sortParam, filters);
                        res.json(200, out);
                    }
                }
            } catch (SQLException e) {
                LOG.error("List failed for entity {}", entity, e);
                res.json(500, errorDetails(e));
            }
        });
        router.get("/api/{entity}/{id}", (req, res) -> {
            AppConfig cfg = ConfigManager.getConfig();
            if (authEnabled(cfg)) {
                String tok = extractToken(req);
                if (!hasRead(tok, cfg)) { res.json(401, Map.of("error","unauthorized")); return; }
            }
            String entity = req.pathParam("entity");
            String idStr = req.pathParam("id");
            EntitySchema schema = SchemaManager.loadSchema(entity);
            if (schema == null) { res.json(404, Map.of("error","unknown entity")); return; }
            try {
                Map<String,Object> row = getById(schema, idStr);
                if (row == null) res.json(404, Map.of("error","not found")); else res.json(200, row);
            } catch (SQLException e) {
                LOG.error("Get by id failed for entity {} id {}", entity, idStr, e);
                res.json(500, errorDetails(e));
            }
        });
        router.put("/api/{entity}/{id}", (req, res) -> {
            AppConfig cfg = ConfigManager.getConfig();
            String actor = "anonymous"; if (authEnabled(cfg)) { String tok = extractToken(req); actor = (tok!=null&&!tok.isBlank())?tok:"anonymous"; if (!hasAdmin(tok, cfg)) { res.json(401, Map.of("error","unauthorized")); return; } }
            String entity = req.pathParam("entity");
            String idStr = req.pathParam("id");
            EntitySchema schema = SchemaManager.loadSchema(entity);
            if (schema == null) { res.json(404, Map.of("error","unknown entity")); return; }
            Map<String, Object> data = req.readJson(new TypeReference<>() {});
            try {
                Map<String,Object> before = getById(schema, idStr);
                int updated = updateById(schema, idStr, data);
                Map<String,Object> after = updated>0? getById(schema, idStr): null;
                if (updated>0) AuditLogService.log("UPDATE", schema.getName(), idStr, actor, before, after);
                res.json(200, Map.of("updated", updated));
            } catch (SQLException e) {
                LOG.error("Update failed for entity {} id {}", entity, idStr, e);
                res.json(500, errorDetails(e));
            }
        });
        router.delete("/api/{entity}/{id}", (req, res) -> {
            AppConfig cfg = ConfigManager.getConfig();
            String actor = "anonymous"; if (authEnabled(cfg)) { String tok = extractToken(req); actor = (tok!=null&&!tok.isBlank())?tok:"anonymous"; if (!hasAdmin(tok, cfg)) { res.json(401, Map.of("error","unauthorized")); return; } }
            String entity = req.pathParam("entity");
            String idStr = req.pathParam("id");
            EntitySchema schema = SchemaManager.loadSchema(entity);
            if (schema == null) { res.json(404, Map.of("error","unknown entity")); return; }
            try {
                Map<String,Object> before = getById(schema, idStr);
                int deleted = deleteById(schema, idStr);
                if (deleted>0) AuditLogService.log("DELETE", schema.getName(), idStr, actor, before, null);
                res.json(200, Map.of("deleted", deleted));
            } catch (SQLException e) {
                LOG.error("Delete failed for entity {} id {}", entity, idStr, e);
                res.json(500, errorDetails(e));
            }
        });

        // Serve static UI pages for servlet containers
        router.get("/ui/builder", (req, res) -> {
            try (InputStream is = ApiServer.class.getResourceAsStream("/ui/builder.html")) {
                if (is == null) { res.text(404, "Not found", "text/plain; charset=utf-8"); return; }
                String html = new String(is.readAllBytes());
                res.text(200, html, "text/html; charset=utf-8");
            } catch (IOException ioe) {
                LOG.error("Failed to read /ui/builder.html", ioe);
                res.text(500, "Internal Server Error", "text/plain; charset=utf-8");
            }
        });
        router.get("/ui/datasource", (req, res) -> {
            try (InputStream is = ApiServer.class.getResourceAsStream("/ui/datasource.html")) {
                if (is == null) { res.text(404, "Not found", "text/plain; charset=utf-8"); return; }
                String html = new String(is.readAllBytes());
                res.text(200, html, "text/html; charset=utf-8");
            } catch (IOException ioe) {
                LOG.error("Failed to read /ui/datasource.html", ioe);
                res.text(500, "Internal Server Error", "text/plain; charset=utf-8");
            }
        });
        router.get("/ui/swagger", (req, res) -> {
            try (InputStream is = ApiServer.class.getResourceAsStream("/ui/swagger.html")) {
                if (is == null) { res.text(404, "Not found", "text/plain; charset=utf-8"); return; }
                String html = new String(is.readAllBytes());
                res.text(200, html, "text/html; charset=utf-8");
            } catch (IOException ioe) {
                LOG.error("Failed to read /ui/swagger.html", ioe);
                res.text(500, "Internal Server Error", "text/plain; charset=utf-8");
            }
        });
        router.get("/ui/studio", (req, res) -> {
            // Prefer built SPA index; fallback to static studio.html (Phase A demo) if build not present
            try (InputStream primary = ApiServer.class.getResourceAsStream("/ui/dist/index.html")) {
                if (primary != null) {
                    String html = new String(primary.readAllBytes());
                    res.text(200, html, "text/html; charset=utf-8");
                    return;
                }
            } catch (IOException ioe) {
                LOG.warn("Failed reading /ui/dist/index.html, attempting fallback studio.html", ioe);
            }
            try (InputStream fallback = ApiServer.class.getResourceAsStream("/ui/studio.html")) {
                if (fallback != null) {
                    String html = new String(fallback.readAllBytes());
                    res.text(200, html, "text/html; charset=utf-8");
                    return;
                }
                res.text(503, "Studio UI missing. Run ./run-ui.sh build to generate dist or ensure studio.html packaged.", "text/plain; charset=utf-8");
            } catch (IOException ioe) {
                LOG.error("Failed to serve /ui/studio", ioe);
                res.text(500, "Internal Server Error", "text/plain; charset=utf-8");
            }
        });
        router.get("/ui/explorer", (req, res) -> {
            // Alias to studio index (SPA handles internal routing for /explorer path)
            try (InputStream is = ApiServer.class.getResourceAsStream("/ui/dist/index.html")) {
                if (is == null) {
                    res.text(503, "UI build missing. Run ./run-ui.sh build (or npm run build) to generate /ui/dist.", "text/plain; charset=utf-8");
                    return;
                }
                String html = new String(is.readAllBytes());
                res.text(200, html, "text/html; charset=utf-8");
            } catch (IOException ioe) {
                LOG.error("Failed to serve /ui/explorer", ioe);
                res.text(500, "Internal Server Error", "text/plain; charset=utf-8");
            }
        });
        router.get("/ui/{path}", (req, res) -> {
            String path = req.pathParam("path");
            if (path == null || path.isBlank() || path.contains("..")) {
                res.text(400, "Bad Request", "text/plain");
                return;
            }
            String resourcePath = "ui/" + path;
            try (InputStream is = ApiServer.class.getClassLoader().getResourceAsStream(resourcePath)) {
                if (is == null) {
                    res.text(404, "Not Found", "text/plain");
                    return;
                }
                String mimeType = "application/octet-stream";
                if (path.endsWith(".html")) mimeType = "text/html";
                else if (path.endsWith(".css")) mimeType = "text/css";
                else if (path.endsWith(".js")) mimeType = "application/javascript";
                else if (path.endsWith(".json")) mimeType = "application/json";
                else if (path.endsWith(".png")) mimeType = "image/png";
                else if (path.endsWith(".jpg") || path.endsWith(".jpeg")) mimeType = "image/jpeg";
                else if (path.endsWith(".gif")) mimeType = "image/gif";
                else if (path.endsWith(".svg")) mimeType = "image/svg+xml";

                res.bytes(200, is.readAllBytes(), mimeType);
            } catch (IOException e) {
                LOG.error("Failed to serve UI file: {}", resourcePath, e);
                res.text(500, "Internal Server Error", "text/plain");
            }
        });
        return router;
    }

    private static void configureServer(HttpServer server) {
        // Build router with all JSON endpoints
        com.appbana.api.Router router = buildRouter();

        // Root routes via router
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

        // serve the swagger UI (HTML)
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

        // Use virtual threads per server
        server.setExecutor(r -> Thread.ofVirtual().start(r));
    }

    // CRUD helpers extracted from EntityHandler
    private static String quote(String id) {
        if (id == null) return null;
        return '"' + id.toUpperCase() + '"';
    }
    private static Object parseId(String idStr, EntitySchema.Field pk) {
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
    private static List<Map<String, Object>> toList(ResultSet rs) throws SQLException {
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
    private static Object coerceAndValidate(EntitySchema.Field f, Object raw) {
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
    private static java.sql.Connection schemaConnection(com.appbana.model.EntitySchema schema) throws java.sql.SQLException {
        return JdbcManager.getConnection(schema != null ? schema.getDatasourceName() : null);
    }
    public static long insertRecord(EntitySchema schema, Map<String, Object> data) throws SQLException {
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
        try (Connection c = schemaConnection(schema); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
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
    public static List<Map<String, Object>> listAll(EntitySchema schema) throws SQLException {
        String sql = "SELECT * FROM " + quote(schema.getName());
        try (Connection c = schemaConnection(schema); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            return toList(rs);
        }
    }
    public static Map<String, Object> getById(EntitySchema schema, String id) throws SQLException {
        EntitySchema.Field pk = schema.getFields().stream().filter(EntitySchema.Field::isPrimaryKey).findFirst().orElse(null);
        if (pk == null) return null;
        String sql = "SELECT * FROM " + quote(schema.getName()) + " WHERE " + quote(pk.getName()) + " = ?";
        try (Connection c = schemaConnection(schema); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, parseId(id, pk));
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> list = toList(rs);
                return list.isEmpty() ? null : list.getFirst();
            }
        }
    }
    public static int updateById(EntitySchema schema, String id, Map<String, Object> data) throws SQLException {
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
        try (Connection c = schemaConnection(schema); PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            for (Object v : vals) ps.setObject(i++, v);
            ps.setObject(i, parseId(id, pk));
            return ps.executeUpdate();
        }
    }
    public static int deleteById(EntitySchema schema, String id) throws SQLException {
        EntitySchema.Field pk = schema.getFields().stream().filter(EntitySchema.Field::isPrimaryKey).findFirst().orElse(null);
        if (pk == null) return 0;
        String sql = "DELETE FROM " + quote(schema.getName()) + " WHERE " + quote(pk.getName()) + " = ?";
        try (Connection c = schemaConnection(schema); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, parseId(id, pk));
            return ps.executeUpdate();
        }
    }

    private static Map<String,Object> parseFilters(String raw, EntitySchema schema) {
        Map<String,Object> map = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) return map;
        String[] pairs = raw.split(",");
        Map<String,EntitySchema.Field> fieldMap = new HashMap<>();
        for (EntitySchema.Field f: schema.getFields()) fieldMap.put(f.getName().toLowerCase(), f);
        for (String p: pairs) {
            int idx = p.indexOf(":");
            if (idx <= 0) continue;
            String name = p.substring(0, idx).trim();
            String val = p.substring(idx+1).trim();
            if (name.isEmpty()) continue;
            EntitySchema.Field f = fieldMap.get(name.toLowerCase());
            if (f == null) continue; // ignore unknown
            Object parsed = parseFilterValue(f, val);
            map.put(f.getName(), parsed); // use canonical case
        }
        return map;
    }
    private static Object parseFilterValue(EntitySchema.Field f, String v) {
        String t = f.getType().toLowerCase();
        try {
            switch (t) {
                case "int": case "integer": return Integer.parseInt(v);
                case "long": return Long.parseLong(v);
                case "boolean": return ("true".equalsIgnoreCase(v) || "1".equals(v));
                case "date": case "timestamp":
                    // Accept only valid ISO-8601 instant strings; if parsing fails treat as raw literal (DB may coerce or fail at execution time)
                    try { return Timestamp.from(Instant.parse(v)); } catch (Exception ignored) { return v; }
                default: return v; // string/text or unhandled types
            }
        } catch (Exception e) { return v; }
    }

    private static long countOnly(EntitySchema schema, String q, Map<String,Object> filters) throws SQLException {
        StringBuilder where = new StringBuilder();
        List<Object> params = new ArrayList<>();
        buildWhere(schema, q, filters, where, params);
        String sql = "SELECT COUNT(*) FROM " + quote(schema.getName()) + where;
        try (Connection c = schemaConnection(schema); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i=0;i<params.size();i++) ps.setObject(i+1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getLong(1); }
        }
    }

    private static void buildWhere(EntitySchema schema, String q, Map<String,Object> filters, StringBuilder where, List<Object> params) {
        List<String> parts = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            String uq = q.trim().toUpperCase();
            List<String> likeParts = new ArrayList<>();
            for (EntitySchema.Field f : schema.getFields()) {
                String t = f.getType().toLowerCase();
                if (t.equals("string") || t.equals("text") || t.equals("varchar")) {
                    likeParts.add("UPPER(" + quote(f.getName()) + ") LIKE ?");
                    params.add("%" + uq + "%");
                }
            }
            if (!likeParts.isEmpty()) parts.add("(" + String.join(" OR ", likeParts) + ")");
        }
        if (filters != null && !filters.isEmpty()) {
            Map<String,EntitySchema.Field> fieldMap = new HashMap<>();
            for (EntitySchema.Field f : schema.getFields()) fieldMap.put(f.getName().toLowerCase(), f);
            for (Map.Entry<String,Object> e : filters.entrySet()) {
                EntitySchema.Field f = fieldMap.get(e.getKey().toLowerCase());
                if (f == null) continue; // unknown
                String t = f.getType().toLowerCase();
                if ((t.equalsIgnoreCase("date") || t.equalsIgnoreCase("timestamp")) && e.getValue() instanceof String sVal) {
                    // Attempt to parse; if invalid, skip predicate (treat as literal left in filters output)
                    boolean valid = false;
                    try { java.time.Instant.parse(sVal); valid = true; } catch (Exception ignored) { }
                    if (!valid) continue; // skip adding predicate, prevents DB parse error
                }
                parts.add(quote(e.getKey()) + " = ?");
                params.add(e.getValue());
            }
        }
        if (!parts.isEmpty()) where.append(" WHERE ").append(String.join(" AND ", parts));
    }

    private static Map<String,Object> listAdvanced(EntitySchema schema, int limit, int offset, String q, String fieldsParam, String sortParam, Map<String,Object> filters) throws SQLException {
        // Projection (preserve order, remove duplicates while keeping first occurrence)
        List<String> projection = new ArrayList<>();
        Set<String> seenProj = new HashSet<>();
        Map<String,EntitySchema.Field> fieldMap = new HashMap<>();
        for (EntitySchema.Field f: schema.getFields()) fieldMap.put(f.getName().toLowerCase(), f);
        if (fieldsParam != null && !fieldsParam.isBlank()) {
            for (String fn : fieldsParam.split(",")) {
                String trimmed = fn.trim(); if (trimmed.isEmpty()) continue;
                EntitySchema.Field f = fieldMap.get(trimmed.toLowerCase());
                if (f != null) {
                    String canonical = f.getName();
                    if (seenProj.add(canonical.toLowerCase())) projection.add(canonical);
                }
            }
        }
        if (projection.isEmpty()) { // default all, preserve declared order
            for (EntitySchema.Field f: schema.getFields()) {
                String canonical = f.getName();
                if (seenProj.add(canonical.toLowerCase())) projection.add(canonical);
            }
        }
        // Build WHERE
        StringBuilder where = new StringBuilder();
        List<Object> params = new ArrayList<>();
        buildWhere(schema, q, filters, where, params);
        // Count
        String countSql = "SELECT COUNT(*) FROM " + quote(schema.getName()) + where;
        long total;
        // Sorting (preserve order, ignore duplicates after first)
        List<String> orderParts = new ArrayList<>();
        Set<String> seenSort = new HashSet<>();
        if (sortParam != null && !sortParam.isBlank()) {
            for (String token : sortParam.split(",")) {
                String t = token.trim(); if (t.isEmpty()) continue;
                boolean desc = t.startsWith("-");
                String name = desc ? t.substring(1) : (t.startsWith("+") ? t.substring(1) : t);
                EntitySchema.Field f = fieldMap.get(name.toLowerCase());
                if (f == null) continue;
                String key = f.getName().toLowerCase();
                if (seenSort.add(key)) {
                    orderParts.add(quote(f.getName()) + (desc?" DESC":" ASC"));
                }
            }
        }
        String orderClause = orderParts.isEmpty() ? "" : (" ORDER BY " + String.join(", ", orderParts));
        // Projection list with alias to preserve original casing
        List<String> selectCols = new ArrayList<>();
        for (String col : projection) selectCols.add(quote(col) + " AS \""+col+"\"");
        String dataSql = "SELECT " + String.join(",", selectCols) + " FROM " + quote(schema.getName()) + where + orderClause + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
        try (Connection c = schemaConnection(schema)) {
            try (PreparedStatement cps = c.prepareStatement(countSql)) {
                for (int i=0;i<params.size();i++) cps.setObject(i+1, params.get(i));
                try (ResultSet rs = cps.executeQuery()) { rs.next(); total = rs.getLong(1); }
            }
            List<Map<String,Object>> rows;
            try (PreparedStatement dps = c.prepareStatement(dataSql)) {
                int idx=1; for (Object p : params) dps.setObject(idx++, p);
                dps.setInt(idx++, offset);
                dps.setInt(idx, limit);
                try (ResultSet rs = dps.executeQuery()) { rows = toList(rs); }
            }
            Map<String,Object> out = new LinkedHashMap<>();
            out.put("rows", rows);
            out.put("total", total);
            out.put("limit", limit);
            out.put("offset", offset);
            if (q != null && !q.isBlank()) out.put("query", q); // if no string fields existed, q is silently ignored (where part empty)
            if (fieldsParam != null && !fieldsParam.isBlank()) out.put("fields", projection);
            if (sortParam != null && !sortParam.isBlank()) out.put("sort", orderParts);
            if (filters != null && !filters.isEmpty()) out.put("filters", filters);
            return out;
        }
    }

    private static Map<String,Object> insertBatch(EntitySchema schema, List<Map<String,Object>> batch) throws SQLException {
        List<EntitySchema.Field> fields = schema.getFields();
        List<EntitySchema.Field> insertable = new ArrayList<>();
        for (EntitySchema.Field f : fields) {
            if (f.isPrimaryKey() && f.isAutoIncrement()) continue; // skip auto
            insertable.add(f);
        }
        String cols = String.join(",", insertable.stream().map(f->quote(f.getName())).toList());
        String placeholders = String.join(",", Collections.nCopies(insertable.size(), "?"));
        String sql = "INSERT INTO " + quote(schema.getName()) + (insertable.isEmpty()? " DEFAULT VALUES" : (" ("+cols+") VALUES ("+placeholders+")"));
        List<Long> ids = new ArrayList<>();
        try (Connection c = schemaConnection(schema); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            c.setAutoCommit(false);
            for (Map<String,Object> row : batch) {
                int idx=1;
                for (EntitySchema.Field f : insertable) {
                    Object raw = row.get(f.getName());
                    Object val = coerceAndValidate(f, raw);
                    ps.setObject(idx++, val);
                }
                ps.addBatch();
            }
            ps.executeBatch();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                while (rs.next()) ids.add(rs.getLong(1));
            } catch (SQLException ignore) { }
            c.commit();
        }
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("inserted", batch.size());
        if (!ids.isEmpty()) out.put("ids", ids);
        return out;
    }

    // Replace old listPaged with advanced version usage
    private static Map<String,Object> listPaged(EntitySchema schema, int limit, int offset, String q) throws SQLException {
        return listAdvanced(schema, limit, offset, q, null, null, Collections.emptyMap());
    }

}
