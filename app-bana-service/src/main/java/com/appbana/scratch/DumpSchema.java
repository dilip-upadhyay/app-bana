package com.appbana.scratch;

import java.sql.*;

public class DumpSchema {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://localhost:5432/appbana";
        String user = "appbana";
        String pass = "appbana_dev_2026";

        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            String sql = "SELECT json FROM appbana_schemas WHERE name LIKE '%Customer%'";
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    System.out.println("Customer Schema JSON:");
                    System.out.println(rs.getString("json"));
                }
            }
        }
    }
}
