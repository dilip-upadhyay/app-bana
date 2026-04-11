package com.appbana.scratch;

import java.sql.*;
import java.util.*;

public class ListAllTables {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:postgresql://localhost:5432/appbana";
        String user = "appbana";
        String password = "appbana_dev_2026";

        try (Connection c = DriverManager.getConnection(url, user, password)) {
            DatabaseMetaData md = c.getMetaData();
            try (ResultSet rs = md.getTables(null, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    System.out.println("Table: " + rs.getString("TABLE_NAME"));
                }
            }
        }
    }
}
