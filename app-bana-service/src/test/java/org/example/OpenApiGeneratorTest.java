package com.appbana;

import com.appbana.model.EntitySchema;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class OpenApiGeneratorTest {
    @Test
    void generatesPathsAndSchemas() {
        EntitySchema.Field id = new EntitySchema.Field();
        id.setName("id"); id.setType("long"); id.setPrimaryKey(true); id.setAutoIncrement(true);
        EntitySchema.Field name = new EntitySchema.Field();
        name.setName("firstName"); name.setType("string"); name.setLength(100); name.setRequired(true);
        EntitySchema schema = new EntitySchema();
        schema.setName("contact");
        schema.setFields(List.of(id, name));

        String spec = OpenApiGenerator.generate(List.of(schema));
        assertNotNull(spec);
        assertTrue(spec.contains("/api/contact"), "spec should include CRUD path");
        assertTrue(spec.contains("\"contact\""), "spec should include schema name");
        assertTrue(spec.contains("components"), "spec should include components section");
    }
}

