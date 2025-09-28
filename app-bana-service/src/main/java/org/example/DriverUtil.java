package org.example;

public final class DriverUtil {
    private DriverUtil() {}

    public static String inferTypeFromUrl(String url) {
        if (url == null) return null;
        if (url.startsWith("jdbc:h2:")) return "h2";
        if (url.startsWith("jdbc:postgresql:")) return "postgres";
        if (url.startsWith("jdbc:mysql:")) return "mysql";
        if (url.startsWith("jdbc:mariadb:")) return "mariadb";
        if (url.startsWith("jdbc:sqlserver:")) return "mssql";
        if (url.startsWith("jdbc:oracle:")) return "oracle";
        if (url.startsWith("jdbc:sqlite:")) return "sqlite";
        return null;
    }

    public static String inferDriver(String type, String url, String provided) {
        if (provided != null && !provided.isBlank()) return provided;
        String t = type;
        if ((t == null || t.isBlank()) && url != null) t = inferTypeFromUrl(url);
        if (t == null) return null;
        switch (t.toLowerCase()) {
            case "h2": return "org.h2.Driver";
            case "postgres":
            case "postgresql": return "org.postgresql.Driver";
            case "mysql": return "com.mysql.cj.jdbc.Driver";
            case "mariadb": return "org.mariadb.jdbc.Driver";
            case "mssql":
            case "sqlserver": return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
            case "oracle": return "oracle.jdbc.OracleDriver";
            case "sqlite": return "org.sqlite.JDBC";
            default: return null;
        }
    }
}

