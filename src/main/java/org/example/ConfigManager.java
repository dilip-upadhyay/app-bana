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
            // return defaults (H2 embedded)
            if (cached == null) cached = normalize(new AppConfig());
            return cached;
        }
        long mt = f.lastModified();
        if (cached != null && mt == cachedMtime) {
            return cached;
        }
        try {
            byte[] b = Files.readAllBytes(p);
            AppConfig cfg = M.readValue(b, AppConfig.class);
            cached = normalize(applyEnvOverrides(cfg));
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
            cached = normalize(applyEnvOverrides(cfg));
            cachedMtime = p.toFile().lastModified();
        } catch (IOException e) {
            throw new RuntimeException("Failed to save config: " + e.getMessage(), e);
        }
    }

    private static AppConfig applyEnvOverrides(AppConfig cfg) {
        String url = firstNonEmpty(System.getenv("APPBANA_JDBC_URL"), System.getProperty("appbana.jdbc.url"));
        String user = firstNonEmpty(System.getenv("APPBANA_DB_USER"), System.getProperty("appbana.db.user"));
        String pass = firstNonEmpty(System.getenv("APPBANA_DB_PASS"), System.getProperty("appbana.db.pass"));
        String drv = firstNonEmpty(System.getenv("APPBANA_DB_DRIVER"), System.getProperty("appbana.db.driver"));
        String adminTok = firstNonEmpty(System.getenv("APPBANA_ADMIN_TOKEN"), System.getProperty("appbana.admin.token"));
        String readTok = firstNonEmpty(System.getenv("APPBANA_READ_TOKEN"), System.getProperty("appbana.read.token"));
        AppConfig out = new AppConfig();
        out.setJdbcUrl(url != null ? url : cfg.getJdbcUrl());
        out.setUsername(user != null ? user : cfg.getUsername());
        out.setPassword(pass != null ? pass : cfg.getPassword());
        out.setDriver(drv != null ? drv : cfg.getDriver());
        out.setName(cfg.getName());
        // preserve multi-datasource fields
        out.setDatasources(cfg.getDatasources() != null ? cfg.getDatasources() : new ArrayList<>());
        out.setActiveDatasource(cfg.getActiveDatasource());
        // tokens
        out.setAdminToken(adminTok != null ? adminTok : cfg.getAdminToken());
        out.setReadToken(readTok != null ? readTok : cfg.getReadToken());
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
}
