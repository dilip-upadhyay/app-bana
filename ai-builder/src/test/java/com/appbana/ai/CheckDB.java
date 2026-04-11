import java.sql.*;

public class CheckDB {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://localhost:5432/appbana";
        String user = "appbana";
        String password = "appbana_dev_2026";

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("✅ Connected to database");
            
            checkTable(conn, "ai_conversations");
            checkTable(conn, "flyway_schema_history");
            
        } catch (SQLException e) {
            System.err.println("❌ Database connection failed: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void checkTable(Connection conn, String tableName) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getTables(null, null, tableName, new String[]{"TABLE"})) {
            if (rs.next()) {
                System.out.println("✅ Table exists: " + tableName);
            } else {
                System.out.println("❌ Table MISSING: " + tableName);
            }
        }
    }
}
