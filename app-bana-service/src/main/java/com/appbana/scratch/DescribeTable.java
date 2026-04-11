package com.appbana.scratch;

import java.sql.*;
import java.util.*;

public class DescribeTable {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://localhost:5432/appbana";
        String user = "appbana";
        String password = "appbana_dev_2026";

        try (Connection c = DriverManager.getConnection(url, user, password)) {
            DatabaseMetaData md = c.getMetaData();
            
            // Find all tables that look like Customer
            try (ResultSet rs = md.getTables(null, null, "%customer%", null)) {
                boolean found = false;
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    System.out.println("--- Table: " + tableName + " ---");
                    found = true;
                    try (ResultSet cols = md.getColumns(null, null, tableName, null)) {
                        while (cols.next()) {
                            System.out.println("Column: " + cols.getString("COLUMN_NAME") + 
                                               ", Type: " + cols.getString("TYPE_NAME") + 
                                               ", Size: " + cols.getInt("COLUMN_SIZE"));
                        }
                    }
                }
                if (!found) {
                    System.out.println("No matching tables found with %customer%");
                }
            }
        }
    }
}
