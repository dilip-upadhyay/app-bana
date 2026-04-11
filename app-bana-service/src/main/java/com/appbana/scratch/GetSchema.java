package com.appbana.scratch;

import java.sql.*;

public class GetSchema {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://localhost:5432/appbana";
        String user = "appbana";
        String password = "appbana_dev_2026";

        try (Connection c = DriverManager.getConnection(url, user, password)) {
            try (PreparedStatement ps = c.prepareStatement("SELECT name, json FROM appbana_schemas WHERE name LIKE '%Customer%'")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        System.out.println("SCHEMA NAME: " + rs.getString(1));
                        System.out.println("JSON: " + rs.getString(2));
                        System.out.println("---");
                    }
                }
            }
        }
    }
}
