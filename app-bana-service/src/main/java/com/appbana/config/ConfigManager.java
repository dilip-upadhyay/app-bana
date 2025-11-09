package com.appbana.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * Configuration manager
 * Loads and saves app configuration from config.json
 */
public class ConfigManager {
    private static final Logger LOG = LoggerFactory.getLogger(ConfigManager.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String CONFIG_FILE = "config.json";
    
    private static AppConfig cachedConfig = null;
    
    /**
     * Get configuration (loads from file if not cached)
     */
    public static AppConfig getConfig() {
        if (cachedConfig == null) {
            cachedConfig = loadConfig();
        }
        return cachedConfig;
    }
    
    /**
     * Save configuration to file
     */
    public static void saveConfig(AppConfig config) throws IOException {
        File file = new File(CONFIG_FILE);
        mapper.writerWithDefaultPrettyPrinter().writeValue(file, config);
        cachedConfig = config;
        LOG.info("Configuration saved to {}", CONFIG_FILE);
    }
    
    /**
     * Reload configuration from file
     */
    public static AppConfig reloadConfig() {
        cachedConfig = null;
        return getConfig();
    }
    
    /**
     * Load configuration from file (creates defaults if missing)
     */
    private static AppConfig loadConfig() {
        File file = new File(CONFIG_FILE);
        
        if (!file.exists()) {
            LOG.info("Config file {} not found, using defaults", CONFIG_FILE);
            return createDefaultConfig();
        }
        
        try {
            AppConfig config = mapper.readValue(file, AppConfig.class);
            LOG.info("Configuration loaded from {}", CONFIG_FILE);
            return config;
        } catch (IOException e) {
            LOG.error("Failed to load config from {}, using defaults", CONFIG_FILE, e);
            return createDefaultConfig();
        }
    }
    
    /**
     * Create default configuration
     */
    private static AppConfig createDefaultConfig() {
        AppConfig config = new AppConfig();
        // Defaults are already set in AppConfig field initializers
        return config;
    }
    
    private ConfigManager() {
        // Utility class, no instantiation
    }
}
