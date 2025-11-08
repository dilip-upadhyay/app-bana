package com.appbana;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    private static int parseIntOrDefault(String s, int def) {
        if (s == null || s.isBlank()) return def;
        try { return Integer.parseInt(s.trim()); } catch (Exception ignored) { return def; }
    }

    public static void main(String[] args) {
        try {
            // initialize DB metadata (best-effort)
            try {
                SchemaManager.init();
            } catch (Exception initErr) {
                LOG.warn("DB initialization failed: {}. Server will still start so you can fix datasource via /ui/datasource.", initErr.toString());
            }

            // initialize app metadata directory
            AppManager.initialize();

            // start HTTP API server (port configurable via -Dappbana.port or APPBANA_PORT)
            int port = Integer.getInteger("appbana.port",
                    parseIntOrDefault(System.getenv("APPBANA_PORT"), 8080));

            String serverType = null;
            try { serverType = ConfigManager.getConfig().getServerType(); } catch (Throwable ignored) {}
            if (serverType == null || serverType.isBlank()) serverType = "jdk";
            switch (serverType.toLowerCase()) {
                case "tomcat":
                    TomcatServer.start(port);
                    System.out.println("AppBana (Tomcat) running on port " + port + ". Use /schema to POST schemas and /api/{entity} to access data.");
                    break;
                case "jdk":
                default:
                    ApiServer.startJdk(port);
                    System.out.println("AppBana (JDK HTTP) running on port " + port + ". Use /schema to POST schemas and /api/{entity} to access data.");
                    break;
            }
        } catch (Exception e) {
            LOG.error("Fatal error starting AppBana", e);
            System.exit(1);
        }
    }
}