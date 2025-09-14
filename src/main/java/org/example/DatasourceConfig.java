package org.example;

public class DatasourceConfig {
    private String name;
    private String jdbcUrl;
    private String username;
    private String password;
    private String driver;

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
}
