package com.appbana;

import com.appbana.model.EntitySchema;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.StringJoiner;

public class CodeGenerator {
    // Generates simple POJO source into ./generated-sources/{Entity}.java
    public static Path generate(EntitySchema schema) throws IOException {
        String pkg = "com.appbana.generated";
        String className = capitalize(schema.getName());
        StringJoiner sj = new StringJoiner(System.lineSeparator());
        sj.add("package " + pkg + ";");
        sj.add("");
        sj.add("public class " + className + " {");
        // fields
        for (EntitySchema.Field f : schema.getFields()) {
            sj.add("    private " + mapType(f.getType()) + " " + f.getName() + ";");
        }
        sj.add("");
        // getters/setters
        for (EntitySchema.Field f : schema.getFields()) {
            String t = mapType(f.getType());
            String fname = f.getName();
            String cap = capitalize(fname);
            sj.add("    public " + t + " get" + cap + "() { return this." + fname + "; }");
            sj.add("    public void set" + cap + "(" + t + " v) { this." + fname + " = v; }");
            sj.add("");
        }
        sj.add("}");

        Path outDir = Path.of("generated-sources", "org", "example", "generated");
        Files.createDirectories(outDir);
        Path out = outDir.resolve(className + ".java");
        Files.writeString(out, sj.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return out;
    }

    private static String mapType(String t) {
        if (t == null) return "String";
        switch (t.toLowerCase()) {
            case "int":
            case "integer":
                return "Integer";
            case "long":
                return "Long";
            case "boolean":
                return "Boolean";
            case "date":
            case "timestamp":
                return "java.time.Instant";
            case "text":
            case "string":
            default:
                return "String";
        }
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}

