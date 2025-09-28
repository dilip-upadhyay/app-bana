package com.appbana;

public class DatasourceConfig {
    private String name;
    private String jdbcUrl;
    private String username;
    private String password;
    private String driver;
    private String type; // e.g., h2, postgres, mysql, mariadb, mssql, oracle, sqlite

    // Optional connection pool settings
    private Integer maxPoolSize;      // default 10
    private Integer minIdle;          // default 2
    private Long connectionTimeoutMs; // default 30000
    private Long idleTimeoutMs;       // default 600000 (10m)
    private Long maxLifetimeMs;       // default 1800000 (30m)
    private Boolean autoCommit;       // default true
    private String poolName;          // optional label

    // Last test result metadata (persisted)
    private Boolean lastTestOk;            // null if never tested
    private Long lastTestAtEpochMs;        // when test was performed (epoch millis)
    private String lastTestMessage;        // success or error message
    private String lastTestDbProduct;      // DB product name on success
    private String lastTestDbVersion;      // DB version on success
    private Long lastTestElapsedMs;        // how long the test took

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getJdbcUrl() { return jdbcUrl; }
    public void setJdbcUrl(String jdbcUrl) { this.jdbcUrl = jdbcUrl; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getDriver() { return driver; }
    public void setDriver(String driver) { this.driver = driver; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Integer getMaxPoolSize() { return maxPoolSize; }
    public void setMaxPoolSize(Integer maxPoolSize) { this.maxPoolSize = maxPoolSize; }

    public Integer getMinIdle() { return minIdle; }
    public void setMinIdle(Integer minIdle) { this.minIdle = minIdle; }

    public Long getConnectionTimeoutMs() { return connectionTimeoutMs; }
    public void setConnectionTimeoutMs(Long connectionTimeoutMs) { this.connectionTimeoutMs = connectionTimeoutMs; }

    public Long getIdleTimeoutMs() { return idleTimeoutMs; }
    public void setIdleTimeoutMs(Long idleTimeoutMs) { this.idleTimeoutMs = idleTimeoutMs; }

    public Long getMaxLifetimeMs() { return maxLifetimeMs; }
    public void setMaxLifetimeMs(Long maxLifetimeMs) { this.maxLifetimeMs = maxLifetimeMs; }

    public Boolean getAutoCommit() { return autoCommit; }
    public void setAutoCommit(Boolean autoCommit) { this.autoCommit = autoCommit; }

    public String getPoolName() { return poolName; }
    public void setPoolName(String poolName) { this.poolName = poolName; }

    public Boolean getLastTestOk() { return lastTestOk; }
    public void setLastTestOk(Boolean lastTestOk) { this.lastTestOk = lastTestOk; }

    public Long getLastTestAtEpochMs() { return lastTestAtEpochMs; }
    public void setLastTestAtEpochMs(Long lastTestAtEpochMs) { this.lastTestAtEpochMs = lastTestAtEpochMs; }

    public String getLastTestMessage() { return lastTestMessage; }
    public void setLastTestMessage(String lastTestMessage) { this.lastTestMessage = lastTestMessage; }

    public String getLastTestDbProduct() { return lastTestDbProduct; }
    public void setLastTestDbProduct(String lastTestDbProduct) { this.lastTestDbProduct = lastTestDbProduct; }

    public String getLastTestDbVersion() { return lastTestDbVersion; }
    public void setLastTestDbVersion(String lastTestDbVersion) { this.lastTestDbVersion = lastTestDbVersion; }

    public Long getLastTestElapsedMs() { return lastTestElapsedMs; }
    public void setLastTestElapsedMs(Long lastTestElapsedMs) { this.lastTestElapsedMs = lastTestElapsedMs; }
}
