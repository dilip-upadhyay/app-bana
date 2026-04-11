package com.appbana.scratch;

import java.sql.*;

public class DescribeTableCase {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://localhost:5432/appbana";
        String user = "appbana";
        String password = "appbana_dev_2026";

        try (Connection c = DriverManager.getConnection(url, user, password)) {
            DatabaseMetaData md = c.getMetaData();
            // Search for the specific table we saw in ListAllTables
            String targetTable = "APP_T_CFE77E13_B4B31A87_9605_44B3_8C46_89028E4076D5_CUSTOMER";
            System.out.println("Checking Table: " + targetTable);
            try (ResultSet rs = md.getColumns(null, null, targetTable, null)) {
                boolean found = false;
                while (rs.next()) {
                    found = true;
                    System.out.println("Column: " + rs.getString("COLUMN_NAME") + 
                                       " (Type: " + rs.getString("TYPE_NAME") + ")");
                }
                if (!found) System.out.println("Table not found in metadata with exact case.");
            }
        }
    }
}
