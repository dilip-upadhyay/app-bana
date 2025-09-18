package org.example;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SchemaManagerH2Test {

    @AfterEach
    void clearOverrides() {
        System.clearProperty("appbana.jdbc.url");
        System.clearProperty("appbana.db.user");
        System.clearProperty("appbana.db.pass");
        System.clearProperty("appbana.db.driver");
    }

    @Test
    void ensureMetaTablesOnH2MemAndListSchemas() {
        System.setProperty("appbana.jdbc.url", "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
        System.setProperty("appbana.db.user", "sa");
        // initialize meta tables (no exception expected)
        SchemaManager.init();
        // listing schemas should not throw and can be empty initially
        assertNotNull(SchemaManager.listSchemaNames());
    }
}

