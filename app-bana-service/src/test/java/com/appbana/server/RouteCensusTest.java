package com.appbana.server;

import com.appbana.api.Router;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * S0.3 — census-drift guard.
 *
 * Fails whenever {@link RouteRegistry#buildRouter()} registers a route (method + path
 * pattern) that isn't accounted for in the S0.2 route census
 * ({@code docs/planning/TENANT_ISOLATION_SECURITY_PLAN.md}, "S0.2 Route census" section),
 * or whenever a censused route is renamed/removed without updating that doc.
 *
 * <p>This parses the census table directly out of the plan doc's Markdown rather than
 * keeping a second, hand-maintained copy in this file. An earlier version hardcoded its own
 * {@code EXPECTED_ROUTES} set, which meant this test only ever checked {@code Router} against
 * a copy a developer typed once — the doc itself could still drift out from under it with
 * nothing to catch that. The doc is what S1–S3 are scoped against, so it — not a shadow copy —
 * is the artifact this test has to agree with (review feedback on the original S0.3).
 *
 * <p>SET comparison (symmetric difference), not a count: {@code Router} has one confirmed
 * duplicate registration today ({@code GET /api/{tenantId}/apps/{id}/env/{env}/full},
 * AppRoutes.java), so a raw registration count would be 97 while the number of distinct
 * (method, path) signatures is 96 — a count-based assertion would silently accept a second,
 * different duplicate appearing while missing an actually-new route.
 */
class RouteCensusTest {

    private static final String CENSUS_START_MARKER = "## S0.2 Route census";
    private static final Pattern HTTP_METHOD = Pattern.compile("^(GET|POST|PUT|DELETE)$");
    private static final Pattern FIRST_BACKTICKED = Pattern.compile("`([^`]+)`");

    @Test
    void registeredRoutesMatchCensusExactly() throws Exception {
        Router router = RouteRegistry.buildRouter();
        Set<String> actual = reflectRegisteredRoutes(router);

        Path planDoc = resolvePlanDoc();
        Set<String> censused = parseCensusFromPlanDoc(planDoc);

        Set<String> registeredButNotCensused = new TreeSet<>(actual);
        registeredButNotCensused.removeAll(censused);

        Set<String> censusedButNotRegistered = new TreeSet<>(censused);
        censusedButNotRegistered.removeAll(actual);

        if (!registeredButNotCensused.isEmpty() || !censusedButNotRegistered.isEmpty()) {
            StringBuilder sb = new StringBuilder("Route census drift detected (Router vs. \"S0.2 Route census\"\n")
                    .append("in ").append(planDoc).append(").\n");
            if (!registeredButNotCensused.isEmpty()) {
                sb.append("Registered in Router but MISSING from the census — add a row to the\n")
                  .append("relevant *Routes.java section of the plan doc:\n");
                registeredButNotCensused.forEach(r -> sb.append("  + ").append(r).append('\n'));
            }
            if (!censusedButNotRegistered.isEmpty()) {
                sb.append("Listed in the census but NO LONGER registered in Router (route was\n")
                  .append("renamed/removed — update the plan doc's census table):\n");
                censusedButNotRegistered.forEach(r -> sb.append("  - ").append(r).append('\n'));
            }
            fail(sb.toString());
        }
    }

    /**
     * Reads {@code Router}'s private {@code routes} list via reflection and reconstructs each
     * route's "METHOD /path/pattern" signature from its private {@code method}/{@code parts}
     * fields. There is no public accessor by design — {@code Router} has no reason to expose
     * its route table to production callers, so this test reaches in deliberately rather than
     * widening Router's public API just to make itself easier to write.
     */
    @SuppressWarnings("unchecked")
    private static Set<String> reflectRegisteredRoutes(Router router) throws Exception {
        Field routesField = Router.class.getDeclaredField("routes");
        routesField.setAccessible(true);
        List<Object> routes = (List<Object>) routesField.get(router);

        Class<?> routeClass = Class.forName("com.appbana.api.Router$Route");
        Field methodField = routeClass.getDeclaredField("method");
        Field partsField = routeClass.getDeclaredField("parts");
        methodField.setAccessible(true);
        partsField.setAccessible(true);

        Set<String> result = new LinkedHashSet<>();
        for (Object route : routes) {
            String method = (String) methodField.get(route);
            List<String> parts = (List<String>) partsField.get(route);
            String path = "/" + String.join("/", parts);
            result.add(method + " " + path);
        }
        return result;
    }

    /**
     * Maven/Surefire's default working directory is the module basedir ({@code app-bana-service/}),
     * but some IDE test runners use the repo root instead — try both rather than assuming one.
     */
    private static Path resolvePlanDoc() {
        Path fromModuleDir = Paths.get("..", "docs", "planning", "TENANT_ISOLATION_SECURITY_PLAN.md");
        if (Files.isRegularFile(fromModuleDir)) {
            return fromModuleDir;
        }
        Path fromRepoRoot = Paths.get("docs", "planning", "TENANT_ISOLATION_SECURITY_PLAN.md");
        if (Files.isRegularFile(fromRepoRoot)) {
            return fromRepoRoot;
        }
        throw new IllegalStateException("Could not locate TENANT_ISOLATION_SECURITY_PLAN.md from working "
                + "directory " + Paths.get("").toAbsolutePath() + " (tried " + fromModuleDir.toAbsolutePath()
                + " and " + fromRepoRoot.toAbsolutePath() + ")");
    }

    /**
     * Parses {@code | METHOD | `/path` | ... |} rows out of the plan doc's "S0.2 Route census"
     * section, bounded below by that heading and above by the next level-2 ({@code "## "})
     * heading. Header/separator rows, prose footnotes, and the "no known caller" bullet list
     * that follows the per-file tables are all ignored — this only accepts lines starting with
     * {@code |} whose first cell is a bare HTTP method, which none of those are.
     *
     * <p>The Path cell's value is whatever's inside its FIRST pair of backticks. Some rows carry
     * a trailing parenthetical after the closing backtick — e.g. AppRoutes.java's confirmed
     * duplicate registration is annotated {@code `/api/.../full` (1st reg., live)} and
     * {@code `/api/.../full` (2nd reg., **dead code**)} — that's explanatory prose, not part of
     * the path, and both resolve to the same signature here (correctly collapsing to one entry
     * in the Set, same as Router's own first-match-wins behavior does at runtime).
     */
    private static Set<String> parseCensusFromPlanDoc(Path planDoc) throws IOException {
        List<String> lines = Files.readAllLines(planDoc);

        int start = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith(CENSUS_START_MARKER)) {
                start = i + 1;
                break;
            }
        }
        if (start < 0) {
            throw new IllegalStateException("Could not find \"" + CENSUS_START_MARKER + "\" in " + planDoc);
        }

        int end = lines.size();
        for (int i = start; i < lines.size(); i++) {
            if (lines.get(i).startsWith("## ")) {
                end = i;
                break;
            }
        }

        Set<String> result = new LinkedHashSet<>();
        for (int i = start; i < end; i++) {
            String line = lines.get(i).trim();
            if (!line.startsWith("|")) {
                continue;
            }

            String[] cells = line.split("\\|", -1);
            if (cells.length < 3) {
                continue;
            }
            String method = cells[1].trim();
            if (!HTTP_METHOD.matcher(method).matches()) {
                continue; // header row ("Method"), separator row ("---"), or not a route row
            }

            Matcher m = FIRST_BACKTICKED.matcher(cells[2]);
            if (!m.find()) {
                continue;
            }
            result.add(method + " " + m.group(1));
        }

        if (result.isEmpty()) {
            throw new IllegalStateException("Parsed zero routes from the S0.2 census in " + planDoc
                    + " — the doc's table structure likely changed and this parser needs updating");
        }
        return result;
    }
}

