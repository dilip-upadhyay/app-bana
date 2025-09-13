package org.example;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) {
        try {
            // initialize DB metadata
            SchemaManager.init();
            // start HTTP API server
            ApiServer.start(8080);
            System.out.println("AppBana running. Use /schema to POST schemas and /api/{entity} to access data.");
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}