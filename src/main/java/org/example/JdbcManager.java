package org.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class JdbcManager {
    private static final String JDBC_URL = "jdbc:h2:./data/appbana;AUTO_SERVER=TRUE";
    private static final String USER = "sa";
    private static final String PASS = "";

    static {
        try {
            // Ensure driver is loaded
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(JDBC_URL, USER, PASS);
    }

    public static void ensureMetaTable() {
        String sql = "CREATE TABLE IF NOT EXISTS appbana_schemas (name VARCHAR(200) PRIMARY KEY, json CLOB)";
        String mig = "CREATE TABLE IF NOT EXISTS appbana_migrations (id IDENTITY PRIMARY KEY, schema_name VARCHAR(200), sql CLOB, executed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";
        try (Connection c = getConnection(); Statement s = c.createStatement()) {
            s.execute(sql);
            s.execute(mig);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
