package com.appbana.service;

import com.appbana.model.EntitySchema;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Focused unit tests for EntityCrudService.coerceAndValidate() decimal-type
 * handling.
 *
 * <p>Pre-Sprint 3 review this branch didn't exist — decimal values fell into
 * the default branch, got {@code .toString()}'d, and Postgres rejected the
 * resulting VARCHAR bind against a NUMERIC column with:
 *
 * <pre>ERROR: column "PRICE" is of type numeric but expression is of type character varying</pre>
 *
 * <p>Every AI-generated app with a {@code price}/{@code amount}/{@code total}
 * field hit this silently. This test locks the fix in.
 *
 * <p>Uses reflection because the coerce method is package-private static and
 * we don't want to widen its visibility for a test.
 */
class EntityCrudServiceDecimalCoercionTest {

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

    private static EntitySchema.Field decimalField(String name) {
        EntitySchema.Field f = new EntitySchema.Field();
        f.setName(name);
        f.setType("decimal");
        return f;
    }

    @Test
    void coercesDoubleToBigDecimal() throws Exception {
        Object out = coerce(decimalField("price"), 9.99d);
        assertInstanceOf(BigDecimal.class, out);
        assertEquals(0, ((BigDecimal) out).compareTo(new BigDecimal("9.99")));
    }

    @Test
    void coercesIntegerToBigDecimal() throws Exception {
        Object out = coerce(decimalField("amount"), 42);
        assertInstanceOf(BigDecimal.class, out);
        assertEquals(0, ((BigDecimal) out).compareTo(new BigDecimal("42")));
    }

    @Test
    void coercesStringToBigDecimal() throws Exception {
        Object out = coerce(decimalField("total"), "123.456");
        assertInstanceOf(BigDecimal.class, out);
        assertEquals(0, ((BigDecimal) out).compareTo(new BigDecimal("123.456")));
    }

    @Test
    void passesBigDecimalThroughUnchanged() throws Exception {
        BigDecimal in = new BigDecimal("7.77");
        Object out = coerce(decimalField("cost"), in);
        assertSame(in, out, "existing BigDecimal should pass through untouched");
    }

    @Test
    void blankStringYieldsNull() throws Exception {
        Object out = coerce(decimalField("optional_price"), "  ");
        assertNull(out, "blank/whitespace decimal input should coerce to null (not error)");
    }

    @Test
    void invalidDecimalStringSurfacesAsFieldValidationError() {
        EntitySchema.Field f = decimalField("price");
        FieldValidationException fve = assertThrows(FieldValidationException.class,
                () -> coerce(f, "not-a-number"));
        assertTrue(fve.getFieldErrors().containsKey("price"),
                "field errors map should contain the offending field: " + fve.getFieldErrors());
    }

    @Test
    void numericTypeAliasCoercesAsStringNotDecimal() throws Exception {
        // Review #5 (blocker) — "numeric" (and "money"/"serial"/"bigserial") are
        // deliberately NOT decimal/integer aliases. Before the shared
        // classifyFieldType() classifier existed, sqlType() had no case for any
        // of the four, so a "numeric" column physically IS VARCHAR(255) for
        // every existing tenant. Round 3 had added "numeric" to this coercion
        // switch only, which would have made SchemaManager issue
        // `ALTER TABLE ... TYPE NUMERIC(19,4) USING col::numeric` on the next
        // schema save for such a table, breaking it for any tenant whose
        // "numeric" column ever held non-numeric free text (which the old
        // fallback-to-VARCHAR(255) behavior explicitly allowed). Coercing it as
        // a plain string keeps insert/filter behavior consistent with the DDL
        // that already exists.
        EntitySchema.Field f = decimalField("qty");
        f.setType("numeric"); // alias
        Object out = coerce(f, "3.14");
        assertInstanceOf(String.class, out);
        assertEquals("3.14", out);
    }

    @Test
    void avoidsDoublePrecisionNoise() throws Exception {
        // Classic float trap: 0.1 + 0.2 = 0.30000000000000004 via double math.
        // Our coercion goes via Number.toString() so 0.1d stays "0.1".
        Object out = coerce(decimalField("price"), 0.1d);
        assertEquals(new BigDecimal("0.1"), out,
                "coercion should use toString() to avoid IEEE-754 noise");
    }

    @Test
    void enforcesMinBound() {
        EntitySchema.Field f = decimalField("price");
        f.setMin(10L);
        FieldValidationException fve = assertThrows(FieldValidationException.class,
                () -> coerce(f, "5.5"));
        assertEquals("below min", fve.getFieldErrors().get("price"));
    }

    @Test
    void enforcesMaxBound() {
        EntitySchema.Field f = decimalField("price");
        f.setMax(100L);
        FieldValidationException fve = assertThrows(FieldValidationException.class,
                () -> coerce(f, "150.0"));
        assertEquals("above max", fve.getFieldErrors().get("price"));
    }
}
