package com.appbana.server;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Review round 1 meta-observation (docs/planning/TENANT_ISOLATION_SECURITY_PLAN.md, S1 review) —
 * ratchet guard against new {@code if (AuthService.authEnabled(cfg))} conditional gates.
 *
 * <p>Three review findings (B2, H1, and the original S0 audit's #2/#3) all trace back to the same
 * root cause: wrapping a security check in {@code if (authEnabled(cfg))} makes the check itself
 * optional, and it silently evaluates to "skip the check" under the shipped config, where
 * {@code adminToken} and {@code readToken} are both null. The check reads correctly, and a live
 * test run with a locally-configured token also looks correct — the gap only shows up when you
 * ask what the shipped default config evaluates to, which is exactly how B2 survived its own
 * live verification (per the review). {@code S3.4} is the task that removes this pattern
 * repo-wide; until then, this test stops the surface area from growing.
 *
 * <p>This is a one-directional ratchet, not a ban: the {@link #BASELINE} below is today's actual
 * count per file (verified against a real grep at the time this test was added — 21 in
 * {@code GenericEntityRoutes.java}, 6 in {@code SchemaRoutes.java}). Removing occurrences (S3.4's
 * job) never fails this test. Adding a new one — in an existing allow-listed file, or introducing
 * the pattern into any other file for the first time (this is exactly how S1.6 added three new
 * instances in {@code AppRoutes.java}) — does.
 */
class AuthEnabledAntiPatternTest {

    /**
     * Matches a code line that uses {@code authEnabled(...)} as an {@code if} condition (the
     * anti-pattern — the check becomes conditional). Deliberately does NOT match lines where
     * {@code authEnabled(cfg)} is merely passed as a boolean argument to some other method (e.g.
     * {@code applyApprovalStatusFilter(..., AuthService.authEnabled(cfg))}) — that is a different,
     * non-gating usage and is not what this test is guarding against.
     */
    private static final Pattern ANTI_PATTERN = Pattern.compile("\\bif\\s*\\(.*authEnabled\\(");

    /**
     * relative path (from {@code src/main/java}) -> max allowed occurrence count today.
     * Do not raise these numbers to make a new occurrence pass — fix S3.4 instead, or if a
     * occurrence is a genuine one-off exception, discuss with the plan doc's owner first.
     */
    private static final Map<String, Integer> BASELINE = Map.of(
            "com/appbana/server/routes/GenericEntityRoutes.java", 21,
            "com/appbana/server/routes/SchemaRoutes.java", 6
    );

    @Test
    void noNewAuthEnabledConditionalGatesAppear() throws IOException {
        Path srcRoot = resolveSrcMainJava();
        Map<String, Integer> actual = countOccurrencesByFile(srcRoot);

        List<String> problems = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : new TreeMap<>(actual).entrySet()) {
            String file = entry.getKey();
            int count = entry.getValue();
            Integer max = BASELINE.get(file);
            if (max == null) {
                problems.add("NEW FILE introduces the if (authEnabled(cfg)) conditional-gate anti-pattern: "
                        + file + " (" + count + " occurrence(s)). A security check must not be conditional "
                        + "on whether a token happens to be configured — see S1.6/B2 in the plan doc's review.");
            } else if (count > max) {
                problems.add(file + ": expected at most " + max + " occurrence(s) of the anti-pattern "
                        + "(today's known baseline, pending S3.4), found " + count + ". Do not add new "
                        + "if (authEnabled(cfg)) blocks — gate unconditionally instead (see S1.4/S1.6-B2 fix).");
            }
        }

        if (!problems.isEmpty()) {
            fail("authEnabled(...) anti-pattern ratchet failed:\n  - " + String.join("\n  - ", problems));
        }
    }

    private static Map<String, Integer> countOccurrencesByFile(Path srcRoot) throws IOException {
        Map<String, Integer> counts = new TreeMap<>();
        try (Stream<Path> files = Files.walk(srcRoot)) {
            List<Path> javaFiles = files.filter(p -> p.toString().endsWith(".java")).toList();
            for (Path file : javaFiles) {
                int count = 0;
                for (String line : Files.readAllLines(file)) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("//") || trimmed.startsWith("*")) {
                        continue; // comment line, not real code
                    }
                    if (ANTI_PATTERN.matcher(line).find()) {
                        count++;
                    }
                }
                if (count > 0) {
                    String relative = srcRoot.relativize(file).toString().replace('\\', '/');
                    counts.put(relative, count);
                }
            }
        }
        return counts;
    }

    /**
     * Same dual-working-directory resolution as {@code RouteCensusTest} — Maven/Surefire runs
     * from the module basedir ({@code app-bana-service/}), some IDE runners use the repo root.
     */
    private static Path resolveSrcMainJava() {
        Path fromModuleDir = Paths.get("src", "main", "java");
        if (Files.isDirectory(fromModuleDir)) {
            return fromModuleDir;
        }
        Path fromRepoRoot = Paths.get("app-bana-service", "src", "main", "java");
        if (Files.isDirectory(fromRepoRoot)) {
            return fromRepoRoot;
        }
        throw new IllegalStateException("Could not locate src/main/java from working directory "
                + Paths.get("").toAbsolutePath() + " (tried " + fromModuleDir.toAbsolutePath()
                + " and " + fromRepoRoot.toAbsolutePath() + ")");
    }
}
