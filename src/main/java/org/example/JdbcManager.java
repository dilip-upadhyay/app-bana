package org.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class JdbcManager {
    private static volatile boolean driverLoaded = false;

    static {
        // keep H2 as a default fallback to avoid CNFE during class init
        try { Class.forName("org.h2.Driver"); } catch (ClassNotFoundException ignored) {}
    }

    private static void ensureDriverLoaded() {
        if (driverLoaded) return;
        try {
            String drv = ConfigManager.getConfig().getDriver();
            if (drv != null && !drv.isBlank()) {
                Class.forName(drv);
            }
            driverLoaded = true;
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("JDBC driver class not found: " + e.getMessage(), e);
        }
    }

    public static Connection getConnection() throws SQLException {
        ensureDriverLoaded();
        AppConfig cfg = ConfigManager.getConfig();
        return DriverManager.getConnection(cfg.getJdbcUrl(), cfg.getUsername(), cfg.getPassword());
    }

    public static void ensureMetaTable() {
        String sql = "CREATE TABLE IF NOT EXISTS appbana_schemas (name VARCHAR(200) PRIMARY KEY, json CLOB)";
        String mig = "CREATE TABLE IF NOT EXISTS appbana_migrations (id IDENTITY PRIMARY KEY, schema_name VARCHAR(200), sql CLOB, executed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";
        try (Connection c = getConnection(); Statement s = c.createStatement()) {
            s.execute(sql);
            s.execute(mig);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
