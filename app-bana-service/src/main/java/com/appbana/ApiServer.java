package com.appbana;

import com.appbana.config.AppConfig;
import com.appbana.config.ConfigManager;
import com.appbana.service.PermissionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.KeyStore;

/**
 * Core HTTP server bootstrap for AppBana.
 * 
 * This class contains only the essential server initialization logic.
 * All CRUD operations have been moved to EntityCrudService.
 * All utility methods have been removed as they were unused.
 */
public class ApiServer {
    private static final ObjectMapper M = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    private static final Logger LOG = LoggerFactory.getLogger(ApiServer.class);

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
                                String host = java.util.Optional.ofNullable(ex.getRequestHeaders().getFirst("Host"))
                                        .orElse("localhost");
                                // strip port from Host if present
                                String hostOnly = host;
                                int idx = host.indexOf(":");
                                if (idx >= 0)
                                    hostOnly = host.substring(0, idx);
                                java.net.URI uri = ex.getRequestURI();
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
