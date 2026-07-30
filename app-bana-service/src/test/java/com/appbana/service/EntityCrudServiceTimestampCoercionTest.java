package com.appbana.service;

import com.appbana.model.EntitySchema;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Focused unit tests for EntityCrudService.coerceAndValidate() TIMESTAMP-type
 * handling.
 *
 * <p>DetailPage.tsx's edit form renders {@code date}/{@code datetime} fields
 * as native {@code <input type="datetime-local">}, whose {@code value} the
 * browser always reports as {@code yyyy-MM-ddTHH:mm} — no seconds, no
 * timezone offset. That string failed every branch this coercion tried
 * ({@code Instant.parse}, the date-only length check, {@code Timestamp.valueOf}
 * which requires seconds) and fell through to "field 'x' invalid format",
 * surfacing as a 400 on every save of a datetime field through the runtime's
 * record-edit screen — including {@code created_at}/{@code updated_at}, which
 * were (incorrectly) rendered as editable inputs at all. This test locks in
 * the missing-seconds fallback; the editability fix lives in DetailPage.tsx.
 *
 * <p>Uses reflection because the coerce method is package-private static and
 * we don't want to widen its visibility for a test.
 */
class EntityCrudServiceTimestampCoercionTest {

    private static Object coerce(EntitySchema.Field f, Object raw) throws Exception {
        Method m = EntityCrudService.class
                .getDeclaredMethod("coerceAndValidate", EntitySchema.Field.class, Object.class);
        m.setAccessible(true);
        try {
            return m.invoke(null, f, raw);
        } catch (java.lang.reflect.InvocationTargetException ite) {
            if (ite.getCause() instanceof RuntimeException re) throw re;
            throw ite;
        }
    }

    private static EntitySchema.Field timestampField(String name) {
        EntitySchema.Field f = new EntitySchema.Field();
        f.setName(name);
        f.setType("datetime");
        return f;
    }

    @Test
    void coercesNativeDatetimeLocalValueMissingSeconds() throws Exception {
        Object out = coerce(timestampField("upload_date"), "2026-07-30T20:47");
        assertInstanceOf(Timestamp.class, out);
        assertEquals(Timestamp.valueOf("2026-07-30 20:47:00"), out);
    }

    @Test
    void coercesFullIso8601WithOffsetUnchanged() throws Exception {
        Object out = coerce(timestampField("submitted_at"), "2026-07-30T20:47:00.000Z");
        // Must be a JDBC-bindable java.sql.Timestamp, NOT a bare java.time.Instant —
        // the Postgres driver's setObject() cannot infer a SQL type for Instant and
        // throws "Can't infer the SQL type to use for an instance of java.time.Instant"
        // at bind time (not at coercion time), which is why a plain assertNotNull()
        // here previously let this bug ship: Instant.parse(rs) also returns non-null.
        assertInstanceOf(Timestamp.class, out);
    }

    @Test
    void coercesIso8601WithNumericOffsetToTimestamp() throws Exception {
        // Reproduces the exact value shape that triggered the production 500: a record
        // re-saved through DetailPage.tsx forwards its own previously-fetched
        // upload_date/created_at value back unchanged, e.g. "...+00:00" (numeric offset,
        // not "Z"). Must coerce to java.sql.Timestamp, mirroring parseFilterValue()'s
        // established `Timestamp.from(Instant.parse(v))` pattern for the same case.
        Object out = coerce(timestampField("upload_date"), "2026-07-30T15:17:00.000+00:00");
        assertInstanceOf(Timestamp.class, out);
        assertEquals(Timestamp.from(java.time.Instant.parse("2026-07-30T15:17:00.000+00:00")), out);
    }

    @Test
    void coercesDateOnlyString() throws Exception {
        Object out = coerce(timestampField("upload_date"), "2026-07-22");
        assertInstanceOf(java.sql.Date.class, out);
        assertEquals(java.sql.Date.valueOf("2026-07-22"), out);
    }

    @Test
    void rejectsTrulyUnparsableString() {
        Exception ex = assertThrows(Exception.class, () -> coerce(timestampField("created_at"), "not-a-date"));
        String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
        assertTrue(msg.contains("invalid format"), "expected 'invalid format' message, got: " + msg);
    }
}
