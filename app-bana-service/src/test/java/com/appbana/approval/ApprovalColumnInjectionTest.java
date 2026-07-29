package com.appbana.approval;

import com.appbana.JdbcManager;
import com.appbana.SchemaManager;
import com.appbana.model.EntitySchema;
import com.appbana.model.TenantContext;
import com.appbana.service.EntityCrudService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task C4.6 — {@code approvalRequired == true} must imply the eight physical approval
 * columns exist, for <em>every</em> writer of the flag, not just {@code scaffold_app}.
 *
 * <p>Before C4.6 the only code that materialised those columns was {@code SchemaEnricher},
 * in the separate ai-builder process, reachable from one of four writers. {@code SchemaManager}
 * contained no reference to approvals at all — {@link com.appbana.approval.ApprovalColumns}'s
 * own javadoc described "the eight system columns SchemaManager injects" as an invariant no
 * code implemented. C4.1 made the flag reachable from {@code create_entity}, which turned that
 * latent gap into a live defect: a table that accepts an insert (the forced DRAFT / revision /
 * submitted_by values are silently dropped, since the insert iterates {@code schema.getFields()})
 * and then throws {@code column "APPROVAL_STATUS" does not exist} on submit, on approve, and on
 * every load of a checker's pending-count badge.
 *
 * <p>Every test here saves a schema carrying <b>only business fields</b> plus the flag —
 * exactly what a real caller sends.
 */
class ApprovalColumnInjectionTest {

    private static final String TENANT_ID = "t_c46";
    private static final String APP_ID = "app_c46";
    private static final String MAKER = "maker_mia";
    private static final String CHECKER = "checker_carl";

    @BeforeAll
    static void setUpDb() throws Exception {
        try (Connection c = JdbcManager.getConnection("default");
                Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS appbana_schemas (" +
                    "name VARCHAR(255) PRIMARY KEY, json CLOB, tenant_id VARCHAR(255), app_id VARCHAR(255))");
        }
    }

    @BeforeEach
    void clean() throws Exception {
        try (Connection c = JdbcManager.getConnection("default");
                Statement s = c.createStatement()) {
            for (String entity : List.of("FlagOnly", "AlterPath", "PartialDecl", "NoFlag")) {
                s.execute("DROP TABLE IF EXISTS \"" + physicalName(entity) + "\"");
            }
        }
    }

    private static String physicalName(String entity) {
        return ("APP_" + TENANT_ID + "_" + APP_ID + "_" + entity).toUpperCase(Locale.ROOT);
    }

    /** Business fields only — no approval columns. This is what a real caller sends. */
    private static EntitySchema businessSchema(String entityName, boolean approvalRequired) {
        EntitySchema schema = new EntitySchema(entityName, List.of(
                new EntitySchema.Field("id", "integer", true, true, null),
                new EntitySchema.Field("applicant_name", "string", false, false, null),
                new EntitySchema.Field("amount", "decimal", false, false, null)));
        schema.setTenantId(TENANT_ID);
        schema.setAppId(APP_ID);
        schema.setApprovalRequired(approvalRequired);
        return schema;
    }

    private static Set<String> physicalColumns(String entityName) throws Exception {
        Set<String> cols = new HashSet<>();
        try (Connection c = JdbcManager.getConnection("default")) {
            DatabaseMetaData md = c.getMetaData();
            try (ResultSet rs = md.getColumns(null, null, physicalName(entityName), null)) {
                while (rs.next()) {
                    cols.add(rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                }
            }
        }
        return cols;
    }

    static List<String> approvalColumnNames() {
        return ApprovalColumns.NAMES;
    }

    /**
     * Driven off {@code ApprovalColumns.NAMES} rather than a hardcoded set, so adding a
     * ninth column extends coverage automatically instead of leaving a silent hole.
     * {@code assertAll} so one missing column doesn't mask the rest.
     */
    @Test
    void createPathMaterialisesEveryApprovalColumnFromTheFlagAlone() throws Exception {
        SchemaManager.saveSchema(businessSchema("FlagOnly", true));

        Set<String> cols = physicalColumns("FlagOnly");
        assertFalse(cols.isEmpty(), "table should have been created");
        assertAll(ApprovalColumns.NAMES.stream().map(column -> () -> assertTrue(cols.contains(column),
                "approvalRequired=true must imply physical column '" + column + "'. Present: " + cols)));
    }

    @Test
    void alterPathAddsEveryApprovalColumnWhenTheFlagIsTurnedOnLater() throws Exception {
        // Save WITHOUT the flag first, so the table exists in its non-approval shape,
        // then turn approvals on. This is "add an approval workflow to an existing
        // entity" — a create-path-only fix would leave this table permanently broken.
        SchemaManager.saveSchema(businessSchema("AlterPath", false));
        Set<String> before = physicalColumns("AlterPath");
        assertAll(ApprovalColumns.NAMES.stream().map(column -> () -> assertFalse(before.contains(column),
                "precondition: no approval columns before the flag is set")));

        SchemaManager.saveSchema(businessSchema("AlterPath", true));

        Set<String> after = physicalColumns("AlterPath");
        assertAll(ApprovalColumns.NAMES.stream().map(column -> () -> assertTrue(after.contains(column),
                "turning approvalRequired on must add '" + column + "' to the existing table. Present: "
                        + after)));
    }

    @Test
    void nonApprovalEntitiesGetNoApprovalColumns() throws Exception {
        SchemaManager.saveSchema(businessSchema("NoFlag", false));

        Set<String> cols = physicalColumns("NoFlag");
        assertFalse(cols.isEmpty(), "table should have been created");
        for (String name : ApprovalColumns.NAMES) {
            assertFalse(cols.contains(name),
                    "no flag means no approval columns; found '" + name + "' in " + cols);
        }
    }

    /**
     * A schema that declares some approval columns itself — what a C4.4 RAG template will
     * teach the agent to emit — must not produce duplicates or an error. The backend
     * dedupes against declared names, which is why deleting SchemaEnricher's
     * rename-and-inject logic is safe.
     */
    @Test
    void declaredApprovalColumnsAreDedupedRatherThanDuplicated() throws Exception {
        EntitySchema schema = new EntitySchema("PartialDecl", List.of(
                new EntitySchema.Field("id", "integer", true, true, null),
                new EntitySchema.Field("applicant_name", "string", false, false, null),
                new EntitySchema.Field("approval_status", "status", false, false, null),
                new EntitySchema.Field("submitted_by", "string", false, false, null)));
        schema.setTenantId(TENANT_ID);
        schema.setAppId(APP_ID);
        schema.setApprovalRequired(true);

        SchemaManager.saveSchema(schema);

        Set<String> cols = physicalColumns("PartialDecl");
        for (String name : ApprovalColumns.NAMES) {
            assertTrue(cols.contains(name), "missing '" + name + "' in " + cols);
        }
        assertNotNull(SchemaManager.loadSchema(TENANT_ID + "_" + APP_ID + "_PartialDecl"));
    }

    /**
     * The assertion that would have failed before C4.6. Column-existence checks alone
     * prove the DDL runs; they do not prove the workflow can actually drive those columns.
     * This walks insert &rarr; submit &rarr; approve on a flag-only schema and reads the
     * <em>stored</em> values back out of Postgres rather than trusting the HTTP response
     * body — the pre-C4.6 insert returned 201 while silently discarding the forced DRAFT
     * state, so a response-shaped assertion would have passed on a broken table.
     */
    @Test
    void fullSubmitApproveLifecycleWorksOnAFlagOnlySchema() throws Exception {
        EntitySchema schema = businessSchema("FlagOnly", true);
        SchemaManager.saveSchema(schema);
        schema = SchemaManager.loadSchema(TENANT_ID + "_" + APP_ID + "_FlagOnly");
        assertNotNull(schema, "schema must be loadable after save");
        assertTrue(schema.isApprovalRequired(), "flag must survive the round-trip");

        UserRoleService.grantRole(TENANT_ID, APP_ID, "FlagOnly", MAKER, UserRoleService.Role.MAKER, "system");
        UserRoleService.grantRole(TENANT_ID, APP_ID, "FlagOnly", CHECKER, UserRoleService.Role.CHECKER, "system");

        // Exactly the map GenericEntityRoutes.enforceApprovalPreInsert() hands to the CRUD
        // service: business fields plus the three server-assigned approval values (it strips
        // any client-supplied approval column first, so these are server-owned by construction).
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("applicant_name", "Ada");
        data.put("amount", 1200);
        data.put("approval_status", "DRAFT");
        data.put("approval_revision", 1);
        data.put("submitted_by", MAKER);

        Object id = new EntityCrudService().insertRecord(new TenantContext(TENANT_ID, APP_ID), schema, data);
        assertNotNull(id, "insert must succeed on an approval-required entity");
        String rowId = String.valueOf(id);

        // Reads the STORED value, not the response body. The insert path iterates
        // schema.getFields(), which no longer contains the approval columns, so without
        // C4.6's write pass this lands as NULL while the insert still reports success.
        assertEquals("DRAFT", storedApprovalStatus("FlagOnly", rowId),
                "server-assigned approval state must actually be persisted");

        ApprovalService.submitForApproval(TENANT_ID, APP_ID, "FlagOnly", rowId, MAKER, "please review");
        assertEquals("PENDING", storedApprovalStatus("FlagOnly", rowId),
                "submit must move the stored state to PENDING");
        assertEquals(MAKER, storedString("FlagOnly", rowId, "SUBMITTED_BY"));

        ApprovalService.approveRecord(TENANT_ID, APP_ID, "FlagOnly", rowId, CHECKER, "ok");
        assertEquals("APPROVED", storedApprovalStatus("FlagOnly", rowId),
                "approve must move the stored state to APPROVED");
        assertEquals(CHECKER, storedString("FlagOnly", rowId, "APPROVED_BY"));
    }

    private static String storedApprovalStatus(String entity, String rowId) throws Exception {
        return storedString(entity, rowId, "APPROVAL_STATUS");
    }

    private static String storedString(String entity, String rowId, String column) throws Exception {
        try (Connection c = JdbcManager.getConnection("default");
                Statement s = c.createStatement();
                ResultSet rs = s.executeQuery("SELECT \"" + column + "\" FROM \"" + physicalName(entity)
                        + "\" WHERE \"ID\" = " + Integer.parseInt(rowId))) {
            assertTrue(rs.next(), "row " + rowId + " should exist");
            return rs.getString(1);
        }
    }
}
