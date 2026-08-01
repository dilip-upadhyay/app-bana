package com.appbana.server;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * S0.5 — estimate + scope reconciliation guard.
 *
 * <p>Sums every task row's estimate in {@code TENANT_ISOLATION_IMPLEMENTATION_TASKS.md} (the
 * tracker) per sub-phase and asserts the total against {@code TENANT_ISOLATION_SECURITY_PLAN.md}'s
 * (the plan doc's) S0–S5 summary table and its own "Total scope" headline — the tracker's own
 * headline is checked the same way, since a review round found the two headlines can drift from
 * each other independently of the per-phase figures.
 *
 * <p>Registered as task S0.5 (review round 4) after an independent line-item sum turned up a ~28%
 * aggregate understatement relative to the hand-maintained totals of the time. Left unimplemented
 * for several rounds while S1 work proceeded; implemented here after a follow-up S1.8 review round
 * found the identical class of drift recur twice more in the meantime (the tracker's and plan doc's
 * S2.6 rows disagreeing, then the plan doc's own headline going stale) and argued the cost of manual
 * reconciliation was already exceeding this task's own 90-minute estimate. Writing this test caught
 * a fourth, independent instance before it was even committed: a ground-up sum of every row disagreed
 * with the hand-maintained running total by about 20 minutes — an error that predates this fix and
 * was never traced to a specific earlier round, consistent with this task's whole premise (derive the
 * total mechanically; don't re-audit history by hand to find exactly where it drifted).
 *
 * <p><b>Range convention</b> (written down at S0.5's own row, round 5): where an estimate is a range
 * (today: {@code S0.0}'s "30–90 min", {@code S2.6}'s "60–90 min"), take the upper bound.
 *
 * <p><b>Deliberately out of scope</b>: the "Scope extended, round 2" clause on S0.5's own row asks to
 * also diff Where/scope text between the docs, not just estimates. {@link
 * #sharedTaskFileListsMatchAcrossDocs()} compares the <em>set</em> of backtick-quoted file/class
 * tokens in each doc's Files/Where column for any task id that has its own row in both docs — not a
 * prose-equality check. The two docs deliberately use different phrasing/verbosity for the same task
 * (the tracker is terse, the plan doc narrative), so a literal text diff would false-positive on
 * nearly every shared row; a file reference actually being added, removed, or renamed on one side and
 * not the other is the concrete, mechanically-checkable proxy for "scope drifted" this test targets
 * instead. Task ids that only have a row in one doc (e.g. {@code S0.5} itself is absent from the plan
 * doc's own S0 sub-phase table) are silently skipped, not flagged — nothing to compare them against.
 */
class EstimateReconciliationTest {

    private static final Pattern TASK_ROW = Pattern.compile("^\\|\\s*(S\\d+(?:\\.\\d+)?[a-z]?)\\s*\\|");
    private static final Pattern PHASE_ROW = Pattern.compile("^\\|\\s*(S[0-5])\\s*\\|");
    private static final Pattern EST_RANGE = Pattern.compile("(\\d+)\\s*[\\u2013-]\\s*(\\d+)\\s*min");
    private static final Pattern EST_SINGLE = Pattern.compile("(\\d+)\\s*min");
    private static final Pattern HR_FIGURE = Pattern.compile("~(\\d+(?:\\.\\d+)?)\\s*hr");
    private static final Pattern HEADLINE = Pattern.compile(
            "\\*\\*Total scope:\\*\\*\\s*~(\\d+(?:\\.\\d+)?)\\s*(?:hr|hours)\\b(?:\\s*across\\s*(\\d+)\\s*tasks)?");
    private static final Pattern BACKTICKED = Pattern.compile("`([^`]+)`");
    private static final Pattern OR_ALTERNATIVE = Pattern.compile("(?i)\\(or [^)]*\\)");

    @Test
    void trackerEstimatesMatchPlanDocSummaryAndBothHeadlines() throws Exception {
        Path trackerDoc = resolveDoc("TENANT_ISOLATION_IMPLEMENTATION_TASKS.md");
        Path planDoc = resolveDoc("TENANT_ISOLATION_SECURITY_PLAN.md");

        Map<String, Integer> perTaskMinutes = parseTrackerTaskEstimates(trackerDoc);
        Map<String, Integer> perPhaseMinutes = new TreeMap<>();
        for (Map.Entry<String, Integer> e : perTaskMinutes.entrySet()) {
            String phase = e.getKey().split("\\.")[0];
            perPhaseMinutes.merge(phase, e.getValue(), Integer::sum);
        }
        int grandTotalMinutes = perTaskMinutes.values().stream().mapToInt(Integer::intValue).sum();
        double grandTotalHours = round2(grandTotalMinutes / 60.0);

        Map<String, Double> planDocPhaseHours = parsePlanDocSummaryTable(planDoc);
        StringBuilder mismatches = new StringBuilder();

        for (Map.Entry<String, Integer> e : perPhaseMinutes.entrySet()) {
            double trackerHours = round2(e.getValue() / 60.0);
            Double planHours = planDocPhaseHours.get(e.getKey());
            if (planHours == null) {
                mismatches.append("Phase ").append(e.getKey())
                        .append(" has task rows in the tracker but no row in the plan doc's S0\u2013S5 summary table\n");
            } else if (Math.abs(trackerHours - planHours) > 0.005) {
                mismatches.append("Phase ").append(e.getKey()).append(": tracker task rows sum to ~")
                        .append(trackerHours).append(" hr, plan doc's summary table says ~")
                        .append(planHours).append(" hr\n");
            }
        }

        double planDocHeadlineHours = parsePlanDocHeadline(planDoc);
        if (Math.abs(grandTotalHours - planDocHeadlineHours) > 0.005) {
            mismatches.append("Grand total: tracker task rows sum to ~").append(grandTotalHours)
                    .append(" hr, plan doc's \"Total scope\" headline says ~").append(planDocHeadlineHours)
                    .append(" hr\n");
        }

        double[] trackerHeadline = parseTrackerHeadline(trackerDoc);
        if (Math.abs(grandTotalHours - trackerHeadline[0]) > 0.005) {
            mismatches.append("Tracker's own headline says ~").append(trackerHeadline[0])
                    .append(" hr, but its task rows actually sum to ~").append(grandTotalHours).append(" hr\n");
        }
        if ((int) trackerHeadline[1] != perTaskMinutes.size()) {
            mismatches.append("Tracker's own headline says ").append((int) trackerHeadline[1])
                    .append(" tasks, but ").append(perTaskMinutes.size()).append(" task rows were found\n");
        }

        if (!mismatches.isEmpty()) {
            fail("Estimate reconciliation drift between the tracker and the plan doc:\n" + mismatches);
        }
    }

    @Test
    void sharedTaskFileListsMatchAcrossDocs() throws Exception {
        Path trackerDoc = resolveDoc("TENANT_ISOLATION_IMPLEMENTATION_TASKS.md");
        Path planDoc = resolveDoc("TENANT_ISOLATION_SECURITY_PLAN.md");

        Map<String, Set<String>> trackerFiles = parseTrackerTaskFiles(trackerDoc);
        Map<String, Set<String>> planDocFiles = parsePlanDocTaskFiles(planDoc);

        StringBuilder mismatches = new StringBuilder();
        for (Map.Entry<String, Set<String>> e : trackerFiles.entrySet()) {
            Set<String> planFiles = planDocFiles.get(e.getKey());
            if (planFiles == null) {
                continue; // no row for this task id in the plan doc's own per-task tables — nothing to compare
            }
            if (!e.getValue().equals(planFiles)) {
                mismatches.append(e.getKey()).append(": tracker Files=").append(new TreeSet<>(e.getValue()))
                        .append(", plan doc Where=").append(new TreeSet<>(planFiles)).append('\n');
            }
        }
        if (!mismatches.isEmpty()) {
            fail("Files/Where column drift between the tracker and the plan doc for shared task rows:\n" + mismatches);
        }
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * Maven/Surefire's default working directory is the module basedir ({@code app-bana-service/}),
     * but some IDE test runners use the repo root instead — try both rather than assuming one
     * (same fallback {@link RouteCensusTest} already uses).
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

    /**
     * Splits a Markdown table row on {@code |}, respecting {@code \|} as an escaped literal pipe
     * within a cell's own text (used by, e.g., S0.2's Files column: {@code `path`\|`query`\|...}) —
     * a naive {@code line.split("\\|")} would over-split those cells and silently misalign every
     * later column, which is exactly what happened the first time this was written.
     */
    private static String[] splitTableRow(String line) {
        String protectedLine = line.replace("\\|", "\u0001");
        String[] cells = protectedLine.split("\\|", -1);
        for (int i = 0; i < cells.length; i++) {
            cells[i] = cells[i].replace("\u0001", "\\|");
        }
        return cells;
    }

    private static int parseEstimateMinutesUpperBound(String cell) {
        Matcher rangeM = EST_RANGE.matcher(cell);
        if (rangeM.find()) {
            return Integer.parseInt(rangeM.group(2));
        }
        Matcher singleM = EST_SINGLE.matcher(cell);
        if (singleM.find()) {
            return Integer.parseInt(singleM.group(1));
        }
        throw new IllegalStateException("Could not parse an estimate out of: " + cell);
    }

    /**
     * Extracts the Files/Where column's backtick-quoted tokens as the comparable "file set" for a
     * task row. Strips any {@code (or ...)} parenthetical first — this doc's own convention for
     * naming an alternative that was considered but not committed to (e.g. S0.1's "(or a small
     * extracted `IdentityResolver`)", S2.10's "(or `AppRoutes.java`)") — otherwise a legitimate,
     * intentional design alternative reads as drift against the tracker's single, decided file.
     */
    private static Set<String> extractBacktickedTokens(String cell) {
        String withoutAlternatives = OR_ALTERNATIVE.matcher(cell).replaceAll("");
        Set<String> result = new LinkedHashSet<>();
        Matcher m = BACKTICKED.matcher(withoutAlternatives);
        while (m.find()) {
            result.add(basename(m.group(1)));
        }
        return result;
    }

    /**
     * Reduces a Files/Where token to its final path segment before comparing across docs — the two
     * docs reference the same file at different levels of path detail (e.g. the tracker's
     * "app-bana-service/.../db/changelog/" vs. the plan doc's full
     * "app-bana-service/src/main/resources/db/changelog/", or the tracker's
     * "ai-builder/.../api/AiChatController.java" vs. the plan doc's bare "AiChatController.java"),
     * and a full-string comparison would flag that abbreviation difference as drift when the file
     * itself hasn't actually changed.
     */
    private static String basename(String token) {
        String trimmed = token.endsWith("/") ? token.substring(0, token.length() - 1) : token;
        int slash = trimmed.lastIndexOf('/');
        return slash >= 0 ? trimmed.substring(slash + 1) : trimmed;
    }

    /** Tracker task rows: {@code | # | Task | Files | Est. | Status | } — 5 real columns. */
    private static Map<String, Integer> parseTrackerTaskEstimates(Path trackerDoc) throws IOException {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String line : Files.readAllLines(trackerDoc)) {
            Matcher m = TASK_ROW.matcher(line);
            if (!m.find() || !m.group(1).contains(".")) {
                continue;
            }
            String[] cells = splitTableRow(line);
            if (cells.length != 7) {
                continue; // not a real 5-column task row
            }
            result.put(m.group(1), parseEstimateMinutesUpperBound(cells[4]));
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("Parsed zero task rows from " + trackerDoc
                    + " — the doc's table structure likely changed and this parser needs updating");
        }
        return result;
    }

    private static Map<String, Set<String>> parseTrackerTaskFiles(Path trackerDoc) throws IOException {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (String line : Files.readAllLines(trackerDoc)) {
            Matcher m = TASK_ROW.matcher(line);
            if (!m.find() || !m.group(1).contains(".")) {
                continue;
            }
            String[] cells = splitTableRow(line);
            if (cells.length != 7) {
                continue;
            }
            result.put(m.group(1), extractBacktickedTokens(cells[3]));
        }
        return result;
    }

    /** Plan doc's S0–S5 summary table: {@code | # | Sub-phase | Deliverable | Est. | } — 4 real columns. */
    private static Map<String, Double> parsePlanDocSummaryTable(Path planDoc) throws IOException {
        List<String> lines = Files.readAllLines(planDoc);
        int start = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains("| # | Sub-phase | Deliverable | Est. |")) {
                start = i + 1;
                break;
            }
        }
        if (start < 0) {
            throw new IllegalStateException("Could not find the S0\u2013S5 summary table header in " + planDoc);
        }

        Map<String, Double> result = new LinkedHashMap<>();
        for (int i = start; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.startsWith("**Total scope:**")) {
                break;
            }
            Matcher m = PHASE_ROW.matcher(line);
            if (!m.find()) {
                continue;
            }
            String[] cells = splitTableRow(line);
            if (cells.length != 6) {
                continue;
            }
            Matcher hrM = HR_FIGURE.matcher(cells[4]);
            if (hrM.find()) {
                result.put(m.group(1), Double.parseDouble(hrM.group(1)));
            }
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("Parsed zero phase rows from the S0\u2013S5 summary table in " + planDoc);
        }
        return result;
    }

    private static double parsePlanDocHeadline(Path planDoc) throws IOException {
        for (String line : Files.readAllLines(planDoc)) {
            if (line.startsWith("**Total scope:**")) {
                Matcher m = HEADLINE.matcher(line);
                if (m.find()) {
                    return Double.parseDouble(m.group(1));
                }
                throw new IllegalStateException("Could not parse the hour figure out of the plan doc's Total scope line: " + line);
            }
        }
        throw new IllegalStateException("Could not find a \"**Total scope:**\" line in " + planDoc);
    }

    private static double[] parseTrackerHeadline(Path trackerDoc) throws IOException {
        for (String line : Files.readAllLines(trackerDoc)) {
            if (line.startsWith("**Total scope:**")) {
                Matcher m = HEADLINE.matcher(line);
                if (m.find() && m.group(2) != null) {
                    return new double[] { Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)) };
                }
                throw new IllegalStateException("Could not parse hours+task-count out of the tracker's Total scope line: " + line);
            }
        }
        throw new IllegalStateException("Could not find a \"**Total scope:**\" line in " + trackerDoc);
    }

    /**
     * Plan doc's per-sub-phase task tables (under each {@code "## Sub-phase SX"} heading, bounded by
     * the next {@code "## "} heading): {@code | # | Task | Files | Est. | } — 4 real columns, same
     * shape as the S0–S5 summary table but keyed by dotted task ids instead of bare phase ids.
     */
    private static Map<String, Set<String>> parsePlanDocTaskFiles(Path planDoc) throws IOException {
        List<String> lines = Files.readAllLines(planDoc);
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).startsWith("## Sub-phase ")) {
                continue;
            }
            for (int j = i + 1; j < lines.size() && !lines.get(j).startsWith("## "); j++) {
                String line = lines.get(j);
                Matcher m = TASK_ROW.matcher(line);
                if (!m.find() || !m.group(1).contains(".")) {
                    continue;
                }
                String[] cells = splitTableRow(line);
                if (cells.length != 6) {
                    continue;
                }
                result.put(m.group(1), extractBacktickedTokens(cells[3]));
            }
        }
        return result;
    }
}
