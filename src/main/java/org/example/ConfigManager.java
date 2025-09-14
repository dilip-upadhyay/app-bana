package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
            if (cached == null) cached = new AppConfig();
            return cached;
        }
        long mt = f.lastModified();
        if (cached != null && mt == cachedMtime) {
            return cached;
        }
        try {
            byte[] b = Files.readAllBytes(p);
            AppConfig cfg = M.readValue(b, AppConfig.class);
            cached = applyEnvOverrides(cfg);
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
            cached = applyEnvOverrides(cfg);
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
        AppConfig out = new AppConfig();
        out.setJdbcUrl(url != null ? url : cfg.getJdbcUrl());
        out.setUsername(user != null ? user : cfg.getUsername());
        out.setPassword(pass != null ? pass : cfg.getPassword());
        out.setDriver(drv != null ? drv : cfg.getDriver());
        return out;
    }

    private static String firstNonEmpty(String... s) {
        if (s == null) return null;
        for (String x : s) {
            if (x != null && !x.isBlank()) return x;
        }
        return null;
    }
}

