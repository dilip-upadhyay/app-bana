package com.appbana;

import com.appbana.config.AppConfig;
import com.appbana.config.ConfigManager;
import com.appbana.service.PermissionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.*;
import com.appbana.model.EntitySchema;
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
            new PermissionService(dataSource);
            LOG.info("PermissionService initialized for Field-Level Security");
        } catch (Exception e) {
            LOG.warn("Failed to initialize PermissionService: {}", e.getMessage());
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



}
