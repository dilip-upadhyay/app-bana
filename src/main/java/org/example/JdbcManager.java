package org.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class JdbcManager {
    private static final Set<String> LOADED = ConcurrentHashMap.newKeySet();

    static {
        // keep H2 as a default fallback to avoid CNFE during class init
        try { Class.forName("org.h2.Driver"); LOADED.add("org.h2.Driver"); } catch (ClassNotFoundException ignored) {}
    }

    private static void ensureDriverLoaded(String driver) {
        if (driver == null || driver.isBlank()) return;
        if (LOADED.contains(driver)) return;
        try {
            Class.forName(driver);
            LOADED.add(driver);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("JDBC driver class not found: " + driver, e);
        }
    }

    private static String inferDriver(DatasourceConfig ds) {
        if (ds == null) return null;
        String t = ds.getType();
        if (t != null) {
            String lt = t.toLowerCase();
            switch (lt) {
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
        String url = ds.getJdbcUrl();
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

    private static DatasourceConfig resolveActive(AppConfig cfg) {
        if (cfg == null) cfg = ConfigManager.getConfig();
        String active = cfg.getActiveDatasource();
        if (cfg.getDatasources() != null && !cfg.getDatasources().isEmpty()) {
            for (DatasourceConfig ds : cfg.getDatasources()) {
                if (ds.getName() != null && ds.getName().equals(active)) return ds;
            }
            return cfg.getDatasources().get(0);
        }
        DatasourceConfig ds = new DatasourceConfig();
        ds.setName(cfg.getName());
        ds.setJdbcUrl(cfg.getJdbcUrl());
        ds.setUsername(cfg.getUsername());
        ds.setPassword(cfg.getPassword());
        ds.setDriver(cfg.getDriver());
        return ds;
    }

    public static Connection getConnection() throws SQLException {
        AppConfig cfg = ConfigManager.getConfig();
        DatasourceConfig ds = resolveActive(cfg);
        String driver = (ds.getDriver() == null || ds.getDriver().isBlank()) ? inferDriver(ds) : ds.getDriver();
        ensureDriverLoaded(driver);
        return DriverManager.getConnection(ds.getJdbcUrl(), ds.getUsername(), ds.getPassword());
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
