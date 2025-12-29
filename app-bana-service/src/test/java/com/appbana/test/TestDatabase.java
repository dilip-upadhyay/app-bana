package com.appbana.test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Test database helper for integration tests.
 * Provides H2 in-memory database for isolated testing.
 * 
 * @see ENTITY_FORM_BINDING_TEST_PLAN.md for complete test plan
 */
public class TestDatabase {
    
    private static final String DB_URL = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL";
    private static final String DB_USER = "sa";
    private static final String DB_PASSWORD = "";
    
    /**
     * Gets a connection to the test database.
     * Creates tables if they don't exist.
     */
    public static Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
        initializeTables(conn);
        return conn;
    }
    
    /**
     * Initializes database tables for testing.
     * Creates user table with passwordHash field (NOT password field).
     */
    private static void initializeTables(Connection conn) throws SQLException {
        Statement stmt = conn.createStatement();
        
        // Create user table
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS user (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                firstName VARCHAR(100) NOT NULL,
                lastName VARCHAR(100) NOT NULL,
                email VARCHAR(255) NOT NULL UNIQUE,
                phone VARCHAR(20),
                passwordHash VARCHAR(255) NOT NULL,
                createdAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """);
        
        // Create user_preferences table (for transaction tests)
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS user_preferences (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                user_id BIGINT NOT NULL,
                theme VARCHAR(50),
                createdAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (user_id) REFERENCES user(id)
            )
        """);
        
        stmt.close();
    }
    
    /**
     * Cleans all data from test database.
     * Call this in @BeforeEach to ensure test isolation.
     */
    public static void cleanDatabase(Connection conn) throws SQLException {
        Statement stmt = conn.createStatement();
        stmt.execute("DELETE FROM user_preferences");
        stmt.execute("DELETE FROM user");
        stmt.close();
    }
    
    /**
     * Drops all tables from test database.
     * Call this in @AfterAll for cleanup.
     */
    public static void dropTables(Connection conn) throws SQLException {
        Statement stmt = conn.createStatement();
        stmt.execute("DROP TABLE IF EXISTS user_preferences");
        stmt.execute("DROP TABLE IF EXISTS user");
        stmt.close();
    }
}
