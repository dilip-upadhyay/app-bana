package org.example;

import java.util.ArrayList;
import java.util.List;

public class AppConfig {
    private String jdbcUrl = "jdbc:h2:./data/appbana;AUTO_SERVER=TRUE";
    private String username = "sa";
    private String password = "";
    private String driver = "org.h2.Driver"; // optional override
    private String name = "default"; // datasource name

    // multi-datasource support
    private List<DatasourceConfig> datasources = new ArrayList<>();
    private String activeDatasource; // name

    public String getJdbcUrl() { return jdbcUrl; }
    public void setJdbcUrl(String jdbcUrl) { this.jdbcUrl = jdbcUrl; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getDriver() { return driver; }
    public void setDriver(String driver) { this.driver = driver; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<DatasourceConfig> getDatasources() { return datasources; }
    public void setDatasources(List<DatasourceConfig> datasources) { this.datasources = datasources; }

    public String getActiveDatasource() { return activeDatasource; }
    public void setActiveDatasource(String activeDatasource) { this.activeDatasource = activeDatasource; }
}
