package com.appbana.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Direct unit tests for {@link EntityCrudService#isSensitiveColumnName}, {@link
 * EntityCrudService#redactSensitiveColumns}, and {@link
 * EntityCrudService#redactSensitiveColumnsFromList} (Task S4.8 — Tenant Isolation Security Plan).
 *
 * <p>Before S4.8, every generic-entity read-path response ({@code GET /api/{entity}}, {@code GET
 * /api/{entity}/{id}}, bulk-export, the studio- and env-scoped list/single routes, the approval
 * pending-queue, and {@code GET /audit}) returned a "password"/"secret"-named column's real stored
 * value (a BCrypt hash for the one column S4.2 auto-hashes, or a raw plaintext value for any other
 * schema author-chosen credential-shaped column name) straight to the client.
 *
 * <p>This file covers only the pure, in-memory redaction utility itself — no database or HTTP
 * server needed, since these methods operate solely on already-fetched {@code Map<String,Object>}
 * row data. Route-level wiring (proving each of the real call sites actually invokes this utility)
 * is covered separately by {@code GenericEntityRoutesRedactionTest} and {@code
 * ApprovalRoutesRedactionTest}, since a unit test of the utility alone cannot catch "the utility
 * exists but a route forgot to call it."
 */
class EntityCrudServiceRedactionTest {

    // ========================================
    // isSensitiveColumnName
    // ========================================

    @Test
    @DisplayName("isSensitiveColumnName: null is never sensitive")
    void testNullIsNotSensitive() {
        assertFalse(EntityCrudService.isSensitiveColumnName(null));
    }

    @Test
    @DisplayName("isSensitiveColumnName: exact 'password' matches")
    void testExactPasswordMatches() {
        assertTrue(EntityCrudService.isSensitiveColumnName("password"));
    }

    @Test
    @DisplayName("isSensitiveColumnName: case-insensitive — 'PASSWORD', 'Password', 'sEcReT' all match")
    void testCaseInsensitiveMatching() {
        assertTrue(EntityCrudService.isSensitiveColumnName("PASSWORD"));
        assertTrue(EntityCrudService.isSensitiveColumnName("Password"));
        assertTrue(EntityCrudService.isSensitiveColumnName("SECRET"));
        assertTrue(EntityCrudService.isSensitiveColumnName("sEcReT"));
    }

    @Test
    @DisplayName("isSensitiveColumnName: substring match — any column NAME containing 'password' or "
            + "'secret' is sensitive, not just an exact-match column")
    void testSubstringMatching() {
        assertTrue(EntityCrudService.isSensitiveColumnName("user_password"));
        assertTrue(EntityCrudService.isSensitiveColumnName("password_hash"));
        assertTrue(EntityCrudService.isSensitiveColumnName("api_secret"));
        assertTrue(EntityCrudService.isSensitiveColumnName("secret_token"));
        assertTrue(EntityCrudService.isSensitiveColumnName("client_secret_key"));
    }

    @Test
    @DisplayName("isSensitiveColumnName: raw uppercase JDBC column labels (as returned by listAll/"
            + "getById's SELECT *) still match — case-insensitivity must survive driver casing")
    void testUppercaseJdbcColumnLabelsMatch() {
        assertTrue(EntityCrudService.isSensitiveColumnName("PASSWORD"));
        assertTrue(EntityCrudService.isSensitiveColumnName("SECRET_TOKEN"));
    }

    @Test
    @DisplayName("isSensitiveColumnName: ordinary business field names are never sensitive")
    void testOrdinaryFieldNamesAreNotSensitive() {
        assertFalse(EntityCrudService.isSensitiveColumnName("email"));
        assertFalse(EntityCrudService.isSensitiveColumnName("name"));
        assertFalse(EntityCrudService.isSensitiveColumnName("id"));
        assertFalse(EntityCrudService.isSensitiveColumnName("amount"));
        assertFalse(EntityCrudService.isSensitiveColumnName("approval_status"));
        // "secretary" contains neither "password" nor "secret" as the FULL word "secret" —
        // but it DOES contain "secret" as a substring ("secret"ary), so this is intentionally
        // sensitive under the documented substring-match convention, not a bug. Documented here
        // so a future reader doesn't "fix" isSensitiveColumnName to require a word boundary.
        assertTrue(EntityCrudService.isSensitiveColumnName("secretary"),
                "substring matching is intentionally broad — 'secretary' contains 'secret'");
    }

    @Test
    @DisplayName("isSensitiveColumnName: blank string is not sensitive")
    void testBlankStringIsNotSensitive() {
        assertFalse(EntityCrudService.isSensitiveColumnName(""));
        assertFalse(EntityCrudService.isSensitiveColumnName("   "));
    }

    // ========================================
    // redactSensitiveColumns (single row)
    // ========================================

    @Test
    @DisplayName("redactSensitiveColumns: null input returns null")
    void testRedactNullRowReturnsNull() {
        assertNull(EntityCrudService.redactSensitiveColumns(null));
    }

    @Test
    @DisplayName("redactSensitiveColumns: empty map returns empty map")
    void testRedactEmptyMapReturnsEmptyMap() {
        Map<String, Object> result = EntityCrudService.redactSensitiveColumns(new LinkedHashMap<>());
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("redactSensitiveColumns: a row with no sensitive keys passes through with all "
            + "keys/values preserved")
    void testRedactRowWithNoSensitiveKeysIsUnchanged() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 1L);
        row.put("email", "alice@example.com");
        row.put("name", "Alice");

        Map<String, Object> result = EntityCrudService.redactSensitiveColumns(row);

        assertEquals(3, result.size());
        assertEquals(1L, result.get("id"));
        assertEquals("alice@example.com", result.get("email"));
        assertEquals("Alice", result.get("name"));
    }

    @Test
    @DisplayName("redactSensitiveColumns: the sensitive key is OMITTED entirely, not replaced with "
            + "a placeholder — mirrors GenericAppAuthController.login()'s own convention")
    void testRedactOmitsKeyEntirelyRatherThanMasking() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 1L);
        row.put("email", "alice@example.com");
        row.put("password", "$2a$12$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ01");

        Map<String, Object> result = EntityCrudService.redactSensitiveColumns(row);

        assertEquals(2, result.size(), "the password key must be removed, not merely blanked");
        assertFalse(result.containsKey("password"), "the key itself must be absent, not present with a placeholder value");
        assertEquals(1L, result.get("id"));
        assertEquals("alice@example.com", result.get("email"));
    }

    @Test
    @DisplayName("redactSensitiveColumns: multiple sensitive keys (password + a differently-named "
            + "secret column) are all omitted in a single pass")
    void testRedactOmitsMultipleSensitiveKeys() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 1L);
        row.put("password", "plaintext-or-hash");
        row.put("api_secret", "sk-live-abc123");
        row.put("name", "Bob");

        Map<String, Object> result = EntityCrudService.redactSensitiveColumns(row);

        assertEquals(2, result.size());
        assertEquals(1L, result.get("id"));
        assertEquals("Bob", result.get("name"));
        assertFalse(result.containsKey("password"));
        assertFalse(result.containsKey("api_secret"));
    }

    @Test
    @DisplayName("redactSensitiveColumns: uppercase JDBC-style column labels (as returned by raw "
            + "SELECT * via listAll/getById) are still redacted")
    void testRedactHandlesUppercaseJdbcKeys() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("ID", 1L);
        row.put("EMAIL", "alice@example.com");
        row.put("PASSWORD", "$2a$12$somehash");

        Map<String, Object> result = EntityCrudService.redactSensitiveColumns(row);

        assertEquals(2, result.size());
        assertFalse(result.containsKey("PASSWORD"));
        assertEquals(1L, result.get("ID"));
    }

    @Test
    @DisplayName("redactSensitiveColumns: a null-valued sensitive column is still omitted (the key "
            + "itself is what leaks intent/shape, regardless of whether the value happens to be null)")
    void testRedactOmitsSensitiveKeyEvenWhenValueIsNull() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 1L);
        row.put("password", null);

        Map<String, Object> result = EntityCrudService.redactSensitiveColumns(row);

        assertEquals(1, result.size());
        assertFalse(result.containsKey("password"));
    }

    @Test
    @DisplayName("redactSensitiveColumns: does not mutate the input map")
    void testRedactDoesNotMutateInput() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 1L);
        row.put("password", "secret-value");

        Map<String, Object> result = EntityCrudService.redactSensitiveColumns(row);

        assertNotSame(row, result, "must return a new map instance, not the same reference");
        assertEquals(2, row.size(), "the original input map must be left completely untouched");
        assertTrue(row.containsKey("password"), "the original map must still contain the sensitive key");
        assertEquals("secret-value", row.get("password"));
    }

    @Test
    @DisplayName("redactSensitiveColumns: preserves insertion order of the surviving keys")
    void testRedactPreservesOrderOfSurvivingKeys() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 1L);
        row.put("password", "x");
        row.put("email", "a@b.com");
        row.put("name", "Carol");

        Map<String, Object> result = EntityCrudService.redactSensitiveColumns(row);

        assertEquals(List.of("id", "email", "name"), List.copyOf(result.keySet()));
    }

    // ========================================
    // redactSensitiveColumnsFromList
    // ========================================

    @Test
    @DisplayName("redactSensitiveColumnsFromList: null input returns null")
    void testRedactListNullReturnsNull() {
        assertNull(EntityCrudService.redactSensitiveColumnsFromList(null));
    }

    @Test
    @DisplayName("redactSensitiveColumnsFromList: empty list returns empty list")
    void testRedactListEmptyReturnsEmpty() {
        List<Map<String, Object>> result = EntityCrudService.redactSensitiveColumnsFromList(List.of());
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("redactSensitiveColumnsFromList: redacts every row independently")
    void testRedactListRedactsEveryRow() {
        Map<String, Object> row1 = new LinkedHashMap<>();
        row1.put("id", 1L);
        row1.put("password", "hash1");

        Map<String, Object> row2 = new LinkedHashMap<>();
        row2.put("id", 2L);
        row2.put("password", "hash2");
        row2.put("name", "Dana");

        List<Map<String, Object>> result = EntityCrudService.redactSensitiveColumnsFromList(List.of(row1, row2));

        assertEquals(2, result.size());
        assertFalse(result.get(0).containsKey("password"));
        assertFalse(result.get(1).containsKey("password"));
        assertEquals(1L, result.get(0).get("id"));
        assertEquals(2L, result.get(1).get("id"));
        assertEquals("Dana", result.get(1).get("name"));
    }

    @Test
    @DisplayName("redactSensitiveColumnsFromList: does not mutate the input list's row maps")
    void testRedactListDoesNotMutateInputRows() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 1L);
        row.put("password", "hash1");
        List<Map<String, Object>> rows = new java.util.ArrayList<>(List.of(row));

        EntityCrudService.redactSensitiveColumnsFromList(rows);

        assertTrue(row.containsKey("password"), "original row map must be untouched");
    }
}
