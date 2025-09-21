package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ConfigManager {
    private static final ObjectMapper M = new ObjectMapper();
    private static final String DEFAULT_PATH = "data/appbana-config.json";
    private static volatile AppConfig cached;
    private static volatile long cachedMtime = -1L;

    public static synchronized AppConfig getConfig() {
        Path p = Path.of(Optional.ofNullable(System.getenv("APPBANA_CONFIG"))
                .orElseGet(() -> System.getProperty("appbana.config", DEFAULT_PATH)));
        File f = p.toFile();
        if (!f.exists()) {
            if (cached == null) cached = applyEnvOverrides(normalize(new AppConfig()));
            return cached;
        }
        long mt = f.lastModified();
        if (cached != null && mt == cachedMtime) {
            return cached;
        }
        try {
            byte[] b = Files.readAllBytes(p);
            AppConfig cfg = M.readValue(b, AppConfig.class);
            cached = applyEnvOverrides(normalize(cfg));
            cachedMtime = mt;
            return cached;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read config: " + p + ", " + e.getMessage(), e);
        }
    }

    public static synchronized void saveConfig(AppConfig cfg) {
        try {
            Path p = Path.of(Optional.ofNullable(System.getenv("APPBANA_CONFIG"))
                    .orElseGet(() -> System.getProperty("appbana.config", DEFAULT_PATH)));
            Files.createDirectories(p.getParent());
            byte[] b = M.writerWithDefaultPrettyPrinter().writeValueAsBytes(cfg);
            Files.write(p, b);
            // Cache normalized + env-overridden view
            cached = applyEnvOverrides(normalize(cfg));
            cachedMtime = p.toFile().lastModified();
        } catch (IOException e) {
            throw new RuntimeException("Failed to save config: " + e.getMessage(), e);
        }
    }

    private static AppConfig applyEnvOverrides(AppConfig in) {
        AppConfig cfg = in == null ? new AppConfig() : in;
        String url = firstNonEmpty(System.getenv("APPBANA_JDBC_URL"), System.getProperty("appbana.jdbc.url"));
        String user = firstNonEmpty(System.getenv("APPBANA_DB_USER"), System.getProperty("appbana.db.user"));
        String pass = firstNonEmpty(System.getenv("APPBANA_DB_PASS"), System.getProperty("appbana.db.pass"));
        String drv = firstNonEmpty(System.getenv("APPBANA_DB_DRIVER"), System.getProperty("appbana.db.driver"));
        String adminTok = firstNonEmpty(System.getenv("APPBANA_ADMIN_TOKEN"), System.getProperty("appbana.admin.token"));
        String readTok = firstNonEmpty(System.getenv("APPBANA_READ_TOKEN"), System.getProperty("appbana.read.token"));
        // HTTPS-related
        String httpsEnabled = firstNonEmpty(System.getenv("APPBANA_HTTPS_ENABLED"), System.getProperty("appbana.https.enabled"));
        String httpsPort = firstNonEmpty(System.getenv("APPBANA_HTTPS_PORT"), System.getProperty("appbana.https.port"));
        String ksPath = firstNonEmpty(System.getenv("APPBANA_KEYSTORE_PATH"), System.getProperty("appbana.keystore.path"));
        String ksPass = firstNonEmpty(System.getenv("APPBANA_KEYSTORE_PASSWORD"), System.getProperty("appbana.keystore.password"));
        String keyPass = firstNonEmpty(System.getenv("APPBANA_KEY_PASSWORD"), System.getProperty("appbana.key.password"));
        String redirect = firstNonEmpty(System.getenv("APPBANA_REDIRECT_HTTP_TO_HTTPS"), System.getProperty("appbana.redirect.http.to.https"));

        AppConfig out = new AppConfig();
        // Copy existing (normalized) config first
        out.setName(cfg.getName());
        out.setJdbcUrl(cfg.getJdbcUrl());
        out.setUsername(cfg.getUsername());
        out.setPassword(cfg.getPassword());
        out.setDriver(cfg.getDriver());
        out.setDatasources(cfg.getDatasources() != null ? new ArrayList<>(cfg.getDatasources()) : new ArrayList<>());
        out.setActiveDatasource(cfg.getActiveDatasource());
        out.setAdminToken(cfg.getAdminToken());
        out.setReadToken(cfg.getReadToken());
        out.setHttpsEnabled(cfg.getHttpsEnabled());
        out.setHttpsPort(cfg.getHttpsPort());
        out.setKeystorePath(cfg.getKeystorePath());
        out.setKeystorePassword(cfg.getKeystorePassword());
        out.setKeyPassword(cfg.getKeyPassword());
        out.setRedirectHttpToHttps(cfg.getRedirectHttpToHttps());

        // Apply root field overrides
        if (url != null) out.setJdbcUrl(url);
        if (user != null) out.setUsername(user);
        if (pass != null) out.setPassword(pass);
        if (drv != null) out.setDriver(drv);
        // Tokens
        if (adminTok != null) out.setAdminToken(adminTok);
        if (readTok != null) out.setReadToken(readTok);
        // HTTPS
        if (httpsEnabled != null) out.setHttpsEnabled(parseBool(httpsEnabled));
        if (httpsPort != null) out.setHttpsPort(parseInt(httpsPort));
        if (ksPath != null) out.setKeystorePath(ksPath);
        if (ksPass != null) out.setKeystorePassword(ksPass);
        if (keyPass != null) out.setKeyPassword(keyPass);
        if (redirect != null) out.setRedirectHttpToHttps(parseBool(redirect));

        // If JDBC overrides present, also override the active datasource entry (in-memory only)
        if (url != null || user != null || pass != null || drv != null) {
            String active = out.getActiveDatasource();
            List<DatasourceConfig> list = out.getDatasources();
            if (list != null && !list.isEmpty()) {
                for (int i = 0; i < list.size(); i++) {
                    DatasourceConfig ds = list.get(i);
                    if (ds.getName() != null && ds.getName().equals(active)) {
                        DatasourceConfig copy = new DatasourceConfig();
                        copy.setName(ds.getName());
                        copy.setJdbcUrl(url != null ? url : ds.getJdbcUrl());
                        copy.setUsername(user != null ? user : ds.getUsername());
                        copy.setPassword(pass != null ? pass : ds.getPassword());
                        copy.setDriver(drv != null ? drv : ds.getDriver());
                        copy.setType(ds.getType());
                        copy.setMaxPoolSize(ds.getMaxPoolSize());
                        copy.setMinIdle(ds.getMinIdle());
                        copy.setConnectionTimeoutMs(ds.getConnectionTimeoutMs());
                        copy.setIdleTimeoutMs(ds.getIdleTimeoutMs());
                        copy.setMaxLifetimeMs(ds.getMaxLifetimeMs());
                        copy.setAutoCommit(ds.getAutoCommit());
                        copy.setPoolName(ds.getPoolName());
                        copy.setLastTestOk(ds.getLastTestOk());
                        copy.setLastTestAtEpochMs(ds.getLastTestAtEpochMs());
                        copy.setLastTestMessage(ds.getLastTestMessage());
                        copy.setLastTestDbProduct(ds.getLastTestDbProduct());
                        copy.setLastTestDbVersion(ds.getLastTestDbVersion());
                        copy.setLastTestElapsedMs(ds.getLastTestElapsedMs());
                        list.set(i, copy);
                        break;
                    }
                }
            }
        }
        return out;
    }

    private static AppConfig normalize(AppConfig cfg) {
        // If no datasources list, seed from root fields for backward-compat
        List<DatasourceConfig> list = cfg.getDatasources();
        if (list == null) {
            list = new ArrayList<>();
            cfg.setDatasources(list);
        }
        if (list.isEmpty()) {
            DatasourceConfig ds = new DatasourceConfig();
            ds.setName(cfg.getName() != null ? cfg.getName() : "default");
            ds.setJdbcUrl(cfg.getJdbcUrl());
            ds.setUsername(cfg.getUsername());
            ds.setPassword(cfg.getPassword());
            ds.setDriver(cfg.getDriver());
            String type = DriverUtil.inferTypeFromUrl(cfg.getJdbcUrl());
            if (type == null) type = "h2";
            ds.setType(type);
            list.add(ds);
        } else {
            // ensure each has a type
            for (DatasourceConfig ds : list) {
                if (ds.getType() == null || ds.getType().isBlank()) {
                    String type = DriverUtil.inferTypeFromUrl(ds.getJdbcUrl());
                    if (type == null) type = "h2";
                    ds.setType(type);
                }
            }
        }
        if (cfg.getActiveDatasource() == null || cfg.getActiveDatasource().isBlank()) {
            String nm = cfg.getName() != null ? cfg.getName() : list.get(0).getName();
            cfg.setActiveDatasource(nm);
        }
        return cfg;
    }

    private static String firstNonEmpty(String... s) {
        if (s == null) return null;
        for (String x : s) {
            if (x != null && !x.isBlank()) return x;
        }
        return null;
    }

    private static Boolean parseBool(String s) {
        if (s == null) return null;
        String v = s.trim().toLowerCase();
        if ("true".equals(v) || "1".equals(v) || "yes".equals(v) || "y".equals(v)) return true;
        if ("false".equals(v) || "0".equals(v) || "no".equals(v) || "n".equals(v)) return false;
        return null;
    }

    private static Integer parseInt(String s) {
        if (s == null) return null;
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return null; }
    }
}
