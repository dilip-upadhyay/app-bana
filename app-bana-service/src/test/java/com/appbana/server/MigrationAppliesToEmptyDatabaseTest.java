package com.appbana.server;

import com.appbana.config.AppConfig;
import com.appbana.config.ConfigManager;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S2.11 — the Liquibase changelog must migrate a genuinely empty database, not merely apply cleanly
 * on top of one that already has V0-V18. Every other test in this suite runs against the shared,
 * already-migrated dev Postgres, which cannot prove this — it is the exact property the V0 bootstrap
 * incident violated (a changeset referencing something only Java created lazily at runtime).
 *
 * Deliberately does NOT reuse {@link com.appbana.ApiServer#startJdk}'s Liquibase invocation verbatim
 * (S2.1 review round 25): that block's connection comes from {@code JdbcManager}'s shared datasource,
 * which would silently test the wrong (already-migrated) database and prove nothing; it also sits next
 * to a {@code dropAll()} branch that has no business anywhere near a test. This test opens its own
 * dedicated connection to a uniquely-named, throwaway database, asserts it is genuinely connected
 * there, and runs {@code update()} only.
 */
public class MigrationAppliesToEmptyDatabaseTest {

    private static final Pattern CHANGE_SET = Pattern.compile("<changeSet\\s");

    @Test
    public void changelogMigratesAGenuinelyEmptyDatabase() throws Exception {
        AppConfig cfg = ConfigManager.getConfig();
        String adminUrl = withDatabase(cfg.getJdbcUrl(), "postgres");
        String probeDb = "appbana_migration_probe_" + System.currentTimeMillis();

        try (Connection admin = DriverManager.getConnection(adminUrl, cfg.getUsername(), cfg.getPassword());
             Statement st = admin.createStatement()) {
            // TEMPLATE template0, not the default template1: template1 is explicitly customisable and
            // a table left in it would make this database "empty" in name only, silently defeating the
            // whole point of this test (round-25 review finding).
            st.execute("CREATE DATABASE " + probeDb + " TEMPLATE template0");
        }

        try {
            String probeUrl = withDatabase(cfg.getJdbcUrl(), probeDb);

            // Guard against the round-25 "silent no-op" hazard: prove this connection is genuinely
            // pointed at the empty throwaway database, never the shared already-migrated one.
            try (Connection checkConn = DriverManager.getConnection(probeUrl, cfg.getUsername(), cfg.getPassword());
                 Statement check = checkConn.createStatement();
                 ResultSet rs = check.executeQuery("SELECT current_database()")) {
                assertTrue(rs.next());
                assertEquals(probeDb, rs.getString(1),
                        "must be connected to the throwaway database, not the shared dev datasource");
            }

            // Liquibase.close() also closes the Connection it wraps, so migration runs in its own
            // connection, separate from the connection used for the post-migration assertions below.
            try (Connection migrationConn = DriverManager.getConnection(probeUrl, cfg.getUsername(), cfg.getPassword())) {
                Database database = DatabaseFactory.getInstance()
                        .findCorrectDatabaseImplementation(new JdbcConnection(migrationConn));
                try (Liquibase liquibase = new Liquibase(
                        "db/changelog/db.changelog-master.xml",
                        new ClassLoaderResourceAccessor(),
                        database)) {
                    // update() only -- never dropAll(), which ApiServer.startJdk gates on
                    // flywayCleanOnStart and which has no place anywhere near a test.
                    liquibase.update(new Contexts(), new LabelExpression());
                }
            }

            try (Connection verifyConn = DriverManager.getConnection(probeUrl, cfg.getUsername(), cfg.getPassword())) {
                try (Statement check = verifyConn.createStatement();
                     ResultSet rs = check.executeQuery(
                             "SELECT to_regclass('public.appbana_app_members') IS NOT NULL")) {
                    assertTrue(rs.next());
                    assertTrue(rs.getBoolean(1), "V19's appbana_app_members must exist after migration");
                }

                int expectedChangeSets = countChangeSetsInMasterChangelog();
                try (Statement check = verifyConn.createStatement();
                     ResultSet rs = check.executeQuery("SELECT COUNT(*) FROM databasechangelog")) {
                    assertTrue(rs.next());
                    assertEquals(expectedChangeSets, rs.getInt(1),
                            "every changeset in db.changelog-master.xml must have executed exactly once");
                }
            }
        } finally {
            dropProbeDatabase(adminUrl, cfg, probeDb);
        }
    }

    /** Derives the expected count from the changelog itself rather than hardcoding it, so this test
     *  doesn't need updating every time a new V-numbered changeset is added. */
    private static int countChangeSetsInMasterChangelog() throws Exception {
        try (InputStream in = MigrationAppliesToEmptyDatabaseTest.class.getClassLoader()
                .getResourceAsStream("db/changelog/db.changelog-master.xml");
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            int count = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher m = CHANGE_SET.matcher(line);
                while (m.find()) {
                    count++;
                }
            }
            return count;
        }
    }

    private static void dropProbeDatabase(String adminUrl, AppConfig cfg, String probeDb) throws Exception {
        try (Connection admin = DriverManager.getConnection(adminUrl, cfg.getUsername(), cfg.getPassword());
             Statement st = admin.createStatement()) {
            // Force-terminate lingering backends first -- a run killed mid-migration otherwise leaves
            // this DROP unable to proceed (learned from the reviewer's own round-23 probe experience).
            st.execute("SELECT pg_terminate_backend(pid) FROM pg_stat_activity "
                    + "WHERE datname = '" + probeDb + "' AND pid <> pg_backend_pid()");
            st.execute("DROP DATABASE IF EXISTS " + probeDb);
        }
    }

    private static String withDatabase(String jdbcUrl, String database) {
        // Preserve any query string (e.g. ?currentSchema=X) rather than silently dropping it, which
        // would otherwise migrate against a different schema/mode than the real config intends.
        int queryIndex = jdbcUrl.indexOf('?');
        String base = queryIndex >= 0 ? jdbcUrl.substring(0, queryIndex) : jdbcUrl;
        String query = queryIndex >= 0 ? jdbcUrl.substring(queryIndex) : "";
        int lastSlash = base.lastIndexOf('/');
        return base.substring(0, lastSlash + 1) + database + query;
    }
}
