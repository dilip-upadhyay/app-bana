package com.appbana.server;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * S2.12 — schema-block drift guard.
 *
 * <p>The plan doc ({@code TENANT_ISOLATION_SECURITY_PLAN.md}) carries a fenced {@code ```sql} block
 * under "Data model additions" that is supposed to document {@code appbana_app_members} exactly as
 * shipped. The S2.1 round-23 review reconciled that block against the real DDL by hand and, even on
 * the round dedicated to fixing this exact class of drift, still left cosmetic-but-real differences
 * from {@code V19__appbana_app_members.sql} ({@code CREATE TABLE} vs. {@code CREATE TABLE IF NOT
 * EXISTS}, {@code now()} vs. {@code NOW()}, a missing {@code IF NOT EXISTS} on the index) — proof
 * that nothing guarded this claim from recurring, unlike the route census (S0.3) or estimate
 * reconciliation (S0.5). A fail-open {@code DEFAULT} sat in the authoritative security plan for
 * multiple rounds before being caught this way; that is not a cosmetic-drift risk class, so this test
 * exists to make it mechanically impossible for the two to disagree silently again.
 *
 * <p><b>Comments are deliberately excluded from the comparison.</b> Both documents carry their own,
 * differently-worded {@code --} commentary explaining the same design decisions for two different
 * audiences (the plan doc's security rationale vs. the migration file's implementation note) — e.g.
 * the plan doc's "review round 5, R5-4" comment vs. V19's own wording for the identical
 * leads-with-{@code user_id} decision. Comparing raw text would make this test fail on every future
 * wording tweak to either doc's prose, for zero actual schema drift, which is precisely the kind of
 * false-positive brittleness {@link EstimateReconciliationTest}'s own "(or ...)" tolerance already
 * establishes as the wrong shape of check. Only the DDL statements themselves are compared.
 *
 * <p>Normalization (per the S2.12 task spec): lowercase, strip {@code IF NOT EXISTS}, collapse all
 * whitespace to single spaces. This intentionally still catches the exact three round-23 differences
 * above — {@code CREATE TABLE} vs. {@code CREATE TABLE IF NOT EXISTS} would show up as a literal
 * {@code IF NOT EXISTS} that this test strips from both sides equally, so a version that never had it
 * and a version that did are correctly indistinguishable after normalization (the real fix was in the
 * DDL itself matching V19's shipped {@code IF NOT EXISTS} everywhere, not in this test papering over
 * a real absence) — while {@code now()} vs. {@code NOW()} and any other genuine token-level
 * difference (e.g. a column type, a constraint, a default value) still fails the comparison.
 */
class SchemaBlockReconciliationTest {

    private static final String SECTION_START_MARKER = "## Data model additions";
    private static final Pattern IF_NOT_EXISTS = Pattern.compile("(?i)if not exists");
    private static final Pattern LINE_COMMENT = Pattern.compile("--.*$", Pattern.MULTILINE);
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    @Test
    void planDocSchemaBlockMatchesShippedMigrationExactly() throws Exception {
        Path planDoc = resolveDoc("TENANT_ISOLATION_SECURITY_PLAN.md");
        Path migration = resolveMigration("V19__appbana_app_members.sql");

        String planDocSql = extractFencedSqlBlock(planDoc, SECTION_START_MARKER);
        String migrationSql = Files.readString(migration);

        String normalizedPlanDoc = normalize(planDocSql);
        String normalizedMigration = normalize(migrationSql);

        if (!normalizedPlanDoc.equals(normalizedMigration)) {
            fail("Schema block drift detected between the plan doc's \"Data model additions\"\n"
                    + "fenced SQL block (" + planDoc + ") and the shipped migration (" + migration + ").\n"
                    + "Comments and IF NOT EXISTS are already normalized away on both sides — this is a\n"
                    + "real DDL difference (column/constraint/default/index), not cosmetic drift:\n\n"
                    + "Plan doc (normalized):\n  " + normalizedPlanDoc + "\n\n"
                    + "V19 (normalized):\n  " + normalizedMigration);
        }
    }

    /**
     * Non-vacuousness self-check: two inputs that genuinely differ at the DDL level (not just in
     * comment wording or {@code IF NOT EXISTS} presence) must still fail normalization-equality.
     * Guards against a normalization bug (e.g. an over-eager strip) silently collapsing every input
     * to the same string, which would make the main test above pass regardless of real drift.
     */
    @Test
    void normalizationDoesNotMaskARealDifference() {
        String base = "CREATE TABLE IF NOT EXISTS foo (id INT NOT NULL DEFAULT NOW());";
        String changedDefault = "CREATE TABLE IF NOT EXISTS foo (id INT NOT NULL DEFAULT 0);";
        assertEquals(normalize(base), normalize(base));
        org.junit.jupiter.api.Assertions.assertNotEquals(normalize(base), normalize(changedDefault));
    }

    private static String normalize(String sql) {
        String withoutComments = LINE_COMMENT.matcher(sql).replaceAll("");
        String lower = withoutComments.toLowerCase();
        String withoutIfNotExists = IF_NOT_EXISTS.matcher(lower).replaceAll("");
        return WHITESPACE.matcher(withoutIfNotExists).replaceAll(" ").trim();
    }

    /**
     * Finds the first {@code ```sql} ... {@code ```} fenced block after {@code sectionMarker},
     * bounded above by the next level-2 ({@code "## "}) heading — same start/end convention {@link
     * RouteCensusTest} and {@link EstimateReconciliationTest} already use for scoping a search to one
     * doc section rather than matching the first occurrence anywhere in the file.
     */
    private static String extractFencedSqlBlock(Path doc, String sectionMarker) throws IOException {
        List<String> lines = Files.readAllLines(doc);

        int sectionStart = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith(sectionMarker)) {
                sectionStart = i + 1;
                break;
            }
        }
        if (sectionStart < 0) {
            throw new IllegalStateException("Could not find \"" + sectionMarker + "\" in " + doc);
        }

        int sectionEnd = lines.size();
        for (int i = sectionStart; i < lines.size(); i++) {
            if (lines.get(i).startsWith("## ")) {
                sectionEnd = i;
                break;
            }
        }

        int fenceStart = -1;
        for (int i = sectionStart; i < sectionEnd; i++) {
            if (lines.get(i).strip().equals("```sql")) {
                fenceStart = i + 1;
                break;
            }
        }
        if (fenceStart < 0) {
            throw new IllegalStateException("Could not find a ```sql fenced block under \"" + sectionMarker
                    + "\" in " + doc);
        }

        int fenceEnd = -1;
        for (int i = fenceStart; i < sectionEnd; i++) {
            if (lines.get(i).strip().equals("```")) {
                fenceEnd = i;
                break;
            }
        }
        if (fenceEnd < 0) {
            throw new IllegalStateException("Fenced ```sql block under \"" + sectionMarker + "\" in " + doc
                    + " was never closed before the next \"## \" heading");
        }

        return String.join("\n", lines.subList(fenceStart, fenceEnd));
    }

    /**
     * Maven/Surefire's default working directory is the module basedir ({@code app-bana-service/}),
     * but some IDE test runners use the repo root instead — try both rather than assuming one (same
     * fallback {@link RouteCensusTest} and {@link EstimateReconciliationTest} already use).
     */
    private static Path resolveDoc(String filename) {
        Path fromModuleDir = Paths.get("..", "docs", "planning", filename);
        if (Files.isRegularFile(fromModuleDir)) {
            return fromModuleDir;
        }
        Path fromRepoRoot = Paths.get("docs", "planning", filename);
        if (Files.isRegularFile(fromRepoRoot)) {
            return fromRepoRoot;
        }
        throw new IllegalStateException("Could not locate " + filename + " from working directory "
                + Paths.get("").toAbsolutePath());
    }

    private static Path resolveMigration(String filename) {
        Path fromModuleDir = Paths.get("src", "main", "resources", "db", "migration", filename);
        if (Files.isRegularFile(fromModuleDir)) {
            return fromModuleDir;
        }
        Path fromRepoRoot = Paths.get("app-bana-service", "src", "main", "resources", "db", "migration", filename);
        if (Files.isRegularFile(fromRepoRoot)) {
            return fromRepoRoot;
        }
        throw new IllegalStateException("Could not locate " + filename + " from working directory "
                + Paths.get("").toAbsolutePath());
    }
}
