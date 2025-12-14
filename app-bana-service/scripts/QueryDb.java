import java.sql.*;
import java.util.*;
import org.h2.Driver;

public class QueryDb {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: java QueryDb <sql_query>");
            System.exit(1);
        }

        String sql = args[0];
        String url = "jdbc:h2:./data/appbana;AUTO_SERVER=TRUE";
        String user = "sa";
        String password = "";

        try {
            Class.forName("org.h2.Driver");
            try (Connection conn = DriverManager.getConnection(url, user, password);
                    Statement stmt = conn.createStatement()) {

                boolean isResultSet = stmt.execute(sql);

                if (isResultSet) {
                    try (ResultSet rs = stmt.getResultSet()) {
                        ResultSetMetaData meta = rs.getMetaData();
                        int colCount = meta.getColumnCount();

                        System.out.println("[");
                        boolean first = true;
                        while (rs.next()) {
                            if (!first)
                                System.out.println(",");
                            first = false;
                            System.out.print("  {");
                            for (int i = 1; i <= colCount; i++) {
                                String name = meta.getColumnLabel(i);
                                String val = rs.getString(i);
                                if (val == null)
                                    val = "null";
                                else
                                    val = "\"" + val.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")
                                            + "\"";

                                if (i > 1)
                                    System.out.print(", ");
                                System.out.print("\"" + name + "\": " + val);
                            }
                            System.out.print("}");
                        }
                        System.out.println("\n]");
                    }
                } else {
                    System.out.println("Update Count: " + stmt.getUpdateCount());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
