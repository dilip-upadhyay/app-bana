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
 * {@code GenericEntityRoutes.java}, 6 in {@code SchemaRoutes.java} at the time). Removing
 * occurrences (S3.4's job) never fails this test. Adding a new one — in an existing allow-listed
 * file, or introducing the pattern into any other file for the first time (this is exactly how
 * S1.6 added three new instances in {@code AppRoutes.java}) — does.
 *
 * <p><b>{@code GenericEntityRoutes.java} dropped from 21 to 13 in S3.4</b>, which wired
 * {@code EntityAccessGuard} unconditionally into all 19 in-scope entity-data routes (the 8
 * packed-key {@code /api/{entity}*}, 5 studio-scoped, and 6 runtime/env-scoped families),
 * replacing each route's own {@code if (authEnabled(cfg))} block. The remaining 13 are a
 * genuinely different, out-of-scope concern, not a shortfall: 1 on {@code /audit} and 7 on
 * {@code /api/field-permissions*} (their own admin surface, never routed through
 * {@code EntityAccessGuard}), plus 5 field-level-security (FLS) readable-fields filters nested
 * inside already-guarded routes — those gate only whether {@code permissionService} redacts
 * individual fields from an already-admitted response, not whether the caller may reach the route
 * at all, so they are not the access-control anti-pattern this ratchet exists to catch.
 *
 * <p><b>{@code SchemaRoutes.java}'s count reached zero via S1.15/S1.16/S1.17</b>, and its entry
 * was removed from {@link #BASELINE} entirely rather than set to {@code 0} — a missing key and a
 * {@code 0} entry both fail equally on any future re-introduction (the "NEW FILE" branch vs. the
 * {@code count > max} branch), so removal is simply the more honest representation of "zero
 * remaining occurrences" — not a stronger guarantee than {@code 0}, just the more accurate one.
 *
 * <p><b>{@code ApiServer.java}, 1 occurrence (S1.10, review round 2)</b> is the one deliberate
 * exception on the list, and is the *inverse* of the anti-pattern rather than a false negative:
 * {@code if (!AuthService.authEnabled(cfg))} there logs a loud startup warning that auth is off —
 * it never gates a security check on auth being on. Round 1 of this file's own review flagged that
 * evading this regex (e.g. via {@code boolean gate = authEnabled(cfg); if (gate)}) is a real,
 * exploitable blind spot; round 2 confirmed the fix actually took that shape and asked for the
 * honest alternative instead — keep the natural {@code if (!authEnabled(cfg))} syntax and count it
 * here, so `git grep authEnabled` and this ratchet's report never disagree. Do not narrow
 * {@link #ANTI_PATTERN} to exclude negated conditions to "fix" this instead —
 * {@code if (!authEnabled(cfg)) { return; }} is the anti-pattern in inverted form and must still
 * be caught.
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
            // S3.4 — dropped from 21 to 13; see class Javadoc for what the remaining 13 are.
            "com/appbana/server/routes/GenericEntityRoutes.java", 13,
            // S1.10 — the inverse of the anti-pattern (warns auth is off; never gates a security
            // check on auth being on). See this class's own Javadoc before adding a second one.
            "com/appbana/ApiServer.java", 1
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
