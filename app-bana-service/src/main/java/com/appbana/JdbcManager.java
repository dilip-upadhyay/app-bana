package com.appbana;

import com.appbana.config.AppConfig;
import com.appbana.config.ConfigManager;
import com.appbana.config.DatasourceConfig;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class JdbcManager {
    private static final Set<String> LOADED = ConcurrentHashMap.newKeySet();
    private static final Map<String, HikariDataSource> POOLS = new ConcurrentHashMap<>();
    private static final Map<String, String> POOL_SIGS = new ConcurrentHashMap<>();

    static {
        // keep H2 as a default fallback to avoid CNFE during class init
        try { Class.forName("org.h2.Driver"); LOADED.add("org.h2.Driver"); } catch (ClassNotFoundException ignored) {}
    }

    private static void ensureDriverLoaded(String driver) {
        if (driver == null || driver.isBlank()) return;
        if (LOADED.contains(driver)) return;
        try { Class.forName(driver); LOADED.add(driver); } catch (ClassNotFoundException e) { throw new RuntimeException("JDBC driver class not found: " + driver, e); }
    }

    private static String inferDriver(DatasourceConfig ds) { return ds == null ? null : DriverUtil.inferDriver(ds.getType(), ds.getJdbcUrl(), ds.getDriver()); }

    private static DatasourceConfig findDatasource(String name, AppConfig cfg) {
        if (cfg == null) cfg = ConfigManager.getConfig();
        if (name == null || name.isBlank()) return resolveActive(cfg);
        if (cfg.getDatasources() != null) {
            for (DatasourceConfig ds : cfg.getDatasources()) {
                if (name.equals(ds.getName())) return ds;
            }
        }
        return resolveActive(cfg);
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

    private static String signature(DatasourceConfig ds, String driver) {
        return (nz(ds.getName())+ '|' + nz(ds.getJdbcUrl())+ '|' + nz(ds.getUsername())+ '|' + nz(driver)+ '|' + nz(String.valueOf(ds.getMaxPoolSize()))+ '|' + nz(String.valueOf(ds.getMinIdle()))+ '|' + nz(String.valueOf(ds.getConnectionTimeoutMs()))+ '|' + nz(String.valueOf(ds.getIdleTimeoutMs()))+ '|' + nz(String.valueOf(ds.getMaxLifetimeMs()))+ '|' + nz(String.valueOf(ds.getAutoCommit()))+ '|' + nz(ds.getPoolName()));
    }
    private static String nz(String s){ return s==null?"":s; }

    private static synchronized HikariDataSource ensurePool(DatasourceConfig ds) {
        String driver = inferDriver(ds);
        ensureDriverLoaded(driver);
        String sig = signature(ds, driver);
        String key = ds.getName()!=null? ds.getName():"__default";
        HikariDataSource existing = POOLS.get(key);
        if (existing != null) {
            String prevSig = POOL_SIGS.get(key);
            if (sig.equals(prevSig)) return existing; // up-to-date
            try { existing.close(); } catch (Exception ignored) {}
            POOLS.remove(key); POOL_SIGS.remove(key);
        }
        HikariConfig hc = new HikariConfig();
        if (driver != null && !driver.isBlank()) hc.setDriverClassName(driver);
        hc.setJdbcUrl(ds.getJdbcUrl());
        if (ds.getUsername() != null) hc.setUsername(ds.getUsername());
        if (ds.getPassword() != null) hc.setPassword(ds.getPassword());
        int maxPool = ds.getMaxPoolSize() != null ? ds.getMaxPoolSize() : 10;
        int minIdle = ds.getMinIdle() != null ? ds.getMinIdle() : 2;
        long connTimeout = ds.getConnectionTimeoutMs() != null ? ds.getConnectionTimeoutMs() : 30_000L;
        long idleTimeout = ds.getIdleTimeoutMs() != null ? ds.getIdleTimeoutMs() : 600_000L;
        long maxLifetime = ds.getMaxLifetimeMs() != null ? ds.getMaxLifetimeMs() : 1_800_000L;
        boolean autoCommit = ds.getAutoCommit() != null ? ds.getAutoCommit() : true;
        String poolName = ds.getPoolName() != null ? ds.getPoolName() : ("appbana-" + (ds.getName() != null ? ds.getName() : "default"));
        hc.setMaximumPoolSize(maxPool);
        hc.setMinimumIdle(minIdle);
        hc.setConnectionTimeout(connTimeout);
        hc.setIdleTimeout(idleTimeout);
        hc.setMaxLifetime(maxLifetime);
        hc.setAutoCommit(autoCommit);
        hc.setPoolName(poolName);
        HikariDataSource dsPool = new HikariDataSource(hc);
        POOLS.put(key, dsPool); POOL_SIGS.put(key, sig);
        return dsPool;
    }

    public static Connection getConnection() throws SQLException { return getConnection((String)null); }
    public static Connection getConnection(String datasourceName) throws SQLException {
        DatasourceConfig ds = findDatasource(datasourceName, ConfigManager.getConfig());
        HikariDataSource pool = ensurePool(ds);
        return pool.getConnection();
    }
    
    public static javax.sql.DataSource getDataSource() {
        return getDataSource(null);
    }
    
    public static javax.sql.DataSource getDataSource(String datasourceName) {
        DatasourceConfig ds = findDatasource(datasourceName, ConfigManager.getConfig());
        return ensurePool(ds);
    }

    private static String detectDialect(DatasourceConfig ds) {
        if (ds == null) ds = resolveActive(ConfigManager.getConfig());
        String t = ds.getType();
        if (t != null && !t.isBlank()) return t.toLowerCase();
        String url = ds.getJdbcUrl();
        String inferred = DriverUtil.inferTypeFromUrl(url);
        return inferred != null ? inferred : "h2";
    }

    public static void ensureMetaTable() { ensureMetaTableFor(null); }
    public static void ensureMetaTableFor(String datasourceName) {
        DatasourceConfig ds = findDatasource(datasourceName, ConfigManager.getConfig());
        String dialect = detectDialect(ds);
        String schemasSql; String migSql; String auditSql;
        switch (dialect) {
            case "postgres":
                schemasSql = "CREATE TABLE IF NOT EXISTS appbana_schemas (name VARCHAR(200) PRIMARY KEY, json TEXT)";
                migSql = "CREATE TABLE IF NOT EXISTS appbana_migrations (id BIGSERIAL PRIMARY KEY, schema_name VARCHAR(200), sql TEXT, executed_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP)";
                auditSql = "CREATE TABLE IF NOT EXISTS appbana_audit (id BIGSERIAL PRIMARY KEY, ts TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP, op VARCHAR(20), entity VARCHAR(200), pk VARCHAR(200), actor VARCHAR(200), before_json TEXT, after_json TEXT, changes_json TEXT)";
                break;
            case "mysql":
            case "mariadb":
                schemasSql = "CREATE TABLE IF NOT EXISTS appbana_schemas (name VARCHAR(200) PRIMARY KEY, json TEXT)";
                migSql = "CREATE TABLE IF NOT EXISTS appbana_migrations (id BIGINT AUTO_INCREMENT PRIMARY KEY, schema_name VARCHAR(200), sql TEXT, executed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";
                auditSql = "CREATE TABLE IF NOT EXISTS appbana_audit (id BIGINT AUTO_INCREMENT PRIMARY KEY, ts TIMESTAMP DEFAULT CURRENT_TIMESTAMP, op VARCHAR(20), entity VARCHAR(200), pk VARCHAR(200), actor VARCHAR(200), before_json TEXT, after_json TEXT, changes_json TEXT)";
                break;
            case "sqlite":
                schemasSql = "CREATE TABLE IF NOT EXISTS appbana_schemas (name TEXT PRIMARY KEY, json TEXT)";
                migSql = "CREATE TABLE IF NOT EXISTS appbana_migrations (id INTEGER PRIMARY KEY AUTOINCREMENT, schema_name TEXT, sql TEXT, executed_at DATETIME DEFAULT CURRENT_TIMESTAMP)";
                auditSql = "CREATE TABLE IF NOT EXISTS appbana_audit (id INTEGER PRIMARY KEY AUTOINCREMENT, ts DATETIME DEFAULT CURRENT_TIMESTAMP, op TEXT, entity TEXT, pk TEXT, actor TEXT, before_json TEXT, after_json TEXT, changes_json TEXT)";
                break;
            case "mssql":
                schemasSql = "CREATE TABLE IF NOT EXISTS appbana_schemas (name NVARCHAR(200) PRIMARY KEY, json NVARCHAR(MAX))";
                migSql = "CREATE TABLE IF NOT EXISTS appbana_migrations (id BIGINT IDENTITY(1,1) PRIMARY KEY, schema_name NVARCHAR(200), sql NVARCHAR(MAX), executed_at DATETIME2 DEFAULT SYSDATETIME())";
                auditSql = "CREATE TABLE IF NOT EXISTS appbana_audit (id BIGINT IDENTITY(1,1) PRIMARY KEY, ts DATETIME2 DEFAULT SYSDATETIME(), op NVARCHAR(20), entity NVARCHAR(200), pk NVARCHAR(200), actor NVARCHAR(200), before_json NVARCHAR(MAX), after_json NVARCHAR(MAX), changes_json NVARCHAR(MAX))";
                break;
            case "oracle":
                schemasSql = "BEGIN EXECUTE IMMEDIATE 'CREATE TABLE appbana_schemas (name VARCHAR2(200) PRIMARY KEY, json CLOB)'; EXCEPTION WHEN OTHERS THEN IF SQLCODE != -955 THEN RAISE; END IF; END;";
                migSql = "BEGIN EXECUTE IMMEDIATE 'CREATE TABLE appbana_migrations (id NUMBER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, schema_name VARCHAR2(200), sql CLOB, executed_at TIMESTAMP DEFAULT SYSTIMESTAMP)'; EXCEPTION WHEN OTHERS THEN IF SQLCODE != -955 THEN RAISE; END IF; END;";
                auditSql = "BEGIN EXECUTE IMMEDIATE 'CREATE TABLE appbana_audit (id NUMBER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, ts TIMESTAMP DEFAULT SYSTIMESTAMP, op VARCHAR2(20), entity VARCHAR2(200), pk VARCHAR2(200), actor VARCHAR2(200), before_json CLOB, after_json CLOB, changes_json CLOB)'; EXCEPTION WHEN OTHERS THEN IF SQLCODE != -955 THEN RAISE; END IF; END;";
                break;
            case "h2":
            default:
                schemasSql = "CREATE TABLE IF NOT EXISTS appbana_schemas (name VARCHAR(200) PRIMARY KEY, json CLOB)";
                migSql = "CREATE TABLE IF NOT EXISTS appbana_migrations (id IDENTITY PRIMARY KEY, schema_name VARCHAR(200), sql CLOB, executed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";
                auditSql = "CREATE TABLE IF NOT EXISTS appbana_audit (id IDENTITY PRIMARY KEY, ts TIMESTAMP DEFAULT CURRENT_TIMESTAMP, op VARCHAR(20), entity VARCHAR(200), pk VARCHAR(200), actor VARCHAR(200), before_json CLOB, after_json CLOB, changes_json CLOB)";
                break;
        }
        try (Connection c = getConnection(datasourceName); Statement s = c.createStatement()) {
            s.execute(schemasSql); s.execute(migSql); s.execute(auditSql);
        } catch (SQLException e) { throw new RuntimeException(e); }
    }
}
