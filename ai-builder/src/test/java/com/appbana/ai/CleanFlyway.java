package com.appbana.ai;

import java.sql.*;

public class CleanFlyway {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://localhost:5432/appbana";
        String user = "appbana";
        String password = "appbana_dev_2026";

        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement()) {
            
            System.out.println("✅ Connected to database");
            
            // Drop flyway_schema_history to force a full re-run
            System.out.println("🧹 Dropping flyway_schema_history...");
            stmt.execute("DROP TABLE IF EXISTS flyway_schema_history CASCADE");
            
            // Also drop tables that might have partially failed or exist without history
            System.out.println("🧹 Dropping existing AI tables for a fresh start...");
            stmt.execute("DROP TABLE IF EXISTS ai_feedback CASCADE");
            stmt.execute("DROP TABLE IF EXISTS ai_user_preferences CASCADE");
            stmt.execute("DROP TABLE IF EXISTS ai_app_patterns CASCADE");
            stmt.execute("DROP TABLE IF EXISTS ai_conversations CASCADE");
            
            System.out.println("✨ Database is now CLEAN for Flyway.");
            
        } catch (SQLException e) {
            System.err.println("❌ Database cleanup failed: " + e.getMessage());
            System.exit(1);
        }
    }
}
