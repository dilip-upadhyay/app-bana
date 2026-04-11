package com.appbana.scratch;

import java.sql.*;

public class FixSchema {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://localhost:5432/appbana";
        String user = "appbana";
        String password = "appbana_dev_2026";

        try (Connection c = DriverManager.getConnection(url, user, password)) {
            // Find schemas with "pattern":"null"
            try (PreparedStatement ps = c.prepareStatement("SELECT name, json FROM appbana_schemas WHERE json LIKE '%\"pattern\":\"null\"%'")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String name = rs.getString(1);
                        String json = rs.getString(2);
                        System.out.println("Fixing schema: " + name);
                        
                        // Replace "pattern":"null" with "pattern":null
                        String fixedJson = json.replace("\"pattern\":\"null\"", "\"pattern\":null");
                        
                        try (PreparedStatement ups = c.prepareStatement("UPDATE appbana_schemas SET json = ? WHERE name = ?")) {
                            ups.setString(1, fixedJson);
                            ups.setString(2, name);
                            ups.executeUpdate();
                        }
                    }
                }
            }
        }
        System.out.println("Update complete.");
    }
}
