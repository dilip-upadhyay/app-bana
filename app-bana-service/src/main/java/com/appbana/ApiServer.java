package com.appbana;

import com.appbana.ai.AiProvider;
import com.appbana.config.AppConfig;
import com.appbana.config.ConfigManager;
import com.appbana.service.PermissionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.*;
import com.appbana.model.EntitySchema;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.KeyStore;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

public class ApiServer {
    private static final ObjectMapper M = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    private static final Logger LOG = LoggerFactory.getLogger(ApiServer.class);
    private static PermissionService permissionService;

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

    public static Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isEmpty())
            return map;
        for (String part : query.split("&")) {
            int i = part.indexOf('=');
            if (i > 0)
                map.put(part.substring(0, i), part.substring(i + 1));
            else
                map.put(part, "");
        }
        return map;
    }

    public static Integer parseInteger(String s) {
        if (s == null || s.isBlank())
            return null;
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    public static Long parseLong(String s) {
        if (s == null || s.isBlank())
            return null;
        try {
            return Long.parseLong(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    public static Boolean parseBoolean(String s) {
        if (s == null || s.isBlank())
            return null;
        String v = s.trim().toLowerCase();
        if ("true".equals(v) || "1".equals(v) || "yes".equals(v) || "y".equals(v))
            return true;
        if ("false".equals(v) || "0".equals(v) || "no".equals(v) || "n".equals(v))
            return false;
        return null;
    }

    /**
     * Build JDBC URL from datasource configuration map.
     * Uses Java 21 switch expressions for cleaner, type-safe URL construction.
     * 
     * @param data Configuration map with type, host, port, database name, etc.
     * @return JDBC URL string or null if type is unknown
     */
    public static String buildJdbcUrl(Map<String, String> data) {
        String type = Optional.ofNullable(data.get("type")).orElse("").toLowerCase();
        String params = Optional.ofNullable(data.get("params")).orElse("").trim();

        // Java 21 switch expression - no fall-through, no break, cleaner code
        String baseUrl = switch (type) {
            case "h2" -> {
                String mode = Optional.ofNullable(data.get("h2Mode")).orElse("file");
                if ("mem".equalsIgnoreCase(mode)) {
                    String name = Optional.ofNullable(data.get("h2MemName")).filter(s -> !s.isBlank()).orElse("test");
                    yield "jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1";
                } else {
                    String file = Optional.ofNullable(data.get("h2File")).filter(s -> !s.isBlank())
                            .orElse("./data/appbana");
                    yield "jdbc:h2:" + file + ";AUTO_SERVER=TRUE";
                }
            }
            case "sqlite" -> {
                String file = Optional.ofNullable(data.get("sqliteFile")).filter(s -> !s.isBlank())
                        .orElse("/path/to/file.db");
                yield "jdbc:sqlite:" + file;
            }
            case "postgres" -> {
                String host = Optional.ofNullable(data.get("host")).filter(s -> !s.isBlank()).orElse("localhost");
                String port = Optional.ofNullable(data.get("port")).filter(s -> !s.isBlank()).orElse("5432");
                String db = Optional.ofNullable(data.get("dbname")).filter(s -> !s.isBlank()).orElse("postgres");
                yield "jdbc:postgresql://" + host + ":" + port + "/" + db;
            }
            case "mysql" -> {
                String host = Optional.ofNullable(data.get("host")).filter(s -> !s.isBlank()).orElse("localhost");
                String port = Optional.ofNullable(data.get("port")).filter(s -> !s.isBlank()).orElse("3306");
                String db = Optional.ofNullable(data.get("dbname")).filter(s -> !s.isBlank()).orElse("test");
                yield "jdbc:mysql://" + host + ":" + port + "/" + db;
            }
            case "mariadb" -> {
                String host = Optional.ofNullable(data.get("host")).filter(s -> !s.isBlank()).orElse("localhost");
                String port = Optional.ofNullable(data.get("port")).filter(s -> !s.isBlank()).orElse("3306");
                String db = Optional.ofNullable(data.get("dbname")).filter(s -> !s.isBlank()).orElse("test");
                yield "jdbc:mariadb://" + host + ":" + port + "/" + db;
            }
            case "mssql" -> {
                String host = Optional.ofNullable(data.get("host")).filter(s -> !s.isBlank()).orElse("localhost");
                String port = Optional.ofNullable(data.get("port")).filter(s -> !s.isBlank()).orElse("1433");
                String db = Optional.ofNullable(data.get("dbname")).filter(s -> !s.isBlank()).orElse("master");
                // SQL Server uses semicolon separator
                String url = "jdbc:sqlserver://" + host + ":" + port + ";databaseName=" + db;
                if (!params.isEmpty()) {
                    yield url + ";" + params.replaceAll("[&?]+", ";");
                }
                yield url;
            }
            case "oracle" -> {
                String host = Optional.ofNullable(data.get("host")).filter(s -> !s.isBlank()).orElse("localhost");
                String port = Optional.ofNullable(data.get("port")).filter(s -> !s.isBlank()).orElse("1521");
                String svc = Optional.ofNullable(data.get("dbname")).filter(s -> !s.isBlank()).orElse("orcl");
                yield "jdbc:oracle:thin@" + host + ":" + port + "/" + svc;
            }
            default -> null; // Unknown database type
        };

        // Append additional params (except for SQL Server which was handled above)
        if (baseUrl != null && !params.isEmpty() && !"mssql".equals(type)) {
            String separator = (type.equals("h2")) ? ";" : "?";
            if (baseUrl.contains(separator)) {
                separator = (type.equals("h2")) ? ";" : "&";
            }
            return baseUrl + separator + params.replaceAll("[&?]+", (type.equals("h2")) ? ";" : "&");
        }

        return baseUrl;
    }

    public static String sanitizeUrl(String url) {
        if (url == null)
            return null;
        String u = url;
        // Mask common password keys in query or ;key=value formats
        String[] keys = { "password", "pwd", "pass" };
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
        return cfg.getAdminToken() != null && !cfg.getAdminToken().isBlank()
                || cfg.getReadToken() != null && !cfg.getReadToken().isBlank();
    }

    public static String extractToken(com.appbana.api.Router.HttpRequest req) {
        String tok = req.header("X-AppBana-Token");
        if (tok == null || tok.isBlank()) {
            String auth = req.header("Authorization");
            if (auth != null && auth.toLowerCase(Locale.ROOT).startsWith("bearer "))
                tok = auth.substring(7).trim();
        }
        return tok;
    }

    /**
     * Extract user ID from request for FLS checks
     * 
     * <p>
     * For now, uses token as user ID. In Phase 2, will decode JWT to get actual
     * user ID.
     * </p>
     * 
     * @param req HTTP request
     * @param cfg App configuration
     * @return User ID or null
     */
    public static String extractUserId(com.appbana.api.Router.HttpRequest req, AppConfig cfg) {
        String userId = req.header("X-User-Id");
        if (userId != null && !userId.isBlank()) {
            return userId;
        }
        // Fallback: use token as user ID (temporary until JWT integration)
        String token = extractToken(req);
        if (token != null && !token.isBlank()) {
            // For admin/read tokens, use special user IDs
            if (hasAdmin(token, cfg)) {
                return "admin";
            }
            if (hasRead(token, cfg)) {
                return "reader";
            }
            // Otherwise use token itself as user ID
            return token;
        }
        return null;
    }

    public static boolean hasAdmin(String token, AppConfig cfg) {
        String at = cfg.getAdminToken();
        return at != null && !at.isBlank() && at.equals(token);
    }

    public static boolean hasRead(String token, AppConfig cfg) {
        if (hasAdmin(token, cfg))
            return true;
        String rt = cfg.getReadToken();
        return rt != null && !rt.isBlank() && rt.equals(token);
    }

    public static void startJdk(int port) throws IOException {
        AppConfig cfg = ConfigManager.getConfig();

        // Run Liquibase migrations BEFORE initializing services
        try {
            LOG.info("Running Liquibase database migrations...");
            
            // Use existing HikariCP datasource (already initialized by ConfigManager)
            javax.sql.DataSource dataSource = JdbcManager.getDataSource();
            
            liquibase.database.Database database = liquibase.database.DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new liquibase.database.jvm.JdbcConnection(
                            dataSource.getConnection()));
            
            liquibase.Liquibase liquibase = new liquibase.Liquibase(
                    "db/changelog/db.changelog-master.xml",
                    new liquibase.resource.ClassLoaderResourceAccessor(),
                    database);
            
            // Clean database only if explicitly enabled
            if (Boolean.TRUE.equals(cfg.getFlywayCleanOnStart())) {
                LOG.warn("⚠️  CLEANING DATABASE - ALL DATA WILL BE LOST (flywayCleanOnStart=true)");
                liquibase.dropAll();
            } else {
                LOG.info("✅ Database persistence enabled (flywayCleanOnStart=false)");
            }
            
            // Run migrations
            liquibase.update(new liquibase.Contexts(), new liquibase.LabelExpression());
            
            LOG.info("Liquibase migrations complete");
        } catch (Exception e) {
            LOG.error("Liquibase migration failed: {}", e.getMessage(), e);
            throw new RuntimeException("Database migration failed", e);
        }

        // Initialize PermissionService with datasource
        try {
            javax.sql.DataSource dataSource = new com.zaxxer.hikari.HikariDataSource();
            ((com.zaxxer.hikari.HikariDataSource) dataSource).setJdbcUrl(cfg.getJdbcUrl());
            ((com.zaxxer.hikari.HikariDataSource) dataSource).setUsername(cfg.getUsername());
            ((com.zaxxer.hikari.HikariDataSource) dataSource).setPassword(cfg.getPassword());
            permissionService = new PermissionService(dataSource);
            LOG.info("PermissionService initialized for Field-Level Security");
        } catch (Exception e) {
            LOG.warn("Failed to initialize PermissionService: {}", e.getMessage());
            permissionService = null;
        }

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
                    KeyStore ks = KeyStore.getInstance(
                            ksPath.toLowerCase().endsWith(".p12") || ksPath.toLowerCase().endsWith(".pkcs12") ? "PKCS12"
                                    : "JKS");
                    try (FileInputStream fis = new FileInputStream(ksPath)) {
                        ks.load(fis, ksp);
                    }
                    KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                    kmf.init(ks, kp);
                    SSLContext sslContext = SSLContext.getInstance("TLS");
                    sslContext.init(kmf.getKeyManagers(), null, null);

                    HttpsServer httpsServer = HttpsServer.create(new InetSocketAddress(httpsPort), 0);
                    httpsServer.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
                        @Override
                        public void configure(HttpsParameters params) {
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
                                String host = Optional.ofNullable(ex.getRequestHeaders().getFirst("Host"))
                                        .orElse("localhost");
                                // strip port from Host if present
                                String hostOnly = host;
                                int idx = host.indexOf(":");
                                if (idx >= 0)
                                    hostOnly = host.substring(0, idx);
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
        // Delegate to modular route registry
        return com.appbana.server.RouteRegistry.buildRouter();
    }

    private static void configureServer(HttpServer server) {
        // Build router with all JSON endpoints
        com.appbana.api.Router router = buildRouter();

        // Root routes via router
        server.createContext("/", exchange -> {
            try {
                router.handle(exchange);
            } catch (IOException ioe) {
                LOG.error("Router handle failed", ioe);
            }
        });

        // Use virtual threads per server
        server.setExecutor(r -> Thread.ofVirtual().start(r));
    }

    // CRUD helpers extracted from EntityHandler
    private static String quote(String id) {
        if (id == null)
            return null;
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
                // Convert CLOB to String for JSON serialization
                if (val instanceof java.sql.Clob) {
                    java.sql.Clob clob = (java.sql.Clob) val;
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
            if (f.isRequired())
                throw new IllegalArgumentException("field '" + f.getName() + "' is required");
            return null;
        }
        String t = f.getType().toLowerCase();
        try {
            switch (t) {
                case "int":
                case "integer":
                    if (raw instanceof Number) {
                        long lv = ((Number) raw).longValue();
                        if (f.getMin() != null && lv < f.getMin())
                            throw new IllegalArgumentException("field '" + f.getName() + "' below min");
                        if (f.getMax() != null && lv > f.getMax())
                            throw new IllegalArgumentException("field '" + f.getName() + "' above max");
                        return (int) lv;
                    }
                    int iv = Integer.parseInt(raw.toString());
                    if (f.getMin() != null && iv < f.getMin())
                        throw new IllegalArgumentException("field '" + f.getName() + "' below min");
                    if (f.getMax() != null && iv > f.getMax())
                        throw new IllegalArgumentException("field '" + f.getName() + "' above max");
                    return iv;
                case "long":
                    if (raw instanceof Number) {
                        long lv = ((Number) raw).longValue();
                        if (f.getMin() != null && lv < f.getMin())
                            throw new IllegalArgumentException("field '" + f.getName() + "' below min");
                        if (f.getMax() != null && lv > f.getMax())
                            throw new IllegalArgumentException("field '" + f.getName() + "' above max");
                        return lv;
                    }
                    long lv = Long.parseLong(raw.toString());
                    if (f.getMin() != null && lv < f.getMin())
                        throw new IllegalArgumentException("field '" + f.getName() + "' below min");
                    if (f.getMax() != null && lv > f.getMax())
                        throw new IllegalArgumentException("field '" + f.getName() + "' above max");
                    return lv;
                case "boolean":
                    if (raw instanceof Boolean)
                        return raw;
                    String s = raw.toString().toLowerCase();
                    if ("true".equals(s) || "1".equals(s))
                        return true;
                    if ("false".equals(s) || "0".equals(s))
                        return false;
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
                    if (f.getLength() != null && str.length() > f.getLength())
                        throw new IllegalArgumentException(
                                "field '" + f.getName() + "' length exceeds " + f.getLength());
                    if (f.getPattern() != null && !f.getPattern().isEmpty()) {
                        if (!Pattern.compile(f.getPattern()).matcher(str).matches())
                            throw new IllegalArgumentException("field '" + f.getName() + "' does not match pattern");
                    }
                    return str;
            }
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException("field '" + f.getName() + "' invalid format");
        }
    }

    private static java.sql.Connection schemaConnection(com.appbana.model.EntitySchema schema)
            throws java.sql.SQLException {
        return JdbcManager.getConnection(schema != null ? schema.getDatasourceName() : null);
    }

    public static Object insertRecord(EntitySchema schema, Map<String, Object> data) throws SQLException {
        List<EntitySchema.Field> fields = schema.getFields();
        List<String> cols = new ArrayList<>();
        List<String> placeholders = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        for (EntitySchema.Field f : fields) {
            if (f.isPrimaryKey()) {
                if (f.isAutoIncrement()) {
                    continue; // skip if auto
                }
                // Generate UUID if missing and type is compatible
                if (!data.containsKey(f.getName())) {
                    String t = f.getType().toLowerCase();
                    if (t.equals("string") || t.equals("text") || t.equals("uuid") || t.equals("varchar")) {
                        data.put(f.getName(), java.util.UUID.randomUUID().toString());
                    }
                }
            }
            cols.add(quote(f.getName()));
            placeholders.add("?");
            Object raw = data.get(f.getName());
            Object val = coerceAndValidate(f, raw);
            values.add(val);
        }
        String sql = "INSERT INTO " + quote(schema.getName()) + " (" + String.join(",", cols) + ") VALUES ("
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

    public static List<Map<String, Object>> listAll(EntitySchema schema) throws SQLException {
        String sql = "SELECT * FROM " + quote(schema.getName());
        try (Connection c = schemaConnection(schema);
                PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            return toList(rs);
        }
    }

    public static Map<String, Object> getById(EntitySchema schema, String id) throws SQLException {
        EntitySchema.Field pk = schema.getFields().stream().filter(EntitySchema.Field::isPrimaryKey).findFirst()
                .orElse(null);
        if (pk == null)
            return null;
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
        EntitySchema.Field pk = schema.getFields().stream().filter(EntitySchema.Field::isPrimaryKey).findFirst()
                .orElse(null);
        if (pk == null)
            return 0;
        List<String> set = new ArrayList<>();
        List<Object> vals = new ArrayList<>();
        for (EntitySchema.Field f : schema.getFields()) {
            if (f.isPrimaryKey())
                continue;
            if (data.containsKey(f.getName())) {
                Object raw = data.get(f.getName());
                Object val = coerceAndValidate(f, raw);
                set.add(quote(f.getName()) + " = ?");
                vals.add(val);
            }
        }
        if (set.isEmpty())
            return 0;
        String sql = "UPDATE " + quote(schema.getName()) + " SET " + String.join(",", set) + " WHERE "
                + quote(pk.getName()) + " = ?";
        try (Connection c = schemaConnection(schema); PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            for (Object v : vals)
                ps.setObject(i++, v);
            ps.setObject(i, parseId(id, pk));
            return ps.executeUpdate();
        }
    }

    public static int deleteById(EntitySchema schema, String id) throws SQLException {
        EntitySchema.Field pk = schema.getFields().stream().filter(EntitySchema.Field::isPrimaryKey).findFirst()
                .orElse(null);
        if (pk == null)
            return 0;
        String sql = "DELETE FROM " + quote(schema.getName()) + " WHERE " + quote(pk.getName()) + " = ?";
        try (Connection c = schemaConnection(schema); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, parseId(id, pk));
            return ps.executeUpdate();
        }
    }

    private static Map<String, Object> parseFilters(String raw, EntitySchema schema) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (raw == null || raw.isBlank())
            return map;
        String[] pairs = raw.split(",");
        Map<String, EntitySchema.Field> fieldMap = new HashMap<>();
        for (EntitySchema.Field f : schema.getFields())
            fieldMap.put(f.getName().toLowerCase(), f);
        for (String p : pairs) {
            int idx = p.indexOf(":");
            if (idx <= 0)
                continue;
            String name = p.substring(0, idx).trim();
            String val = p.substring(idx + 1).trim();
            if (name.isEmpty())
                continue;
            EntitySchema.Field f = fieldMap.get(name.toLowerCase());
            if (f == null)
                continue; // ignore unknown
            Object parsed = parseFilterValue(f, val);
            map.put(f.getName(), parsed); // use canonical case
        }
        return map;
    }

    private static Object parseFilterValue(EntitySchema.Field f, String v) {
        String t = f.getType().toLowerCase();
        try {
            switch (t) {
                case "int":
                case "integer":
                    return Integer.parseInt(v);
                case "long":
                    return Long.parseLong(v);
                case "boolean":
                    return ("true".equalsIgnoreCase(v) || "1".equals(v));
                case "date":
                case "timestamp":
                    // Accept only valid ISO-8601 instant strings; if parsing fails treat as raw
                    // literal (DB may coerce or fail at execution time)
                    try {
                        return Timestamp.from(Instant.parse(v));
                    } catch (Exception ignored) {
                        return v;
                    }
                default:
                    return v; // string/text or unhandled types
            }
        } catch (Exception e) {
            return v;
        }
    }

    private static long countOnly(EntitySchema schema, String q, Map<String, Object> filters) throws SQLException {
        StringBuilder where = new StringBuilder();
        List<Object> params = new ArrayList<>();
        buildWhere(schema, q, filters, where, params);
        String sql = "SELECT COUNT(*) FROM " + quote(schema.getName()) + where;
        try (Connection c = schemaConnection(schema); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++)
                ps.setObject(i + 1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private static void buildWhere(EntitySchema schema, String q, Map<String, Object> filters, StringBuilder where,
            List<Object> params) {
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
            if (!likeParts.isEmpty())
                parts.add("(" + String.join(" OR ", likeParts) + ")");
        }
        if (filters != null && !filters.isEmpty()) {
            Map<String, EntitySchema.Field> fieldMap = new HashMap<>();
            for (EntitySchema.Field f : schema.getFields())
                fieldMap.put(f.getName().toLowerCase(), f);
            for (Map.Entry<String, Object> e : filters.entrySet()) {
                EntitySchema.Field f = fieldMap.get(e.getKey().toLowerCase());
                if (f == null)
                    continue; // unknown
                String t = f.getType().toLowerCase();
                if ((t.equalsIgnoreCase("date") || t.equalsIgnoreCase("timestamp"))
                        && e.getValue() instanceof String sVal) {
                    // Attempt to parse; if invalid, skip predicate (treat as literal left in
                    // filters output)
                    boolean valid = false;
                    try {
                        java.time.Instant.parse(sVal);
                        valid = true;
                    } catch (Exception ignored) {
                    }
                    if (!valid)
                        continue; // skip adding predicate, prevents DB parse error
                }
                parts.add(quote(e.getKey()) + " = ?");
                params.add(e.getValue());
            }
        }
        if (!parts.isEmpty())
            where.append(" WHERE ").append(String.join(" AND ", parts));
    }

    private static Map<String, Object> listAdvanced(EntitySchema schema, int limit, int offset, String q,
            String fieldsParam, String sortParam, Map<String, Object> filters) throws SQLException {
        // Projection (preserve order, remove duplicates while keeping first occurrence)
        List<String> projection = new ArrayList<>();
        Set<String> seenProj = new HashSet<>();
        Map<String, EntitySchema.Field> fieldMap = new HashMap<>();
        for (EntitySchema.Field f : schema.getFields())
            fieldMap.put(f.getName().toLowerCase(), f);
        if (fieldsParam != null && !fieldsParam.isBlank()) {
            for (String fn : fieldsParam.split(",")) {
                String trimmed = fn.trim();
                if (trimmed.isEmpty())
                    continue;
                EntitySchema.Field f = fieldMap.get(trimmed.toLowerCase());
                if (f != null) {
                    String canonical = f.getName();
                    if (seenProj.add(canonical.toLowerCase()))
                        projection.add(canonical);
                }
            }
        }
        if (projection.isEmpty()) { // default all, preserve declared order
            for (EntitySchema.Field f : schema.getFields()) {
                String canonical = f.getName();
                if (seenProj.add(canonical.toLowerCase()))
                    projection.add(canonical);
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
                String t = token.trim();
                if (t.isEmpty())
                    continue;
                boolean desc = t.startsWith("-");
                String name = desc ? t.substring(1) : (t.startsWith("+") ? t.substring(1) : t);
                EntitySchema.Field f = fieldMap.get(name.toLowerCase());
                if (f == null)
                    continue;
                String key = f.getName().toLowerCase();
                if (seenSort.add(key)) {
                    orderParts.add(quote(f.getName()) + (desc ? " DESC" : " ASC"));
                }
            }
        }
        String orderClause = orderParts.isEmpty() ? "" : (" ORDER BY " + String.join(", ", orderParts));
        // Projection list with alias to preserve original casing
        List<String> selectCols = new ArrayList<>();
        for (String col : projection)
            selectCols.add(quote(col) + " AS \"" + col + "\"");
        String dataSql = "SELECT " + String.join(",", selectCols) + " FROM " + quote(schema.getName()) + where
                + orderClause + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
        try (Connection c = schemaConnection(schema)) {
            try (PreparedStatement cps = c.prepareStatement(countSql)) {
                for (int i = 0; i < params.size(); i++)
                    cps.setObject(i + 1, params.get(i));
                try (ResultSet rs = cps.executeQuery()) {
                    rs.next();
                    total = rs.getLong(1);
                }
            }
            List<Map<String, Object>> rows;
            try (PreparedStatement dps = c.prepareStatement(dataSql)) {
                int idx = 1;
                for (Object p : params)
                    dps.setObject(idx++, p);
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
            if (q != null && !q.isBlank())
                out.put("query", q); // if no string fields existed, q is silently ignored (where part empty)
            if (fieldsParam != null && !fieldsParam.isBlank())
                out.put("fields", projection);
            if (sortParam != null && !sortParam.isBlank())
                out.put("sort", orderParts);
            if (filters != null && !filters.isEmpty())
                out.put("filters", filters);
            return out;
        }
    }

    private static Map<String, Object> insertBatch(EntitySchema schema, List<Map<String, Object>> batch)
            throws SQLException {
        List<EntitySchema.Field> fields = schema.getFields();
        List<EntitySchema.Field> insertable = new ArrayList<>();
        for (EntitySchema.Field f : fields) {
            if (f.isPrimaryKey() && f.isAutoIncrement())
                continue; // skip auto
            insertable.add(f);
        }
        String cols = String.join(",", insertable.stream().map(f -> quote(f.getName())).toList());
        String placeholders = String.join(",", Collections.nCopies(insertable.size(), "?"));
        String sql = "INSERT INTO " + quote(schema.getName())
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
                while (rs.next())
                    ids.add(rs.getLong(1));
            } catch (SQLException ignore) {
            }
            c.commit();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("inserted", batch.size());
        if (!ids.isEmpty())
            out.put("ids", ids);
        return out;
    }

    // Replace old listPaged with advanced version usage
    private static Map<String, Object> listPaged(EntitySchema schema, int limit, int offset, String q)
            throws SQLException {
        return listAdvanced(schema, limit, offset, q, null, null, Collections.emptyMap());
    }

}
