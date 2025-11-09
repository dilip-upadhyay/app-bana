package com.appbana.config;

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

    // Optional token-based auth (if both null/blank, auth is disabled)
    private String adminToken; // full read-write access
    private String readToken;  // read-only access

    // Optional HTTPS support
    private Boolean httpsEnabled; // if true, start an HTTPS server as configured
    private Integer httpsPort;    // default 8443 if enabled and not set
    private String keystorePath;  // path to JKS/PKCS12 keystore
    private String keystorePassword; // keystore password
    private String keyPassword;       // key password (defaults to keystorePassword if null)
    private Boolean redirectHttpToHttps; // if true, HTTP server redirects all requests to HTTPS

    // Web server implementation: "jdk" (default) or "tomcat"
    private String serverType; // null => default to "jdk"

    // AI Provider Configuration
    private String aiProvider;         // "openai", "anthropic", or "ollama"
    
    // OpenAI settings
    private String openaiApiKey;       // API key (or use OPENAI_API_KEY env var)
    private String openaiModel = "gpt-4o-mini"; // default model
    
    // Anthropic settings
    private String anthropicApiKey;    // API key (or use ANTHROPIC_API_KEY env var)
    private String anthropicModel = "claude-3-5-sonnet-20241022"; // default model
    
    // Ollama settings (local AI)
    private String ollamaUrl = "http://localhost:11434"; // Ollama server URL
    private String ollamaModel = "llama3.1"; // default model

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

    public String getAdminToken() { return adminToken; }
    public void setAdminToken(String adminToken) { this.adminToken = adminToken; }

    public String getReadToken() { return readToken; }
    public void setReadToken(String readToken) { this.readToken = readToken; }

    public Boolean getHttpsEnabled() { return httpsEnabled; }
    public void setHttpsEnabled(Boolean httpsEnabled) { this.httpsEnabled = httpsEnabled; }

    public Integer getHttpsPort() { return httpsPort; }
    public void setHttpsPort(Integer httpsPort) { this.httpsPort = httpsPort; }

    public String getKeystorePath() { return keystorePath; }
    public void setKeystorePath(String keystorePath) { this.keystorePath = keystorePath; }

    public String getKeystorePassword() { return keystorePassword; }
    public void setKeystorePassword(String keystorePassword) { this.keystorePassword = keystorePassword; }

    public String getKeyPassword() { return keyPassword; }
    public void setKeyPassword(String keyPassword) { this.keyPassword = keyPassword; }

    public Boolean getRedirectHttpToHttps() { return redirectHttpToHttps; }
    public void setRedirectHttpToHttps(Boolean redirectHttpToHttps) { this.redirectHttpToHttps = redirectHttpToHttps; }

    public String getServerType() { return serverType; }
    public void setServerType(String serverType) { this.serverType = serverType; }

    // AI Configuration Getters/Setters
    public String getAiProvider() { return aiProvider; }
    public void setAiProvider(String aiProvider) { this.aiProvider = aiProvider; }

    public String getOpenaiApiKey() {
        // Try config first, then environment variable
        return openaiApiKey != null ? openaiApiKey : System.getenv("OPENAI_API_KEY");
    }
    public void setOpenaiApiKey(String openaiApiKey) { this.openaiApiKey = openaiApiKey; }

    public String getOpenaiModel() { return openaiModel; }
    public void setOpenaiModel(String openaiModel) { this.openaiModel = openaiModel; }

    public String getAnthropicApiKey() {
        // Try config first, then environment variable
        return anthropicApiKey != null ? anthropicApiKey : System.getenv("ANTHROPIC_API_KEY");
    }
    public void setAnthropicApiKey(String anthropicApiKey) { this.anthropicApiKey = anthropicApiKey; }

    public String getAnthropicModel() { return anthropicModel; }
    public void setAnthropicModel(String anthropicModel) { this.anthropicModel = anthropicModel; }

    public String getOllamaUrl() { return ollamaUrl; }
    public void setOllamaUrl(String ollamaUrl) { this.ollamaUrl = ollamaUrl; }

    public String getOllamaModel() { return ollamaModel; }
    public void setOllamaModel(String ollamaModel) { this.ollamaModel = ollamaModel; }
}
