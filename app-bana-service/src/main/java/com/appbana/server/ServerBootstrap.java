package com.appbana.server;

import com.appbana.config.AppConfig;
import com.appbana.config.ConfigManager;
import com.appbana.service.PermissionService;
import com.sun.net.httpserver.*;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.KeyStore;
import java.util.Optional;

/**
 * Server initialization and lifecycle management.
 * Handles HTTP/HTTPS server creation, SSL configuration, and database
 * migrations.
 */
public class ServerBootstrap {
    private static final Logger LOG = LoggerFactory.getLogger(ServerBootstrap.class);

    /**
     * Run Flyway database migrations
     */
    public static void runMigrations(AppConfig cfg) {
        try {
            LOG.info("Running Flyway database migrations...");
            Flyway flyway = Flyway.configure()
                    .dataSource(cfg.getJdbcUrl(), cfg.getUsername(), cfg.getPassword())
                    .locations("classpath:db/migration")
                    .cleanDisabled(cfg.getFlywayCleanOnStart() == null || !cfg.getFlywayCleanOnStart())
                    .load();

            // Clean database only if explicitly enabled
            if (Boolean.TRUE.equals(cfg.getFlywayCleanOnStart())) {
                LOG.warn("⚠️  CLEANING DATABASE - ALL DATA WILL BE LOST (flywayCleanOnStart=true)");
                flyway.clean();
            } else {
                LOG.info("✅ Database persistence enabled (flywayCleanOnStart=false)");
            }

            int migrationsApplied = flyway.migrate().migrationsExecuted;
            LOG.info("Flyway migrations complete: {} migrations applied", migrationsApplied);
        } catch (Exception e) {
            LOG.error("Flyway migration failed: {}", e.getMessage(), e);
            throw new RuntimeException("Database migration failed", e);
        }
    }

    /**
     * Initialize PermissionService with datasource
     */
    public static PermissionService initializePermissionService(AppConfig cfg) {
        try {
            javax.sql.DataSource dataSource = new com.zaxxer.hikari.HikariDataSource();
            ((com.zaxxer.hikari.HikariDataSource) dataSource).setJdbcUrl(cfg.getJdbcUrl());
            ((com.zaxxer.hikari.HikariDataSource) dataSource).setUsername(cfg.getUsername());
            ((com.zaxxer.hikari.HikariDataSource) dataSource).setPassword(cfg.getPassword());
            PermissionService permissionService = new PermissionService(dataSource);
            LOG.info("PermissionService initialized for Field-Level Security");
            return permissionService;
        } catch (Exception e) {
            LOG.warn("Failed to initialize PermissionService: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Start HTTP and optionally HTTPS servers
     */
    public static void start(int port, HttpHandler handler) throws IOException {
        AppConfig cfg = ConfigManager.getConfig();

        // Always start the HTTP server (can redirect to HTTPS if configured)
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        httpServer.createContext("/", handler);
        boolean httpsStarted = false;

        // Optionally start HTTPS server
        if (Boolean.TRUE.equals(cfg.getHttpsEnabled())) {
            httpsStarted = startHttpsServer(cfg, handler, httpServer);
        }

        // Use virtual threads for handling requests (Java 21+)
        httpServer.setExecutor(r -> Thread.ofVirtual().start(r));
        httpServer.start();
        LOG.info("HTTP server started on port {}{}", port, httpsStarted ? " (HTTPS also enabled)" : "");
    }

    /**
     * Start HTTPS server with SSL configuration
     */
    private static boolean startHttpsServer(AppConfig cfg, HttpHandler handler, HttpServer httpServer) {
        Integer httpsPort = cfg.getHttpsPort() != null ? cfg.getHttpsPort() : 8443;
        String ksPath = cfg.getKeystorePath();
        String ksPass = cfg.getKeystorePassword();
        String keyPass = cfg.getKeyPassword() != null ? cfg.getKeyPassword() : ksPass;

        if (ksPath == null || ksPath.isBlank() || ksPass == null) {
            LOG.error("HTTPS enabled but keystorePath/keystorePassword not provided; skipping HTTPS startup");
            return false;
        }

        try {
            SSLContext sslContext = createSSLContext(ksPath, ksPass, keyPass);

            HttpsServer httpsServer = HttpsServer.create(new InetSocketAddress(httpsPort), 0);
            httpsServer.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
                @Override
                public void configure(HttpsParameters params) {
                    try {
                        SSLContext c = getSSLContext();
                        SSLEngine engine = c.createSSLEngine();
                        // Build SSLParameters and avoid deprecated HttpsParameters setters
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
            httpsServer.createContext("/", handler);
            httpsServer.setExecutor(r -> Thread.ofVirtual().start(r));
            httpsServer.start();
            LOG.info("HTTPS server started on port {}", httpsPort);

            // If redirect is enabled, replace HTTP handler with redirect
            if (Boolean.TRUE.equals(cfg.getRedirectHttpToHttps())) {
                setupHttpsRedirect(httpServer, httpsPort);
            }

            return true;
        } catch (Exception e) {
            LOG.error("Failed to start HTTPS server: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Create SSL context from keystore
     */
    private static SSLContext createSSLContext(String ksPath, String ksPass, String keyPass) throws Exception {
        char[] kp = keyPass != null ? keyPass.toCharArray() : ksPass.toCharArray();
        char[] ksp = ksPass.toCharArray();

        KeyStore ks = KeyStore.getInstance(
                ksPath.toLowerCase().endsWith(".p12") || ksPath.toLowerCase().endsWith(".pkcs12") ? "PKCS12" : "JKS");
        try (FileInputStream fis = new FileInputStream(ksPath)) {
            ks.load(fis, ksp);
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, kp);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, null);
        return sslContext;
    }

    /**
     * Setup HTTP to HTTPS redirect
     */
    private static void setupHttpsRedirect(HttpServer httpServer, int httpsPort) {
        httpServer.removeContext("/");
        httpServer.createContext("/", ex -> {
            try {
                String host = Optional.ofNullable(ex.getRequestHeaders().getFirst("Host"))
                        .orElse("localhost");
                // Strip port from Host if present
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
}
