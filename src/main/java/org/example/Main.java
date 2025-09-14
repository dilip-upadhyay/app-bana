package org.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        try {
            // initialize DB metadata
            SchemaManager.init();
            // start HTTP API server
            ApiServer.start(8080);
            System.out.println("AppBana running. Use /schema to POST schemas and /api/{entity} to access data.");
        } catch (Exception e) {
            LOG.error("Fatal error starting AppBana", e);
            System.exit(1);
        }
    }
}