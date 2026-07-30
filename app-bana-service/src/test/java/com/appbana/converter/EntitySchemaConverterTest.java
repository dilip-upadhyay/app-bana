package com.appbana.converter;

import com.appbana.model.EntitySchema;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Found live (2026-07-30): scaffolding an Employee Onboarding app via the
 * Studio chat -> scaffold_app -> AppPublishService.deploySchemasTransactionally
 * pipeline failed with:
 *
 *   "foreign key constraint ... cannot be implemented: Key columns
 *    HEAD_OF_DEPARTMENT and ID are of incompatible types: character
 *    varying and integer"
 *
 * Root cause: {@link EntitySchemaConverter#convert} is the ONLY place the
 * scaffold_app/deploy_app pipeline turns the AI-generated entity JSON into an
 * {@link EntitySchema}, and its type-mapping switch had no case for
 * "reference" (or "decimal"/"longtext") — they fell through the default
 * branch and were silently downgraded to a plain "string" (VARCHAR(255))
 * column. For "reference" fields this breaks FK creation entirely (wrong
 * column type vs. the parent's INTEGER primary key); for "decimal"/"longtext"
 * it silently mis-types money/long-text columns.
 *
 * This is distinct from {@code SchemaManagerForeignKeyTest}, which only
 * exercises {@code SchemaManager.saveSchema} directly with a hand-built,
 * already-correctly-typed {@link EntitySchema} — it never goes through this
 * converter, which is why the bug was invisible to the existing suite despite
 * FK support being otherwise well-tested.
 */
public class EntitySchemaConverterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void referenceFieldConvertsToReferenceTypeAndKeepsFkMetadata() throws Exception {
        JsonNode entity = MAPPER.readTree("""
                {
                  "fields": [
                    { "name": "head_of_department", "type": "reference",
                      "referenceEntity": "Employee", "onDelete": "setNull", "required": true }
                  ]
                }
                """);
        EntitySchema schema = EntitySchemaConverter.convert("Department", entity);
        EntitySchema.Field f = schema.getFields().stream()
                .filter(x -> "head_of_department".equals(x.getName()))
                .findFirst().orElseThrow();

        assertEquals("reference", f.getType());
        assertEquals("Employee", f.getReferenceEntity());
        assertEquals("setNull", f.getOnDelete());
    }

    @Test
    public void decimalFieldConvertsToDecimalNotString() throws Exception {
        JsonNode entity = MAPPER.readTree("""
                {
                  "fields": [
                    { "name": "salary", "type": "decimal", "required": true }
                  ]
                }
                """);
        EntitySchema schema = EntitySchemaConverter.convert("Employee", entity);
        EntitySchema.Field f = schema.getFields().stream()
                .filter(x -> "salary".equals(x.getName()))
                .findFirst().orElseThrow();

        assertEquals("decimal", f.getType());
    }

    @Test
    public void longtextFieldConvertsToTextNotString() throws Exception {
        JsonNode entity = MAPPER.readTree("""
                {
                  "fields": [
                    { "name": "notes", "type": "longtext", "required": false }
                  ]
                }
                """);
        EntitySchema schema = EntitySchemaConverter.convert("OnboardingTask", entity);
        EntitySchema.Field f = schema.getFields().stream()
                .filter(x -> "notes".equals(x.getName()))
                .findFirst().orElseThrow();

        assertEquals("text", f.getType());
    }

    @Test
    public void statusFieldConvertsToStringWithoutWarningPath() throws Exception {
        JsonNode entity = MAPPER.readTree("""
                {
                  "fields": [
                    { "name": "employment_type", "type": "status", "required": true }
                  ]
                }
                """);
        EntitySchema schema = EntitySchemaConverter.convert("Employee", entity);
        EntitySchema.Field f = schema.getFields().stream()
                .filter(x -> "employment_type".equals(x.getName()))
                .findFirst().orElseThrow();

        assertEquals("string", f.getType());
    }

    @Test
    public void referenceWithoutMetadataStillTypesAsReference() throws Exception {
        // A reference field with no referenceEntity supplied should still type
        // as "reference" (not silently downgrade to string); SchemaManager's
        // syncForeignKeys already tolerates a missing/blank referenceEntity by
        // skipping FK creation for that field.
        JsonNode entity = MAPPER.readTree("""
                {
                  "fields": [
                    { "name": "manager", "type": "reference" }
                  ]
                }
                """);
        EntitySchema schema = EntitySchemaConverter.convert("Employee", entity);
        EntitySchema.Field f = schema.getFields().stream()
                .filter(x -> "manager".equals(x.getName()))
                .findFirst().orElseThrow();

        assertEquals("reference", f.getType());
        assertNull(f.getReferenceEntity());
    }
}
