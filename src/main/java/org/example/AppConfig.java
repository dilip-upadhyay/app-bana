package org.example;

public class AppConfig {
    private String jdbcUrl = "jdbc:h2:./data/appbana;AUTO_SERVER=TRUE";
    private String username = "sa";
    private String password = "";
    private String driver = "org.h2.Driver"; // optional override

    public String getJdbcUrl() { return jdbcUrl; }
    public void setJdbcUrl(String jdbcUrl) { this.jdbcUrl = jdbcUrl; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getDriver() { return driver; }
    public void setDriver(String driver) { this.driver = driver; }
}

